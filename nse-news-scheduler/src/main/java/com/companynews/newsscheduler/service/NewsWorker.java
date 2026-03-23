package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class NewsWorker {

    private static final Logger log = LoggerFactory.getLogger(NewsWorker.class);

    // Pre-compiled date formatters — shared, DateTimeFormatter is thread-safe
    private static final DateTimeFormatter NSE_FORMAT =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter RSS_FORMAT =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private final CompanyNewsRepository repository;
    private final SimilarityChecker similarityChecker;

    @Value("${news.limit:5}")
    private int newsLimit;

    /**
     * Per-keyword locking: Thread 1 saving "INFY" does NOT block Thread 2 saving "RELIANCE".
     * ConcurrentHashMap.computeIfAbsent is atomic — no external synchronization needed.
     */
    private final ConcurrentHashMap<String, ReentrantLock> keywordLocks = new ConcurrentHashMap<>();

    /**
     * Micrometer metrics:
     *
     * news.items.saved — Counter of items written to DB across all keywords.
     *   Useful for alerting on "0 items saved in last N minutes" (pipeline silent).
     *
     * news.items.skipped tagged by reason:
     *   url_duplicate  — item URL already in DB or seen in current batch
     *   similarity     — headline too similar to existing stored headline
     *   null_link      — item had no URL (unusable)
     *
     * WHY not tagged by keyword: keyword is high-cardinality (hundreds of symbols).
     * High-cardinality tags blow up Prometheus memory and cardinality limits.
     */
    private final Counter savedCounter;
    private final Counter skippedUrlCounter;
    private final Counter skippedSimilarityCounter;
    private final Counter skippedNullLinkCounter;

    public NewsWorker(CompanyNewsRepository repository,
                      SimilarityChecker similarityChecker,
                      MeterRegistry meterRegistry) {
        this.repository = repository;
        this.similarityChecker = similarityChecker;
        this.savedCounter = Counter.builder("news.items.saved")
            .description("Total news items written to DB")
            .register(meterRegistry);
        this.skippedUrlCounter = Counter.builder("news.items.skipped")
            .tag("reason", "url_duplicate")
            .description("Items skipped due to exact URL already in DB or current batch")
            .register(meterRegistry);
        this.skippedSimilarityCounter = Counter.builder("news.items.skipped")
            .tag("reason", "similarity")
            .description("Items skipped due to near-duplicate headline")
            .register(meterRegistry);
        this.skippedNullLinkCounter = Counter.builder("news.items.skipped")
            .tag("reason", "null_link")
            .description("Items skipped because link was null or empty")
            .register(meterRegistry);
    }

    private ReentrantLock getLock(String keyword) {
        return keywordLocks.computeIfAbsent(keyword, k -> new ReentrantLock());
    }

    /**
     * Deduplicates and saves news items for a given keyword.
     *
     * 4-layer dedup strategy (in order):
     *  1. URL null/empty check — no link → skip
     *  2. Exact URL match against DB — already saved → skip
     *  3. Exact URL match within this batch — same URL submitted twice → skip
     *  4. Jaccard similarity against existing headlines — near-duplicate text → skip
     *
     * MDC.put("keyword") is set here so ALL log lines inside this method (and anything
     * called from it) include the keyword field — visible in log aggregators without
     * needing to grep through interleaved parallel log output.
     */
    @Transactional
    public void saveNews(String keyword, List<NewsItem> newItems) {
        if (newItems == null || newItems.isEmpty()) return;

        ReentrantLock lock = getLock(keyword);
        lock.lock();
        MDC.put("keyword", keyword);

        try {
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

            Set<String> savedUrls = currentNews.stream()
                .map(NewsItem::getLink)
                .filter(l -> l != null)
                .collect(Collectors.toSet());

            List<String> existingSummaries = currentNews.stream()
                .map(NewsItem::getSummary)
                .filter(s -> s != null)
                .collect(Collectors.toList());

            Set<String> seenInBatch = new HashSet<>();
            int added = 0;

            for (NewsItem item : newItems) {
                String link    = item.getLink();
                String summary = item.getSummary();

                if (link == null || link.isEmpty()) {
                    log.debug("Skipping item with null/empty link");
                    skippedNullLinkCounter.increment();
                    continue;
                }
                if (savedUrls.contains(link)) {
                    log.debug("Duplicate URL — already in DB");
                    skippedUrlCounter.increment();
                    continue;
                }
                if (seenInBatch.contains(link)) {
                    log.debug("Duplicate URL — seen in current batch");
                    skippedUrlCounter.increment();
                    continue;
                }
                if (similarityChecker.isDuplicate(summary, existingSummaries)) {
                    log.debug("Similarity duplicate skipped: {}", summary);
                    skippedSimilarityCounter.increment();
                    continue;
                }

                currentNews.add(item);
                savedUrls.add(link);
                seenInBatch.add(link);
                existingSummaries.add(summary);
                added++;
            }

            if (added == 0) {
                log.debug("No new items to save");
                return;
            }

            // Sort latest first, trim to limit
            currentNews.sort((a, b) -> compareDates(b.getDate(), a.getDate()));
            if (currentNews.size() > newsLimit) {
                currentNews = currentNews.subList(0, newsLimit);
            }

            record.setNews(currentNews);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);

            savedCounter.increment(added);
            log.info("Saved {} new item(s)", added);

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation — retrying as upsert");
            upsert(keyword, newItems);
        } finally {
            MDC.remove("keyword");
            lock.unlock();
        }
    }

    /**
     * Fallback for race conditions: the INSERT path found no row, but between
     * findByKeyword and save, another thread inserted one. Re-read and update.
     *
     * WHY no @Transactional here:
     * This method is called from saveNews() which is itself @Transactional.
     * Java's proxy-based AOP intercepts only external calls — calling upsert() via
     * "this.upsert()" bypasses the proxy entirely, so @Transactional on this method
     * would have no effect. The method already participates in saveNews()'s transaction
     * by default (Spring's REQUIRED propagation), so no annotation is needed.
     */
    private void upsert(String keyword, List<NewsItem> newItems) {
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
        log.info("Upsert succeeded for keyword: {}", keyword);
    }

    private int compareDates(String dateA, String dateB) {
        if (dateA == null && dateB == null) return 0;
        if (dateA == null) return -1;
        if (dateB == null) return 1;

        try {
            return parseDate(dateA).compareTo(parseDate(dateB));
        } catch (Exception e) {
            log.warn("Date parse failed — string fallback: {} vs {}", dateA, dateB);
            return dateA.compareTo(dateB);
        }
    }

    private ZonedDateTime parseDate(String dateStr) {
        // NSE format: "12-Mar-2026 17:11:14"
        try {
            return java.time.LocalDateTime.parse(dateStr, NSE_FORMAT)
                .atZone(ZoneId.of("Asia/Kolkata"));
        } catch (Exception ignored) {}

        // Google RSS format: "Thu, 12 Mar 2026 10:00:00 GMT"
        try {
            return ZonedDateTime.parse(dateStr, RSS_FORMAT);
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse date: " + dateStr);
        }
    }
}
