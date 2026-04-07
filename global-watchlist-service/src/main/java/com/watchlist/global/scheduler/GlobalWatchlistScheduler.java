package com.watchlist.global.scheduler;

import com.watchlist.global.service.GlobalWatchlistService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for the global watchlist service.
 *
 * <p>Both jobs also run once at application startup (via {@link ApplicationReadyEvent})
 * so the cache and DB are fully in sync immediately after boot.
 *
 * <p>Recurring schedule on trading days (Mon–Fri):
 * <ol>
 *   <li><b>Price refresh</b> — every 5 minutes from 9 AM to 3:55 PM IST,
 *       calls NSE directly to update the in-memory cache</li>
 *   <li><b>Market-close persist</b> — 3:30 PM IST, saves the cache to the
 *       {@code global_watchlist} table</li>
 * </ol>
 */
@Component
public class GlobalWatchlistScheduler {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistScheduler.class);

    private final GlobalWatchlistService service;

    /**
     * Constructs the scheduler with the required service dependency.
     *
     * @param service the service managing the in-memory price cache
     */
    public GlobalWatchlistScheduler(GlobalWatchlistService service) {
        this.service = service;
    }

    /**
     * Runs both jobs once after the application has fully started.
     *
     * <p>Order:
     * <ol>
     *   <li>{@link GlobalWatchlistService#refreshPrices()} — fetch live prices from NSE</li>
     *   <li>{@link GlobalWatchlistService#persistMarketClose()} — write fresh prices to DB</li>
     * </ol>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        logger.info("Startup: running refresh → persist");
        service.refreshPrices();
        service.persistMarketClose();
        logger.info("Startup jobs complete");
    }

    /**
     * Refreshes prices for all tracked companies every 5 minutes during market hours.
     *
     * <p>Cron: {@code 0 *}{@code /5 9-15 * * MON-FRI} — runs between 9 AM and 3:55 PM IST
     * on weekdays. Performs one NSE cookie handshake per run, then fetches prices
     * for all companies in the in-memory cache.
     */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI")
    public void refreshPrices() {
        logger.debug("Scheduled price refresh triggered");
        service.refreshPrices();
    }

    /**
     * Persists the end-of-day price snapshot to the database at market close.
     *
     * <p>Cron: {@code 0 30 15 * * MON-FRI} — runs at 3:30 PM IST on weekdays.
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void persistMarketClose() {
        logger.info("Scheduled market-close persistence triggered");
        service.persistMarketClose();
    }

    /**
     * Refreshes NIFTY 50 composition bi-weekly (1st and 15th of every month at 2 AM).
     *
     * <p>Cron: {@code 0 0 2 1,15 * ?}
     */
    @Scheduled(cron = "0 0 2 1,15 * ?")
    public void refreshNifty50Composition() {
        logger.info("Bi-weekly NIFTY 50 composition refresh triggered");
        service.refreshNifty50Composition();
    }
}
