package com.equity.sebi.scheduler;

import com.equity.sebi.service.SebiBuybackService;
import com.equity.sebi.service.SebiRssService;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SebiScheduler {

    private static final Logger log = LogManager.getLogger(SebiScheduler.class);

    private final SebiRssService rssService;
    private final SebiBuybackService buybackService;

    public SebiScheduler(SebiRssService rssService, SebiBuybackService buybackService) {
        this.rssService     = rssService;
        this.buybackService = buybackService;
    }

    @PostConstruct
    public void warmCacheOnStartup() {
        log.info("[SebiScheduler] Warming SEBI cache on startup...");
        try {
            rssService.fetchAndMatch();
            log.info("[SebiScheduler] RSS cache warmed on startup");
        } catch (Exception e) {
            log.warn("[SebiScheduler] RSS warm-up failed: {}", e.getMessage());
        }
        try {
            buybackService.fetchAndMatch();
            log.info("[SebiScheduler] Buyback cache warmed on startup");
        } catch (Exception e) {
            log.warn("[SebiScheduler] Buyback warm-up failed: {}", e.getMessage());
        }
    }

    // Daily at 6:00 AM IST (00:30 UTC)
    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    public void refreshDaily() {
        log.info("[SebiScheduler] Daily refresh started");
        try {
            rssService.fetchAndMatch();
            log.info("[SebiScheduler] RSS daily refresh done");
        } catch (Exception e) {
            log.error("[SebiScheduler] RSS daily refresh failed: {}", e.getMessage());
        }
        try {
            buybackService.fetchAndMatch();
            log.info("[SebiScheduler] Buyback daily refresh done");
        } catch (Exception e) {
            log.error("[SebiScheduler] Buyback daily refresh failed: {}", e.getMessage());
        }
    }
}
