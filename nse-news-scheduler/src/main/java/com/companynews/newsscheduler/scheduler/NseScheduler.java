package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.service.NseFetchService;
import com.companynews.newsscheduler.service.SeqIdWindowService;
import com.companynews.newsscheduler.service.WorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NseScheduler {

    private static final Logger log = LoggerFactory.getLogger(NseScheduler.class);

    private final NseFetchService nseFetchService;
    private final WorkerService workerService;
    private final SeqIdWindowService seqIdWindowService;

    // This flag makes sure the startup fetch runs ONLY ONCE.
    // WHY: @EventListener(ContextRefreshedEvent.class) can fire more than once
    // in some Spring configurations (e.g. if the context is refreshed again).
    // Without this flag, you could get duplicate fetches on startup.
    private boolean startupFetchDone = false;

    public NseScheduler(NseFetchService nseFetchService,
                        WorkerService workerService,
                        SeqIdWindowService seqIdWindowService) {
        this.nseFetchService = nseFetchService;
        this.workerService = workerService;
        this.seqIdWindowService = seqIdWindowService;
    }

    /**
     * ApplicationReadyEvent fires AFTER ContextRefreshedEvent.
     * It means the application is fully ready to serve requests.
     * NSE runs first on this event.
     *
     * WHY ApplicationReadyEvent instead of ContextRefreshedEvent:
     * ContextRefreshedEvent can fire multiple times.
     * ApplicationReadyEvent fires exactly ONCE when the app is
     * fully started and ready. More reliable for startup tasks.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupFetchDone) return;
        startupFetchDone = true;
        log.info("=== NSE startup fetch triggered ===");
        fetchAndSaveNseNews();
    }

    /**
     * Runs every 15 minutes automatically via cron.
     * Fetches latest NSE announcements and saves new ones.
     */
    /**
     * scheduler = "nseTaskScheduler" tells Spring to use our dedicated
     * named thread pool for this scheduled method instead of the default
     * shared thread pool.
     * Now NSE always runs on "nse-scheduler-thread-1".
     */
    @Scheduled(cron = "${news.nse.cron}", scheduler = "nseTaskScheduler")
    public void fetchAndSaveNseNews() {
        log.info("NSE fetch triggered — window size: {}",
                seqIdWindowService.getWindowSize());

        // Step 1: Fetch all announcements from NSE
        List<NseAnnouncement> allAnnouncements = nseFetchService.fetchAllAnnouncements();

        if (allAnnouncements.isEmpty()) {
            log.warn("NSE returned 0 announcements — skipping");
            return;
        }

        // Step 2: Filter duplicates using in-memory sliding window
        // Also deduplicate within the batch itself by seqId
        java.util.Set<Long> seenInThisBatch = new java.util.HashSet<>();
        List<NseAnnouncement> newAnnouncements = new ArrayList<>();

        for (NseAnnouncement announcement : allAnnouncements) {
            Long seqId = announcement.getSeqId();

            // Skip if already seen in this batch (NSE sometimes returns duplicates)
            if (seenInThisBatch.contains(seqId)) {
                log.debug("Duplicate seqId {} in same NSE batch — skipping", seqId);
                continue;
            }

            // Skip if already seen in previous fetch cycles (window check)
            if (seqIdWindowService.isAlreadySeen(seqId)) {
                log.debug("Duplicate seqId {} from previous cycle — skipping", seqId);
                continue;
            }

            seenInThisBatch.add(seqId);
            seqIdWindowService.markAsSeen(seqId);
            newAnnouncements.add(announcement);
        }

        log.info("{} new out of {} total — window size now: {}",
                newAnnouncements.size(),
                allAnnouncements.size(),
                seqIdWindowService.getWindowSize());

        if (newAnnouncements.isEmpty()) {
            log.info("No new announcements — nothing to save");
            return;
        }

        // Step 3: Group by company symbol
        // Collectors.groupingBy groups the list into a Map:
        // { "INFY" -> [item1], "RELIANCE" -> [item2, item3], ... }
        Map<String, List<NewsItem>> bySymbol = newAnnouncements.stream()
            .filter(a -> a.getNewsItem().getSymbol() != null)
            .collect(Collectors.groupingBy(
                a -> a.getNewsItem().getSymbol(),
                Collectors.mapping(
                    NseAnnouncement::getNewsItem,
                    Collectors.toList()
                )
            ));

        // Step 4: Save each company's new items to DB
        for (Map.Entry<String, List<NewsItem>> entry : bySymbol.entrySet()) {
            workerService.processAndSave(entry.getKey(), entry.getValue());
        }

        log.info("NSE fetch complete — {} companies saved/updated", bySymbol.size());
    }
}
