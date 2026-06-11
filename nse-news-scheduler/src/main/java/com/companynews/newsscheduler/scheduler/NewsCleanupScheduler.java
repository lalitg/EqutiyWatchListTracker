package com.companynews.newsscheduler.scheduler;

import com.companynews.newsscheduler.service.NewsCleanupService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled component that triggers the news retention cleanup every hour.
 *
 * <p>Delegates all logic to {@link NewsCleanupService#cleanup()}.
 * Cron expression is configurable via {@code news.cleanup.cron} in {@code application.properties}.
 */
@Component
public class NewsCleanupScheduler {

    private static final Logger log = LogManager.getLogger(NewsCleanupScheduler.class);

    private final NewsCleanupService newsCleanupService;

    public NewsCleanupScheduler(NewsCleanupService newsCleanupService) {
        this.newsCleanupService = newsCleanupService;
    }

    @Scheduled(cron = "${news.cleanup.cron}")
    public void runCleanup() {
        log.info("=== News cleanup job started ===");
        newsCleanupService.cleanup();
    }
}
