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

        ReentrantLock lock = getLockForKeyword(keyword);
        lock.lock();

        try {
            // Load existing row or create new one
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

            // Build a set of already-saved URLs for exact duplicate detection
            // WHY Set<String> of URLs:
            // URL is the most reliable unique identifier for a news item
            // Same article will always have the same URL regardless of source
            // O(1) lookup — much faster than iterating the list
            java.util.Set<String> savedUrls = currentNews.stream()
                .map(NewsItem::getLink)
                .filter(link -> link != null)
                .collect(java.util.stream.Collectors.toSet());

            // Build a set of already-saved summaries for similarity check
            List<String> existingSummaries = currentNews.stream()
                .map(NewsItem::getSummary)
                .filter(s -> s != null)
                .collect(java.util.stream.Collectors.toList());

            // Also deduplicate within the incoming newItems batch itself
            // WHY: If the same URL appears twice in the incoming batch
            // (e.g. two fetch cycles both returned the same article),
            // we only save it once
            java.util.Set<String> seenInThisBatch = new java.util.HashSet<>();

            int added = 0;
            for (NewsItem item : newItems) {
                String link = item.getLink();
                String summary = item.getSummary();

                // Step 1: Skip if URL is null
                if (link == null || link.isEmpty()) {
                    log.debug("Skipping item with null/empty link for keyword: {}", keyword);
                    continue;
                }

                // Step 2: Skip if exact URL already saved in DB
                if (savedUrls.contains(link)) {
                    log.debug("Exact URL duplicate skipped for keyword: {} — link already in DB",
                            keyword);
                    continue;
                }

                // Step 3: Skip if exact URL already seen in this batch
                if (seenInThisBatch.contains(link)) {
                    log.debug("Exact URL duplicate skipped for keyword: {} — link seen in same batch",
                            keyword);
                    continue;
                }

                // Step 4: Skip if headline is too similar to an existing headline
                if (similarityService.isDuplicate(summary, existingSummaries)) {
                    log.debug("Similarity duplicate skipped for keyword: {} — headline: {}",
                            keyword, summary);
                    continue;
                }

                // All checks passed — this is a genuinely new item
                currentNews.add(item);
                savedUrls.add(link);            // add to URL set so next items in batch check against it
                seenInThisBatch.add(link);      // track within this batch
                existingSummaries.add(summary); // add to similarity list for next items
                added++;
            }

            if (added == 0) {
                log.debug("No new items to save for keyword: {}", keyword);
                return;  // nothing changed — skip DB write entirely
            }

            // Sort by date descending — latest first
            currentNews.sort((a, b) -> compareDates(b.getDate(), a.getDate()));

            // Trim to limit
            if (currentNews.size() > newsLimit) {
                currentNews = currentNews.subList(0, newsLimit);
            }

            record.setNews(currentNews);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);

            log.info("Saved {} new item(s) for keyword: {}", added, keyword);

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint hit for keyword: {} — retrying as update", keyword);
            retryAsUpdate(keyword, newItems);
        } finally {
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

    /**
     * Compares two date strings for sorting.
     * Handles two date formats:
     * - NSE format:         "12-Mar-2026 17:11:14"
     * - Google RSS format:  "Thu, 12 Mar 2026 10:00:00 GMT"
     *
     * WHY handle two formats:
     * NSE and Google RSS return dates in different formats.
     * Both write to the same company_news table.
     * We need one method that handles both correctly.
     *
     * Returns negative if dateA is before dateB
     * Returns positive if dateA is after dateB
     * Returns 0 if equal
     * Used with sort() to order news latest first.
     */
    private int compareDates(String dateA, String dateB) {
        // If either date is null, treat null as oldest
        if (dateA == null && dateB == null) return 0;
        if (dateA == null) return -1;  // a is null → a comes last
        if (dateB == null) return 1;   // b is null → b comes last

        try {
            java.time.ZonedDateTime parsedA = parseDate(dateA);
            java.time.ZonedDateTime parsedB = parseDate(dateB);
            return parsedA.compareTo(parsedB);
        } catch (Exception e) {
            // If parsing fails for any reason, fall back to string comparison
            // This is a safety net — should not happen in normal operation
            log.warn("Date parsing failed — falling back to string comparison: {} vs {}",
                    dateA, dateB);
            return dateA.compareTo(dateB);
        }
    }

    /**
     * Parses a date string into ZonedDateTime.
     * Tries NSE format first, then Google RSS format.
     *
     * NSE format:        "12-Mar-2026 17:11:14"
     * Google RSS format: "Thu, 12 Mar 2026 10:00:00 GMT"
     */
    private java.time.ZonedDateTime parseDate(String dateStr) {
        // Try NSE format first: "12-Mar-2026 17:11:14"
        try {
            java.time.format.DateTimeFormatter nseFormatter =
                java.time.format.DateTimeFormatter.ofPattern(
                    "dd-MMM-yyyy HH:mm:ss",
                    java.util.Locale.ENGLISH
                );
            java.time.LocalDateTime localDateTime =
                java.time.LocalDateTime.parse(dateStr, nseFormatter);
            // Convert to ZonedDateTime using IST timezone
            return localDateTime.atZone(java.time.ZoneId.of("Asia/Kolkata"));
        } catch (Exception e1) {
            // Try Google RSS format: "Thu, 12 Mar 2026 10:00:00 GMT"
            try {
                java.time.format.DateTimeFormatter rssFormatter =
                    java.time.format.DateTimeFormatter.ofPattern(
                        "EEE, dd MMM yyyy HH:mm:ss z",
                        java.util.Locale.ENGLISH
                    );
                return java.time.ZonedDateTime.parse(dateStr, rssFormatter);
            } catch (Exception e2) {
                // If both fail, throw so compareDates can handle it
                throw new RuntimeException("Cannot parse date: " + dateStr);
            }
        }
    }
}
