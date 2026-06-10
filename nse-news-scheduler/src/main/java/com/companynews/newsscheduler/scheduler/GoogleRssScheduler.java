package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.service.RssFetcher;
import com.companynews.newsscheduler.service.KeywordLoader;
import com.companynews.newsscheduler.service.UrlWindow;
import com.companynews.newsscheduler.service.NewsWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
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

/**
 * Scheduled component that fetches Google RSS news for all configured keywords.
 *
 * <p>Fetch pipeline:
 * <ol>
 *   <li>Load all keywords from {@link KeywordLoader} (company symbols, sectors, macro terms).</li>
 *   <li>Submit one fetch task per keyword to the managed {@code googleRssExecutor} thread pool.</li>
 *   <li>Each task: fetch from Google RSS → dedup by {@link UrlWindow} → save via {@link NewsWorker}.</li>
 *   <li>Wait for all tasks to complete before returning (using {@link CompletableFuture#allOf}).</li>
 * </ol>
 *
 * <p>A configurable delay ({@code news.google.fetch.delay.ms}, default 500ms) is inserted
 * between keyword submissions to avoid triggering Google's rate limits.
 *
 * <p>An immediate startup fetch is scheduled 5 seconds after {@link ApplicationReadyEvent}
 * to give the NSE scheduler time to complete its own startup fetch first.
 */
@Component
public class GoogleRssScheduler {

    private static final Logger log = LogManager.getLogger(GoogleRssScheduler.class);

    private final RssFetcher rssFetcher;
    private final NewsWorker newsWorker;
    private final KeywordLoader keywordLoader;
    private final UrlWindow urlWindow;

    /**
     * Injected singleton {@link ExecutorService} (defined in
     * {@link com.companynews.newsscheduler.config.AppConfig} as {@code googleRssExecutor}).
     *
     * <p>WHY injected instead of created inline: The old code called
     * {@code Executors.newFixedThreadPool(n)} inside {@link #runFetch()}, which created and
     * destroyed a thread pool on every scheduler run — expensive, and the {@code shutdown()}
     * call permanently killed the pool. With a managed bean, the pool is created once at
     * startup and reused; Spring drains it gracefully on app stop.
     */
    private final ExecutorService executor;

    /**
     * Spring-managed {@link TaskScheduler} for the one-off startup delay.
     *
     * <p>WHY {@code TaskScheduler} instead of raw {@code new Thread()}: A raw thread is
     * unmanaged — Spring cannot monitor or stop it gracefully on shutdown. With a managed
     * {@code TaskScheduler}, Spring owns the lifecycle and the delay is expressed as a
     * future {@link Instant}, eliminating manual {@code Thread.sleep()} and interrupt handling.
     */
    private final TaskScheduler startupScheduler;

    /** Delay in milliseconds between keyword fetch submissions to avoid Google rate limits. */
    @Value("${news.google.fetch.delay.ms:500}")
    private long fetchDelayMs;

    /**
     * Guards against the startup fetch running more than once.
     *
     * <p>WHY {@code volatile}: {@link ApplicationReadyEvent} fires on the main Spring startup
     * thread. {@code @Scheduled} tasks run on a different thread. Without {@code volatile},
     * the JVM is free to cache {@code startupDone=true} in a CPU register, making it invisible
     * to other threads. {@code volatile} guarantees cross-thread visibility of the write.
     */
    private volatile boolean startupDone = false;

    /**
     * Constructs a {@code GoogleRssScheduler} with all required dependencies injected by Spring.
     *
     * @param rssFetcher       fetches news articles from Google RSS feeds
     * @param newsWorker       handles deduplication and persistence of news items
     * @param keywordLoader    loads the merged keyword list from DB and classpath
     * @param urlWindow        in-memory sliding window for URL-based deduplication
     * @param executor         named fixed thread pool for parallel keyword processing
     * @param startupScheduler lightweight scheduler for the one-shot startup delay
     */
    public GoogleRssScheduler(RssFetcher rssFetcher,
                               NewsWorker newsWorker,
                               KeywordLoader keywordLoader,
                               UrlWindow urlWindow,
                               @Qualifier("googleRssExecutor") ExecutorService executor,
                               @Qualifier("startupScheduler") TaskScheduler startupScheduler) {
        this.rssFetcher       = rssFetcher;
        this.newsWorker       = newsWorker;
        this.keywordLoader    = keywordLoader;
        this.urlWindow        = urlWindow;
        this.executor         = executor;
        this.startupScheduler = startupScheduler;
    }

