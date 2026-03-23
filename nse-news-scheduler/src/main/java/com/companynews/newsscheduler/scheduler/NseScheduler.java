package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.service.NseFetcher;
import com.companynews.newsscheduler.service.SeqIdWindow;
import com.companynews.newsscheduler.service.NewsWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

@Component
public class NseScheduler {

    private static final Logger log = LoggerFactory.getLogger(NseScheduler.class);

    private final NseFetcher nseFetcher;
    private final NewsWorker newsWorker;
    private final SeqIdWindow seqIdWindow;

    /**
     * WHY volatile:
     * ApplicationReadyEvent fires on the main Spring startup thread.
     * @Scheduled tasks run on a different thread (nse-scheduler-thread-1).
     * Without volatile, the JVM may cache startupDone=true in a CPU register,
     * and the scheduler thread may never observe the updated value — causing
     * onStartup() to be called more than once. volatile enforces cross-thread visibility.
     */
    private volatile boolean startupDone = false;

    public NseScheduler(NseFetcher nseFetcher,
                        NewsWorker newsWorker,
                        SeqIdWindow seqIdWindow) {
        this.nseFetcher   = nseFetcher;
        this.newsWorker   = newsWorker;
        this.seqIdWindow  = seqIdWindow;
    }

    /**
     * Fires once when the application is fully started and ready.
     * WHY ApplicationReadyEvent: fires exactly once, after context is fully refreshed,
     * DB is connected, and all beans are initialized — the right moment to start fetching.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupDone) return;
        startupDone = true;
        log.info("=== NSE startup fetch triggered ===");
        runFetch();
    }

    /**
     * Scheduled fetch — runs every 15 minutes on the dedicated nse-scheduler-thread-1.
     * The dedicated TaskScheduler bean (defined in AppConfig) ensures this never competes
     * with the Google RSS scheduler for threads.
     */
    @Scheduled(cron = "${news.nse.cron}", scheduler = "nseTaskScheduler")
    public void runFetch() {
        log.info("NSE fetch triggered — window size: {}", seqIdWindow.getWindowSize());

        // Step 1: Fetch all announcements (resilience4j retry + circuit breaker active)
        List<NseAnnouncement> allAnnouncements = nseFetcher.fetch();

        if (allAnnouncements.isEmpty()) {
            log.warn("NSE returned 0 announcements — skipping");
            return;
        }

        // Step 2: SeqId dedup — filter announcements already processed
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

        log.info("{} new out of {} total — window size now: {}",
                newAnnouncements.size(), allAnnouncements.size(), seqIdWindow.getWindowSize());

        if (newAnnouncements.isEmpty()) {
            log.info("No new announcements — nothing to save");
            return;
        }

        // Step 3: Group by symbol
        Map<String, List<NewsItem>> bySymbol = newAnnouncements.stream()
            .filter(a -> a.getNewsItem().getSymbol() != null)
            .collect(Collectors.groupingBy(
                a -> a.getNewsItem().getSymbol(),
                Collectors.mapping(NseAnnouncement::getNewsItem, Collectors.toList())
            ));

        // Step 4: Save per symbol — MDC set per symbol so log lines are traceable
        for (Map.Entry<String, List<NewsItem>> entry : bySymbol.entrySet()) {
            MDC.put("keyword", entry.getKey());
            try {
                newsWorker.saveNews(entry.getKey(), entry.getValue());
            } finally {
                MDC.remove("keyword");
            }
        }

        log.info("NSE fetch complete — {} companies processed", bySymbol.size());
    }
}
