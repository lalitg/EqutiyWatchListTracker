package com.watchlist.global.scheduler;

import com.watchlist.global.service.GlobalIndexService;
import com.watchlist.global.service.GlobalWatchlistService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GlobalWatchlistScheduler {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistScheduler.class);

    private final GlobalWatchlistService service;
    private final GlobalIndexService     globalIndexService;

    public GlobalWatchlistScheduler(GlobalWatchlistService service,
                                    GlobalIndexService globalIndexService) {
        this.service            = service;
        this.globalIndexService = globalIndexService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        logger.info("Startup: seeding caches...");
        service.refreshPrices();
        service.persistMarketClose();
        globalIndexService.refreshAll();
        logger.info("Startup jobs complete");
    }

    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    public void refreshPrices() { service.refreshPrices(); }

    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void persistMarketClose() { service.persistMarketClose(); }

    @Scheduled(cron = "0 */30 * * * *")
    public void refreshGlobalIndices() { globalIndexService.refreshAll(); }

    @Scheduled(cron = "0 0 2 1,15 * ?")
    public void refreshNifty50Composition() { service.refreshNifty50Composition(); }
}
