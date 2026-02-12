package com.equity.watchlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Main entry point for the Watchlist Service application.
 */
@SpringBootApplication
public class WatchlistServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(WatchlistServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Watchlist Service is READY on port 8080");
    }
}
