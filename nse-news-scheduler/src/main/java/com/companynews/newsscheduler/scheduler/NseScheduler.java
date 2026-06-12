package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.service.NseFetcher;
import com.companynews.newsscheduler.service.SeqIdWindow;
import com.companynews.newsscheduler.service.NewsWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduled component that polls the NSE corporate-announcements API every 15 minutes.
 *
 * <p>Fetch pipeline:
 * <ol>
 *   <li>Fetch all announcements from NSE via {@link NseFetcher} (with retry + circuit breaker).</li>
 *   <li>Deduplicate using the {@link SeqIdWindow} — skip any seqId already seen in the
 *       current batch or in previous cycles.</li>
 *   <li>Group remaining announcements by company symbol.</li>
 *   <li>Persist each group via {@link NewsWorker} (which applies additional URL and
 *       similarity deduplication before saving).</li>
 * </ol>
 *
 * <p>The scheduler runs on a dedicated {@code nseTaskScheduler} thread pool bean (defined in
 * {@link com.companynews.newsscheduler.config.AppConfig}) so it never competes with the
 * Google RSS scheduler for threads.
 *
 * <p>An immediate startup fetch is also triggered once via {@link ApplicationReadyEvent}
 * so the first batch of announcements is available as soon as the service starts.
 */
@Component
public class NseScheduler {

    private static final Logger log = LogManager.getLogger(NseScheduler.class);

    private final NseFetcher nseFetcher;
    private final NewsWorker newsWorker;
    private final SeqIdWindow seqIdWindow;

    /**
     * Guards against the startup fetch running more than once.
     *
     * <p>WHY {@code volatile}: {@link ApplicationReadyEvent} fires on the main Spring startup
     * thread. {@code @Scheduled} tasks run on a different thread ({@code nse-scheduler-thread-1}).
     * Without {@code volatile}, the JVM may cache {@code startupDone=true} in a CPU register
     * and the scheduler thread may never observe the updated value — causing {@link #onStartup()}
     * to run more than once. {@code volatile} enforces cross-thread visibility of the write.
     */
    private volatile boolean startupDone = false;

    /**
     * Constructs an {@code NseScheduler} with all required dependencies injected by Spring.
     *
     * @param nseFetcher  fetches corporate announcements from the NSE API
     * @param newsWorker  handles deduplication and persistence of news items
     * @param seqIdWindow in-memory sliding window for NSE sequence ID deduplication
     */
    public NseScheduler(NseFetcher nseFetcher,
                        NewsWorker newsWorker,
                        SeqIdWindow seqIdWindow) {
        this.nseFetcher  = nseFetcher;
        this.newsWorker  = newsWorker;
        this.seqIdWindow = seqIdWindow;
    }

    /**
     * Fires once when the application is fully started and ready.
     *
     * <p>WHY {@link ApplicationReadyEvent}: fires exactly once, after the context is fully
     * refreshed, the database is connected, and all beans are initialized — the correct moment
     * to start fetching. Using {@code @PostConstruct} would fire too early (before DB is ready).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupDone) return;
        startupDone = true;
        log.info("=== NSE startup fetch triggered ===");
        runFetch();
    }

    /**
     * Runs the full NSE fetch-dedup-save cycle on a configurable cron schedule
     * (default: every 15 minutes, configured via {@code news.nse.cron}).
     *
     * <p>The {@code scheduler = "nseTaskScheduler"} attribute routes this task to the
     * dedicated NSE thread pool, ensuring it never blocks or is blocked by other schedulers.
     */
    @Scheduled(cron = "${news.nse.cron}", scheduler = "nseTaskScheduler", zone = "${scheduler.timezone}")
    public void runFetch() {
        log.info("NSE fetch triggered — current seqId window size: {}", seqIdWindow.getWindowSize());

        // Step 1: Fetch all announcements (resilience4j retry + circuit breaker active)
        List<NseAnnouncement> allAnnouncements = nseFetcher.fetch();

        if (allAnnouncements.isEmpty()) {
            log.warn("NSE returned 0 announcements — skipping this cycle");
            return;
        }

        // Step 2: SeqId dedup — filter announcements already processed in this batch or prior cycles
        Set<Long> seenInBatch = new HashSet<>();
        List<NseAnnouncement> newAnnouncements = new ArrayList<>();

        for (NseAnnouncement announcement : allAnnouncements) {
            Long seqId = announcement.getSeqId();

            if (seenInBatch.contains(seqId)) {
                log.debug("Duplicate seqId {} within same NSE batch — skipping", seqId);
                continue;
            }
            if (seqIdWindow.contains(seqId)) {
                log.debug("Duplicate seqId {} from previous cycle — skipping", seqId);
                continue;
            }

            seenInBatch.add(seqId);
            seqIdWindow.add(seqId);
            newAnnouncements.add(announcement);
        }

        log.info("{} new out of {} total announcements — seqId window size now: {}",
                newAnnouncements.size(), allAnnouncements.size(), seqIdWindow.getWindowSize());

        if (newAnnouncements.isEmpty()) {
            log.info("All announcements already seen — nothing to save this cycle");
            return;
        }

        // Step 3: Group by symbol
        Map<String, List<NewsItem>> bySymbol = newAnnouncements.stream()
            .filter(a -> a.getNewsItem().getSymbol() != null)
            .collect(Collectors.groupingBy(
                a -> a.getNewsItem().getSymbol(),
                Collectors.mapping(NseAnnouncement::getNewsItem, Collectors.toList())
            ));

        // Step 4: Save per symbol — ThreadContext (Log4j2 MDC equivalent) set per symbol
        // so all log lines for that symbol carry the keyword field, traceable in log aggregators
        for (Map.Entry<String, List<NewsItem>> entry : bySymbol.entrySet()) {
            ThreadContext.put("keyword", entry.getKey());
            try {
                newsWorker.saveNews(entry.getKey(), entry.getValue());
            } finally {
                ThreadContext.remove("keyword");
            }
        }

        log.info("NSE fetch complete — {} unique companies processed", bySymbol.size());
    }
}
