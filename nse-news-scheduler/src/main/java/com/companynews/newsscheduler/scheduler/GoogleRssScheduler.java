package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.service.RssFetcher;
import com.companynews.newsscheduler.service.KeywordLoader;
import com.companynews.newsscheduler.service.KeywordNewsBucketCache;
import com.companynews.newsscheduler.service.NewsStore;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Scheduled component that fetches Google RSS news for all configured keywords.
 *
 * <p>Fetch pipeline:
 * <ol>
 *   <li>Load all keywords from {@link KeywordLoader} (company symbols, sectors, macro terms),
 *       plus the keywords.txt-only subset via {@link KeywordLoader#loadFileKeywords()}.</li>
 *   <li>Submit one fetch task per keyword to the managed {@code googleRssExecutor} thread pool.</li>
 *   <li>Each task: fetch from Google RSS → dedup by {@link UrlWindow} → save via {@link NewsWorker}.
 *       For keywords.txt-sourced keywords, also rebuild that keyword's entry in
 *       {@link KeywordNewsBucketCache} — see {@link #processKeyword} for why this must happen
 *       unconditionally, on every cycle, not just when new articles are found.</li>
 *   <li>Wait for all tasks to complete before returning (using {@link CompletableFuture#allOf}).</li>
 * </ol>
 *
 * <p>A configurable delay ({@code news.google.fetch.delay.ms}, default 500ms) is inserted
 * between keyword submissions to avoid triggering Google's rate limits.
 *
 * <p>An immediate startup fetch is scheduled 5 seconds after {@link ApplicationReadyEvent}
 * to give the NSE scheduler time to complete its own startup fetch first. This is also what
 * naturally repopulates {@link KeywordNewsBucketCache} after a restart — the cache always
 * starts empty in a fresh process, and this startup fetch rebuilds every keywords.txt
 * keyword's entry from {@link NewsStore} (itself seeded from the database), with no
 * separate "restore" logic needed.
 */
@Component
public class GoogleRssScheduler {

    private static final Logger log = LogManager.getLogger(GoogleRssScheduler.class);

    private final RssFetcher rssFetcher;
    private final NewsWorker newsWorker;
    private final KeywordLoader keywordLoader;
    private final UrlWindow urlWindow;
    private final KeywordNewsBucketCache bucketCache;
    private final NewsStore newsStore;

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
     * @param bucketCache      in-memory 96-slot rolling-24-hour cache for keywords.txt keywords
     * @param newsStore        in-memory mirror of {@code company_news}, used as the rebuild
     *                         source for {@code bucketCache}
     * @param executor         named fixed thread pool for parallel keyword processing
     * @param startupScheduler lightweight scheduler for the one-shot startup delay
     */
    public GoogleRssScheduler(RssFetcher rssFetcher,
                               NewsWorker newsWorker,
                               KeywordLoader keywordLoader,
                               UrlWindow urlWindow,
                               KeywordNewsBucketCache bucketCache,
                               NewsStore newsStore,
                               @Qualifier("googleRssExecutor") ExecutorService executor,
                               @Qualifier("startupScheduler") TaskScheduler startupScheduler) {
        this.rssFetcher       = rssFetcher;
        this.newsWorker       = newsWorker;
        this.keywordLoader    = keywordLoader;
        this.urlWindow        = urlWindow;
        this.bucketCache      = bucketCache;
        this.newsStore        = newsStore;
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
     * Scheduled entry point: every 15 minutes, all day, every day of the week.
     * Delegates to {@link #runFetch()}.
     *
     * <p>Previously this was split across 5 separate cron windows (weekday peak/off-peak,
     * weekend, and two US-market-hours windows reading a separate {@code us-keywords.txt}
     * file). All keywords — domestic, global, and US — now live together in
     * {@code keywords.txt} and are fetched on this single unified cadence.
     */
    @Scheduled(cron = "${news.google.cron}", zone = "${scheduler.timezone}")
    public void runFetchScheduled() {
        runFetch();
    }

    /**
     * Runs the full Google RSS fetch-dedup-save cycle.
     * Called by the scheduled entry point and the startup listener.
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
        long startMs = System.currentTimeMillis();

        List<String> keywords = keywordLoader.load();
        if (keywords.isEmpty()) {
            log.warn("No keywords found — skipping Google RSS fetch cycle");
            return;
        }

        // keywords.txt-sourced subset — only these get a bucket-cache rebuild per keyword,
        // and this same set defines what's allowed to remain in the cache at all.
        Set<String> fileKeywords = keywordLoader.loadFileKeywords();
        bucketCache.pruneRemovedKeywords(fileKeywords);

        // company-symbol subset — only these get importance classification (Important News tab).
        // Loaded once per cycle so each keyword is a cheap in-memory membership check, not a DB call.
        Set<String> companySymbols = keywordLoader.loadCompanySymbols();

        log.info("Submitting {} keywords to executor pool ({} keywords.txt-sourced, {} companies)",
                 keywords.size(), fileKeywords.size(), companySymbols.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String keyword : keywords) {
            boolean isFileKeyword = fileKeywords.contains(keyword);
            boolean isCompany = companySymbols.contains(keyword);
            futures.add(CompletableFuture.runAsync(() -> processKeyword(keyword, isFileKeyword, isCompany), executor));

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

        long durationSec = (System.currentTimeMillis() - startMs) / 1000;
        log.info("Google RSS fetch cycle completed — {} keywords processed, duration={}s",
                 keywords.size(), durationSec);
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
     * <p><b>WHY the bucket-cache rebuild lives in the {@code finally} block, not inside the
     * "new items saved" branch:</b> {@link KeywordNewsBucketCache} needs to re-filter its
     * 24-hour window on every single cycle, even when this keyword found nothing new this
     * time. If the rebuild only ran when {@code saveNews} was called, a quiet keyword's cache
     * entry would never get re-checked, and articles that have since aged past 24 hours would
     * keep being served indefinitely instead of aging out on schedule. Running it in
     * {@code finally} guarantees it fires on every path — empty fetch, all-duplicate, a real
     * save, or even an exception — for every keywords.txt keyword, every cycle.
     *
     * @param keyword       the keyword to fetch news for
     * @param isFileKeyword whether this keyword came from {@code keywords.txt} — only these
     *                      get a bucket-cache rebuild; company symbols and sector names never do
     * @param isCompany     whether this keyword is a company symbol — only then are fetched
     *                      items classified for the "Important News" tab
     */
    private void processKeyword(String keyword, boolean isFileKeyword, boolean isCompany) {
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

            newsWorker.saveNews(keyword, newItems, isCompany);

        } catch (Exception e) {
            // One failed keyword must not stop other keywords from processing.
            // RuntimeException wrapping IOException is caught here — inspect e.getCause() if needed.
            log.error("Error processing keyword [{}]: {}", keyword, e.getMessage(), e);
        } finally {
            if (isFileKeyword) {
                bucketCache.rebuild(keyword, newsStore.get(keyword));
            }
            ThreadContext.remove("keyword");
        }
    }
}
