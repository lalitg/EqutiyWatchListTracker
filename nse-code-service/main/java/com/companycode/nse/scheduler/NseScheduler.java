package com.companycode.nse.scheduler;

import com.companycode.nse.service.NseSyncService;
import com.companycode.nse.service.NseSectorSyncService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that triggers NSE data sync jobs.
 * Runs two sync operations in sequence:
 *   1. Company sync  — pulls all NSE-listed companies from EQUITY_L.csv into company_master
 *   2. Sector sync   — pulls sector-wise stock lists from NSE API into sector_companies
 *
 * Triggers:
 *   - On application startup (via ApplicationReadyEvent)
 *   - Every Sunday at 6:00 AM UTC (weekly refresh)
 */
@Component
public class NseScheduler {

    private final NseSyncService service;
    private final NseSectorSyncService sectorSyncService;

    public NseScheduler(NseSyncService service, NseSectorSyncService sectorSyncService) {
        this.service = service;
        this.sectorSyncService = sectorSyncService;
    }

    /**
     * Runs once when the application has fully started.
     * Ensures the DB is populated with fresh data on every deployment/restart.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        service.syncCompanies();       // Step 1: sync ~2200 companies from NSE CSV
        sectorSyncService.syncSectors(); // Step 2: sync 19 sectors and their stocks from NSE API
    }

    /**
     * Runs every Sunday at 6:00 AM UTC.
     * Keeps company and sector data up to date with any weekly changes on NSE.
     */
    @Scheduled(cron = "0 0 6 ? * SUN")
    public void runEverySunday() {
        service.syncCompanies();       // Step 1: sync ~2200 companies from NSE CSV
        sectorSyncService.syncSectors(); // Step 2: sync 19 sectors and their stocks from NSE API
    }
}
