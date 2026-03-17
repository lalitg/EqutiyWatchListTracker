package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final CompanyNewsRepository repository;

    private final SimilarityService similarityService;

    @Value("${news.limit:5}")
    private int newsLimit;

    /**
     * WHY ConcurrentHashMap of ReentrantLocks:
     *
     * We need per-keyword locking — not a single global lock.
     * A single global lock (synchronized on 'this') would mean
     * Thread 1 saving "INFY" blocks Thread 2 saving "RELIANCE"
     * even though they are completely independent operations.
     * That defeats the purpose of multithreading.
     *
     * Per-keyword locking means:
     * - Thread 1 saving "INFY" only blocks other threads saving "INFY"
     * - Thread 2 saving "RELIANCE" proceeds without waiting
     * - Maximum parallelism, minimum blocking
     *
     * ConcurrentHashMap is thread-safe for put/get operations.
     * ReentrantLock allows the same thread to acquire the lock
     * multiple times without deadlocking (reentrant = re-enterable).
     */
    private final ConcurrentHashMap<String, ReentrantLock> keywordLocks
        = new ConcurrentHashMap<>();

    public WorkerService(CompanyNewsRepository repository, SimilarityService similarityService) {
        this.repository = repository;
        this.similarityService = similarityService;
    }

    /**
     * Gets or creates a lock for a specific keyword.
     * computeIfAbsent is atomic — thread-safe without additional locking.
     */
    private ReentrantLock getLockForKeyword(String keyword) {
        return keywordLocks.computeIfAbsent(keyword, k -> new ReentrantLock());
    }

    @Transactional
    public void processAndSave(String keyword, List<NewsItem> newItems) {
        if (newItems == null || newItems.isEmpty()) return;

        // Get the lock for this specific keyword
        ReentrantLock lock = getLockForKeyword(keyword);

        // Acquire the lock — if another thread holds it for the same keyword,
        // this thread waits here until the lock is released
        lock.lock();

        try {
            // Step 1: Load existing row or create new one
            Optional<CompanyNews> existing = repository.findByKeyword(keyword);
            CompanyNews record;
            List<NewsItem> currentNews;

            if (existing.isPresent()) {
                record = existing.get();
                currentNews = record.getNews() != null
                    ? new ArrayList<>(record.getNews())
                    : new ArrayList<>();
            } else {
                record = new CompanyNews();
                record.setKeyword(keyword);
                record.setSentiments("");
                currentNews = new ArrayList<>();
            }

            // Step 2: Add new items to front — with similarity check
            // Collect existing summaries for comparison
            List<String> existingSummaries = currentNews.stream()
                .map(NewsItem::getSummary)
                .filter(s -> s != null)
                .collect(java.util.stream.Collectors.toList());

            for (NewsItem item : newItems) {
                // Check if this headline is too similar to any already-saved headline
                if (similarityService.isDuplicate(item.getSummary(), existingSummaries)) {
                    log.debug("Similarity duplicate skipped for keyword: {} — headline: {}",
                            keyword, item.getSummary());
                    continue;  // skip this item
                }
                // New unique item — add to front and also add to comparison list
                currentNews.add(0, item);
                existingSummaries.add(item.getSummary());  // include in comparison for next items
            }
            
            for (NewsItem item : newItems) {
                currentNews.add(0, item);
            }

            // Step 3: Trim to limit
            if (currentNews.size() > newsLimit) {
                currentNews = currentNews.subList(0, newsLimit);
            }

            // Step 4: Save
            record.setNews(currentNews);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);

            log.info("Saved {} new item(s) for keyword: {}", newItems.size(), keyword);

        } catch (DataIntegrityViolationException e) {
            // WHY catch this specifically:
            // Even with the lock, in rare cases (e.g. app restart during save)
            // a unique constraint violation can occur.
            // Instead of crashing, we retry once by loading and updating.
            log.warn("Unique constraint hit for keyword: {} — retrying as update", keyword);
            retryAsUpdate(keyword, newItems);
        } finally {
            // ALWAYS release the lock — even if an exception occurs
            // Without finally, a crash inside the try block would
            // leave the lock permanently locked — deadlock
            lock.unlock();
        }
    }

    /**
     * Fallback method — called when INSERT fails due to race condition.
     * At this point we know the row exists, so we just UPDATE it.
     */
    @Transactional
    private void retryAsUpdate(String keyword, List<NewsItem> newItems) {
        Optional<CompanyNews> existing = repository.findByKeyword(keyword);
        if (existing.isEmpty()) return;

        CompanyNews record = existing.get();
        List<NewsItem> currentNews = record.getNews() != null
            ? new ArrayList<>(record.getNews())
            : new ArrayList<>();

        for (NewsItem item : newItems) {
            currentNews.add(0, item);
        }

        if (currentNews.size() > newsLimit) {
            currentNews = currentNews.subList(0, newsLimit);
        }

        record.setNews(currentNews);
        record.setLastUpdated(LocalDateTime.now());
        repository.save(record);
        log.info("Retry update succeeded for keyword: {}", keyword);
    }
}
