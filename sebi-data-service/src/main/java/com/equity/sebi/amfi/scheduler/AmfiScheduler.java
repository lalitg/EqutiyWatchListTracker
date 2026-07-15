package com.equity.sebi.amfi.scheduler;

import com.equity.sebi.amfi.service.AmfiMonthlyNoteService;
import com.equity.sebi.amfi.service.AmfiMonthlyReportService;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the monthly download of AMFI documents (Monthly Note PDF + AMFI Monthly Excel
 * report) for the previous calendar month.
 *
 * <p>Runs once immediately on application startup — so a redeploy or restart doesn't have to
 * wait for the next scheduled fire to pick up a month it hasn't fetched yet. Safe to call
 * repeatedly: both services skip the download if the target month's file already exists on
 * disk. Also runs on a monthly cron for the normal ongoing cadence.
 */
@Component
public class AmfiScheduler {

    private static final Logger log = LogManager.getLogger(AmfiScheduler.class);

    private final AmfiMonthlyNoteService monthlyNoteService;
    private final AmfiMonthlyReportService monthlyReportService;

    public AmfiScheduler(AmfiMonthlyNoteService monthlyNoteService,
                          AmfiMonthlyReportService monthlyReportService) {
        this.monthlyNoteService   = monthlyNoteService;
        this.monthlyReportService = monthlyReportService;
    }

    @PostConstruct
    public void runOnStartup() {
        log.info("[AmfiScheduler] Running AMFI downloads on startup...");
        runDownloads();
    }

    // 5th of every month at 7:00 AM IST (01:30 UTC) — a few days' buffer past month-end
    // in case AMFI publishes the previous month's documents late.
    @Scheduled(cron = "0 30 1 5 * *", zone = "UTC")
    public void refreshMonthly() {
        log.info("[AmfiScheduler] Monthly AMFI download started");
        runDownloads();
    }

    private void runDownloads() {
        try {
            var noteResult = monthlyNoteService.downloadPreviousMonth();
            log.info("[AmfiScheduler] Monthly Note result: {}", noteResult);
        } catch (Exception e) {
            log.error("[AmfiScheduler] Monthly Note download failed: {}", e.getMessage());
        }
        try {
            var reportResult = monthlyReportService.downloadPreviousMonth();
            log.info("[AmfiScheduler] Monthly Report result: {}", reportResult);
        } catch (Exception e) {
            log.error("[AmfiScheduler] Monthly Report download failed: {}", e.getMessage());
        }
    }
}
