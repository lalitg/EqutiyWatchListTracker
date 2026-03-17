package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.service.GoogleRssService;
import com.companynews.newsscheduler.service.KeywordLoaderService;
import com.companynews.newsscheduler.service.UrlWindowService;
import com.companynews.newsscheduler.service.WorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class GoogleRssScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoogleRssScheduler.class);

    private final GoogleRssService googleRssService;
    private final WorkerService workerService;
    private final KeywordLoaderService keywordLoaderService;
    private final UrlWindowService urlWindowService;

    // Number of worker threads — read from application.properties
    // Default 2 — enough for public API without getting rate limited
    @Value("${news.google.thread.pool.size:2}")
    private int threadPoolSize;

    // Delay between keyword fetches in milliseconds
    // WHY: Google will rate limit or block if we hammer it with requests
    // 500ms between requests is respectful and safe
    @Value("${news.google.fetch.delay.ms:500}")
    private long fetchDelayMs;

    // Startup flag — same pattern as NseScheduler
    private boolean startupFetchDone = false;

    public GoogleRssScheduler(GoogleRssService googleRssService,
                               WorkerService workerService,
                               KeywordLoaderService keywordLoaderService,
                               UrlWindowService urlWindowService) {
        this.googleRssService = googleRssService;
        this.workerService = workerService;
        this.keywordLoaderService = keywordLoaderService;
        this.urlWindowService = urlWindowService;
    }

    /**
     * Google RSS startup fetch runs after NSE startup fetch.
     * WHY Thread.sleep(5000):
     * NSE and Google RSS both save data for the same keywords.
     * By delaying Google RSS startup by 5 seconds, NSE gets to
     * complete its startup fetch first.
     * When Google RSS runs, the per-keyword locks in WorkerService
     * handle any remaining concurrency safely.
     *
     * 5 seconds is enough — NSE fetches 20 items, saves them fast.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupFetchDone) return;
        startupFetchDone = true;

        // Run in a separate thread so the 5-second delay does not
        // block the main application thread
        new Thread(() -> {
            try {
                log.info("=== Google RSS startup fetch — waiting 5s for NSE to complete ===");
                Thread.sleep(5000);
                fetchAndSaveGoogleRssNews();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Google RSS startup fetch interrupted");
            }
        }, "google-rss-startup-thread").start();
    }

    /**
     * Runs every day at 8 AM.
     * Fetches Google RSS news for all keywords from all 3 sources.
     */
    @Scheduled(cron = "${news.google.cron}")
    public void fetchAndSaveGoogleRssNews() {
        log.info("Google RSS fetch started — loading keywords...");

        // Step 1: Load all keywords from all sources
        List<String> keywords = keywordLoaderService.loadAllKeywords();

        if (keywords.isEmpty()) {
            log.warn("No keywords found — skipping Google RSS fetch");
            return;
        }

        log.info("Starting Google RSS fetch for {} keywords with {} threads",
                 keywords.size(), threadPoolSize);

        // Step 2: Create a thread pool with configured number of threads
        // WHY ExecutorService: allows us to submit tasks and run them
        // in parallel across multiple threads.
        // WHY fixed thread pool: limits concurrent requests to Google
        // so we don't get rate limited.
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        // Step 3: Submit one task per keyword to the thread pool
        for (String keyword : keywords) {
            executor.submit(() -> processKeyword(keyword));

            // Add delay between submissions to avoid hammering Google
            // WHY here and not inside processKeyword:
            // The delay is about submission rate, not processing rate.
            // We control how fast we send requests, not how fast threads run.
            try {
                Thread.sleep(fetchDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Fetch delay interrupted");
            }
        }

        // Step 4: Shutdown executor — no new tasks accepted
        // Then wait up to 30 minutes for all submitted tasks to finish
        executor.shutdown();
        try {
            boolean finished = executor.awaitTermination(30, TimeUnit.MINUTES);
            if (!finished) {
                log.warn("Google RSS fetch timed out after 30 minutes");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        log.info("Google RSS fetch completed for all {} keywords", keywords.size());
    }

    /**
     * Processes a single keyword — fetch, dedup, save.
     * This method runs inside a worker thread from the ExecutorService.
     *
     * WHY a separate method:
     * Keeps the lambda in the submit() call clean.
     * Also makes error handling per-keyword — if one keyword fails,
     * other keywords keep processing normally.
     */
    private void processKeyword(String keyword) {
        try {
            log.debug("Processing keyword: {}", keyword);

            // Fetch news from Google RSS for this keyword
            List<NewsItem> fetched = googleRssService.fetchNews(keyword);

            if (fetched.isEmpty()) {
                log.debug("No news returned for keyword: {}", keyword);
                return;
            }

            // Deduplicate using URL window — keep only new articles
            // NEW — atomic check and mark in one step
            List<NewsItem> newItems = fetched.stream()
                .filter(item -> urlWindowService.checkAndMark(item.getLink()))
                .toList();

            if (newItems.isEmpty()) {
                log.debug("All items duplicate for keyword: {}", keyword);
                return;
            }

            // Save to DB — same WorkerService used by NSE
            workerService.processAndSave(keyword, newItems);

        } catch (Exception e) {
            // Catch all exceptions so one failed keyword doesn't stop others
            log.error("Error processing keyword '{}': {}", keyword, e.getMessage());
        }
    }
}
