package com.equity.fundamentals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for fundamentals-service.
 *
 * Responsibilities:
 *   1. Fetch quarterly results (last 4 quarters) for all NSE companies — nightly during results season.
 *   2. Fetch annual balance sheets for all NSE companies — monthly on 1st of each month.
 *
 * Data is fetched from a local Python yfinance wrapper (localhost:5001) and stored in PostgreSQL.
 * The frontend reads this data via new endpoints added to main-api-service.
 *
 * Standalone run:
 *   java -jar fundamentals-service-0.0.1-SNAPSHOT-exec.jar \
 *        --DB_HOST=... --DB_PORT=5432 --DB_NAME=watchlisttracker \
 *        --DB_USERNAME=postgres --DB_PASSWORD=... \
 *        --YFINANCE_BASE_URL=http://localhost:5001
 *
 * Unified-scheduler integration:
 *   Called via startService("fundamentals", FundamentalsApplication.class,
 *                           "application-fundamentals", "8086")
 *   in UnifiedSchedulerApplication.java.
 */
@SpringBootApplication
@EnableScheduling
public class FundamentalsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundamentalsApplication.class, args);
    }
}
