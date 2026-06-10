package com.watchlist.global.scheduler;

import com.watchlist.global.service.DomesticIndexService;
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
    private final DomesticIndexService   domesticIndexService;

    public GlobalWatchlistScheduler(GlobalWatchlistService service,
                                    GlobalIndexService globalIndexService,
                                    DomesticIndexService domesticIndexService) {
        this.service              = service;
        this.globalIndexService   = globalIndexService;
        this.domesticIndexService = domesticIndexService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        logger.info("Startup: seeding caches...");
        service.refreshPrices();
        service.persistMarketClose();
        globalIndexService.refreshAll();
        domesticIndexService.refreshDomesticIndices();
        domesticIndexService.refreshSectorIndices();
        logger.info("Startup jobs complete");
    }

    /** NSE stock prices — every 5 min during market hours */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    public void refreshPrices() {
        service.refreshPrices();
    }

    /** Market-close DB persist */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void persistMarketClose() {
        service.persistMarketClose();
    }

    /** Global indices (Yahoo) — every 5 min, 24/7 (markets in different timezones) */
    @Scheduled(cron = "0 */5 * * * *")
    public void refreshGlobalIndices() {
        globalIndexService.refreshAll();
    }

    /** Domestic + sector index headers and symbol membership map — every 5 min during market hours */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    public void refreshDomesticIndices() {
        domesticIndexService.refreshDomesticIndices();
        domesticIndexService.refreshSectorIndices();
    }

    /** Nifty 50 composition — bi-weekly */
    @Scheduled(cron = "0 0 2 1,15 * ?")
    public void refreshNifty50Composition() {
        service.refreshNifty50Composition();
    }
}
