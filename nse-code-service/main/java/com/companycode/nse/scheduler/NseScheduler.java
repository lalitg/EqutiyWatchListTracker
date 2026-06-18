package com.companycode.nse.scheduler;

import com.companycode.nse.service.NseSyncService;
import com.companycode.nse.service.NseSectorSyncService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that triggers NSE data sync jobs.
 *
 * <p>Runs two sync operations in sequence on each trigger:
 * <ol>
 *   <li><b>Company sync</b> — pulls all NSE-listed companies from
 *       {@code EQUITY_L.csv} into the {@code company_master} table</li>
 *   <li><b>Sector sync</b> — pulls sector-wise stock lists from the NSE API
 *       into the {@code sector_companies} table</li>
 * </ol>
 *
 * <p>Triggers:
 * <ul>
 *   <li>Once on application startup (via {@link ApplicationReadyEvent})</li>
 *   <li>Quarterly on the 1st of January, April, July, October at 6:00 AM UTC</li>
 * </ul>
 */
@Component
public class NseScheduler {

    private static final Logger logger = LogManager.getLogger(NseScheduler.class);

    private final NseSyncService       nseSyncService;
    private final NseSectorSyncService nseSectorSyncService;

    /**
     * Constructs the scheduler with required service dependencies.
     *
     * @param nseSyncService       service for syncing company master data
     * @param nseSectorSyncService service for syncing sector-company mappings
     */
    public NseScheduler(NseSyncService nseSyncService, NseSectorSyncService nseSectorSyncService) {
        this.nseSyncService       = nseSyncService;
        this.nseSectorSyncService = nseSectorSyncService;
    }

    /**
     * Runs both sync jobs once immediately after the application has fully started.
     *
     * <p>Ensures {@code company_master} and {@code sector_companies} are populated
     * with fresh data on every deployment or restart, without waiting for the weekly
     * Sunday schedule.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        logger.info("Startup: running company sync → sector sync");
        runSync();
        logger.info("Startup sync complete");
    }

    /**
     * Runs both sync jobs on the 1st of January, April, July, and October at 6:00 AM UTC.
     * Aligns with NSE's quarterly index rebalance calendar.
     */
    @Scheduled(cron = "0 0 6 1 1,4,7,10 *")
    public void runQuarterlySync() {
        logger.info("Quarterly sync triggered");
        runSync();
        logger.info("Quarterly sync complete");
    }

    /**
     * Executes company sync followed by sector sync in sequence.
     *
     * <p>Company sync runs first so that any new symbols added to {@code company_master}
     * are available when the sector sync references them.
     */
    private void runSync() {
        nseSyncService.syncCompanies();
        nseSectorSyncService.syncSectors();
    }
}