    /**
     * Schedules the startup fetch 5 seconds after the application is fully ready.
     *
     * <p>WHY the 5-second delay: NSE and Google RSS both write to the same
     * {@code company_news} rows. Starting RSS 5 seconds after NSE gives NSE data time
     * to be saved, so the URL and similarity dedup in {@link NewsWorker} correctly
     * skips any cross-source duplicates on the first run.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupDone) return;
        startupDone = true;
        log.info("=== Google RSS startup fetch — scheduling in 5s for NSE to complete first ===");
        startupScheduler.schedule(this::runFetch, Instant.now().plusSeconds(5));
    }

    /**
     * Scheduled entry point for peak hours: weekdays 8 AM–5 PM, every 30 minutes.
     * Delegates to {@link #runFetch()}.
     */
    @Scheduled(cron = "${news.google.cron.peak}")
    public void runFetchPeak() {
        runFetch();
    }

    /**
     * Scheduled entry point for off-peak weekday hours: midnight–8 AM and 6 PM–11 PM, every hour.
     * Delegates to {@link #runFetch()}.
     */
    @Scheduled(cron = "${news.google.cron.offpeak.weekday}")
    public void runFetchOffpeakWeekday() {
        runFetch();
    }

    /**
     * Scheduled entry point for weekends: every hour all day.
     * Delegates to {@link #runFetch()}.
     */
    @Scheduled(cron = "${news.google.cron.offpeak.weekend}")
    public void runFetchOffpeakWeekend() {
        runFetch();
    }

    /**
     * Runs the full Google RSS fetch-dedup-save cycle.
     * Called by the three scheduled entry points and the startup listener.
     *
     * <p>Submits one {@link CompletableFuture} per keyword to the managed executor pool
     * with a configurable delay between submissions to avoid rate-limiting.
     * Uses {@link CompletableFuture#allOf} to wait for all tasks without shutting down the pool.
     *
     * <p>WHY {@code CompletableFuture.allOf().join()} instead of {@code executor.awaitTermination()}:
     * {@code awaitTermination()} requires shutting down the pool first. Since the pool is reused
     * across runs, it must NOT be shut down. {@code allOf()} blocks until all submitted futures
     * complete — equivalent behaviour without touching the pool lifecycle.
     */
    public void runFetch() {
        log.info("Google RSS fetch started — loading keywords");

        List<String> keywords = keywordLoader.load();
        if (keywords.isEmpty()) {
            log.warn("No keywords found — skipping Google RSS fetch cycle");
            return;
        }

        log.info("Submitting {} keywords to executor pool", keywords.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String keyword : keywords) {
            futures.add(CompletableFuture.runAsync(() -> processKeyword(keyword), executor));

            // Rate-limiting delay between submissions — controls how fast we send to Google
            try {
                Thread.sleep(fetchDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Fetch delay interrupted — stopping remaining keyword submissions");
                break;
            }
        }

        // Wait for all submitted tasks to finish before returning
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Google RSS fetch cycle completed — {} keywords processed", keywords.size());
    }

    /**
     * Processes a single keyword: fetch articles → URL dedup → save.
     * Runs inside a worker thread from the managed executor pool.
     *
     * <p>Log4j2's {@link ThreadContext} (equivalent to SLF4J MDC) is populated with the keyword
     * so that all log lines emitted during processing of this keyword carry the {@code keyword}
     * field. This makes parallel log output traceable in log aggregators without needing to
     * grep by thread name. {@code ThreadContext.remove()} in {@code finally} guarantees cleanup
     * even when an exception is thrown mid-processing.
     *
     * <p>Exception handling strategy: catches any {@link Exception} so that one failed keyword
     * does not stop other keywords from processing. Note: {@link java.io.IOException} from
     * {@link RssFetcher#fetch(String)} is always wrapped as {@link RuntimeException} before
     * escaping (required by Resilience4j's proxy model), so it is caught here as a
     * {@code RuntimeException} whose cause can be inspected if needed.
     *
     * @param keyword the keyword to fetch news for
     */
    private void processKeyword(String keyword) {
        ThreadContext.put("keyword", keyword);
        try {
            log.debug("Processing keyword: {}", keyword);

            List<NewsItem> fetched = rssFetcher.fetch(keyword);
            if (fetched.isEmpty()) {
                log.debug("No news returned for keyword: {}", keyword);
                return;
            }

            List<NewsItem> newItems = fetched.stream()
                .filter(item -> urlWindow.addIfAbsent(item.getLink()))
                .toList();

            if (newItems.isEmpty()) {
                // All fetched URLs were already seen — no new articles this run.
                // Still touch last_updated so the frontend shows the data was verified now,
                // not the last time a new article was actually added (which could be days ago).
                log.debug("All {} items already seen in URL window for keyword: {} — touching last_updated", fetched.size(), keyword);
                newsWorker.touchLastUpdated(keyword);
                return;
            }

            newsWorker.saveNews(keyword, newItems);

        } catch (Exception e) {
            // One failed keyword must not stop other keywords from processing.
            // RuntimeException wrapping IOException is caught here — inspect e.getCause() if needed.
            log.error("Error processing keyword [{}]: {}", keyword, e.getMessage(), e);
        } finally {
            ThreadContext.remove("keyword");
        }
    }
}
