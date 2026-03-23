package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.service.RssFetcher;
import com.companynews.newsscheduler.service.KeywordLoader;
import com.companynews.newsscheduler.service.UrlWindow;
import com.companynews.newsscheduler.service.NewsWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
public class GoogleRssScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoogleRssScheduler.class);

    private final RssFetcher rssFetcher;
    private final NewsWorker newsWorker;
    private final KeywordLoader keywordLoader;
    private final UrlWindow urlWindow;

    /**
     * Injected singleton ExecutorService (defined in AppConfig as "googleRssExecutor").
     *
     * WHY injected instead of created inline:
     * The old code called Executors.newFixedThreadPool(n) inside runFetch(), then
     * called executor.shutdown() at the end. This created and destroyed a thread pool
     * on every scheduler run — expensive, and the shutdown() call would have permanently
     * killed the pool if the code was ever restructured to reuse it.
     *
     * With a managed bean:
     *  - Pool is created ONCE at startup and reused across all scheduler runs
     *  - Thread names ("rss-worker-N") appear in logs and thread dumps
     *  - Spring's destroyMethod = "shutdown" gracefully drains the pool on app stop
     *  - @Qualifier avoids ambiguity if other ExecutorService beans are added later
     */
    private final ExecutorService executor;

    /**
     * Spring-managed TaskScheduler for the one-off startup delay.
     *
     * WHY TaskScheduler instead of raw new Thread():
     * The previous implementation used:
     *   new Thread(() -> { Thread.sleep(5000); runFetch(); }, "rss-startup-thread").start()
     *
     * Problems with raw Thread:
     *  - Unmanaged: Spring cannot monitor or gracefully stop it on shutdown
     *  - If the app stops during the 5-second sleep, the thread is silently abandoned
     *  - Requires manual Thread.currentThread().interrupt() boilerplate
     *
     * With the managed TaskScheduler:
     *  - Spring owns the lifecycle — destroyMethod="shutdown" drains it on app stop
     *  - The 5-second delay is expressed as Instant.now().plusSeconds(5) — no sleep needed
     *  - Thread is named "startup-thread-1", visible in thread dumps and metrics
     */
    private final TaskScheduler startupScheduler;

    @Value("${news.google.fetch.delay.ms:500}")
    private long fetchDelayMs;

    /**
     * WHY volatile:
     * ApplicationReadyEvent fires on the main Spring startup thread.
     * @Scheduled tasks run on a different thread (nse-scheduler-thread or default scheduler).
     * Without volatile, the JVM is free to cache startupDone=true in a CPU register,
     * and another thread may never observe the updated value — meaning onStartup() could
     * run more than once. volatile guarantees cross-thread visibility.
     */
    private volatile boolean startupDone = false;

    public GoogleRssScheduler(RssFetcher rssFetcher,
                               NewsWorker newsWorker,
                               KeywordLoader keywordLoader,
                               UrlWindow urlWindow,
                               @Qualifier("googleRssExecutor") ExecutorService executor,
                               @Qualifier("startupScheduler") TaskScheduler startupScheduler) {
        this.rssFetcher      = rssFetcher;
        this.newsWorker      = newsWorker;
        this.keywordLoader   = keywordLoader;
        this.urlWindow       = urlWindow;
        this.executor        = executor;
        this.startupScheduler = startupScheduler;
    }

    /**
     * Startup fetch — scheduled 5 seconds after NSE startup fetch to let NSE complete first.
     *
     * WHY the delay: NSE and Google RSS both write to the same company_news rows.
     * Starting RSS 5 seconds after NSE means NSE data is already saved when RSS runs,
     * so the URL and similarity dedup in NewsWorker correctly skips duplicates.
     *
     * WHY startupScheduler.schedule() instead of new Thread():
     * See field comment above. The managed TaskScheduler expresses the one-shot delay
     * as a future Instant — no manual Thread.sleep() or interrupt handling needed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupDone) return;
        startupDone = true;
        log.info("=== Google RSS startup fetch — scheduling in 5s for NSE to complete ===");
        startupScheduler.schedule(this::runFetch, Instant.now().plusSeconds(5));
    }

    /**
     * Scheduled fetch — runs at configured times (default: midnight, 8 AM, 10 AM, 12 PM, 2 PM, 4 PM, 8 PM).
     *
     * Submits one task per keyword to the managed executor pool with a configurable
     * delay between submissions to avoid rate-limiting from Google RSS.
     *
     * WHY CompletableFuture.allOf().join() instead of executor.awaitTermination():
     * executor.awaitTermination() requires shutting down the pool first.
     * Since we're reusing the managed pool across runs, we must NOT shut it down.
     * CompletableFuture.allOf() collects all submitted task futures and blocks until
     * every one completes — equivalent behaviour without touching the pool lifecycle.
     */
    @Scheduled(cron = "${news.google.cron}")
    public void runFetch() {
        log.info("Google RSS fetch started — loading keywords...");

        List<String> keywords = keywordLoader.load();
        if (keywords.isEmpty()) {
            log.warn("No keywords found — skipping Google RSS fetch");
            return;
        }

        log.info("Submitting {} keywords to executor", keywords.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String keyword : keywords) {
            futures.add(CompletableFuture.runAsync(() -> processKeyword(keyword), executor));

            // Rate-limiting delay between submissions — controls how fast we send to Google
            try {
                Thread.sleep(fetchDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Fetch delay interrupted — stopping remaining submissions");
                break;
            }
        }

        // Wait for all submitted tasks to finish before returning
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Google RSS fetch completed for all {} keywords", keywords.size());
    }

    /**
     * Processes a single keyword: fetch → dedup → save.
     * Runs inside a worker thread from the executor pool.
     *
     * MDC.put("keyword") ensures all log lines for this keyword carry the keyword field,
     * making parallel log output traceable in log aggregators without grepping by thread name.
     * MDC.remove() in finally guarantees cleanup even if an exception occurs mid-processing.
     */
    private void processKeyword(String keyword) {
        MDC.put("keyword", keyword);
        try {
            log.debug("Processing keyword");

            List<NewsItem> fetched = rssFetcher.fetch(keyword);
            if (fetched.isEmpty()) {
                log.debug("No news returned");
                return;
            }

            List<NewsItem> newItems = fetched.stream()
                .filter(item -> urlWindow.addIfAbsent(item.getLink()))
                .toList();

            if (newItems.isEmpty()) {
                log.debug("All items already seen in URL window");
                return;
            }

            newsWorker.saveNews(keyword, newItems);

        } catch (Exception e) {
            // Catch-all: one failed keyword must not stop other keywords from processing
            log.error("Error processing keyword: {}", e.getMessage());
        } finally {
            MDC.remove("keyword");
        }
    }
}
