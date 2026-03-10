package com.companynews.newsscheduler.scheduler;
 
import com.companynews.newsscheduler.service.ArchiveService;
import com.companynews.newsscheduler.service.GoogleRssService;
import com.companynews.newsscheduler.service.NseFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
 
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsScheduler {
 
    private final NseFetchService nseFetchService;
    private final GoogleRssService googleRssService;
    private final ArchiveService archiveService;
 
    // ── Job 1: Daily fetch at 8 AM ─────────────────────────────
    // Fetches NSE APIs + all Google RSS feeds
    @Scheduled(cron = "${news.scheduler.cron:0 0 8 * * *}")
    public void runDailyFetch() {
        log.info("=== Daily news fetch started ===");
        try {
            nseFetchService.fetchAll();
            googleRssService.fetchAllRssNews();
        } catch (Exception e) {
            log.error("Daily fetch failed: {}", e.getMessage(), e);
        }
        log.info("=== Daily news fetch complete ===");
    }
 
    // ── Job 2: Midnight archive job ────────────────────────────
    // Archives events whose date has passed
    @Scheduled(cron = "${archive.scheduler.cron:0 0 0 * * *}")
    public void runMidnightArchive() {
        log.info("=== Midnight archive job started ===");
        try {
            archiveService.archiveExpiredEvents();
        } catch (Exception e) {
            log.error("Archive job failed: {}", e.getMessage(), e);
        }
        log.info("=== Midnight archive job complete ===");
    }
}
