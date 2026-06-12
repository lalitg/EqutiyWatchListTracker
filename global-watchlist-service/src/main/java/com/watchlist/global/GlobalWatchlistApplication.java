package com.watchlist.global;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Global Watchlist Service.
 *
 * <p>This service maintains an in-memory price cache ({@link java.util.concurrent.ConcurrentHashMap})
 * for NIFTY 50 stocks and any additional companies tracked in user watchlists.
 * Prices are refreshed every 5 minutes during market hours via a scheduled job
 * and persisted to the {@code global_watchlist} table at market close (3:30 PM IST).
 */
@SpringBootApplication
@EnableScheduling
public class GlobalWatchlistApplication {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistApplication.class);

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed to the JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(GlobalWatchlistApplication.class, args);
    }

    /**
     * Logs a startup confirmation after the application context is fully initialised.
     * Triggered by {@link ApplicationReadyEvent}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Global Watchlist Service is READY on port 8083");
    }
}
