package com.companycode.nse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the NSE Enterprise Scheduler service.
 *
 * <p>This service keeps two reference tables in sync with NSE data:
 * <ul>
 *   <li>{@code company_master} — all ~2200 NSE-listed companies, sourced from
 *       the {@code EQUITY_L.csv} file published by NSE</li>
 *   <li>{@code sector_companies} — 19 NSE sectoral indices and their constituent
 *       stocks, sourced from the NSE {@code allIndices} API</li>
 * </ul>
 *
 * <p>Both tables are refreshed on startup and every Sunday at 6:00 AM UTC.
 */
@SpringBootApplication
@EnableScheduling
public class NseEnterpriseSchedulerApplication {

    private static final Logger logger = LogManager.getLogger(NseEnterpriseSchedulerApplication.class);

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed to the JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(NseEnterpriseSchedulerApplication.class, args);
    }

    /**
     * Logs a startup confirmation after the application context is fully initialised.
     * Triggered by {@link ApplicationReadyEvent}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("NSE Enterprise Scheduler is READY on port 8084");
    }
}
