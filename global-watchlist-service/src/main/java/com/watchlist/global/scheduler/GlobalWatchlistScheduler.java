package com.watchlist.global.scheduler;

import com.watchlist.global.service.GlobalWatchlistService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GlobalWatchlistScheduler {

    private final GlobalWatchlistService service;

    public GlobalWatchlistScheduler(GlobalWatchlistService service) {
        this.service = service;
    }

    /** Refresh prices every 5 minutes during market hours (9 AM – 3 PM IST, Mon–Fri). */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    public void refreshPrices() {
        service.refreshPrices();
    }

    /** Persist final prices to DB at market close — 3:30 PM IST, Mon–Fri. */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void persistMarketClose() {
        service.persistMarketClose();
    }
}
