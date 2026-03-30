package com.equity.watchlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Main entry point for the Watchlist Service application.
 *
 * <p>Bootstraps the Spring Boot application context and logs a startup
 * confirmation once all beans are initialised and the service is ready
 * to accept requests.
 */
@SpringBootApplication
public class WatchlistServiceApplication {

    private static final Logger logger = LogManager.getLogger(WatchlistServiceApplication.class);

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed to the JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(WatchlistServiceApplication.class, args);
    }

    /**
     * Logs a startup confirmation after the application context is fully initialised.
     * Triggered by {@link ApplicationReadyEvent}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Watchlist Service is READY on port 8080");
    }
}
