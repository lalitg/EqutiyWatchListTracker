package com.equity.sebi.amfi.controller;

import com.equity.sebi.amfi.model.AmfiDownloadResult;
import com.equity.sebi.amfi.service.AmfiMonthlyNoteService;
import com.equity.sebi.amfi.service.AmfiMonthlyReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual trigger + status endpoints for the AMFI monthly document downloads.
 * Primarily useful for verifying the feature without waiting for the monthly cron to fire.
 */
@RestController
@RequestMapping("/api/v1/amfi")
public class AmfiController {

    private final AmfiMonthlyNoteService monthlyNoteService;
    private final AmfiMonthlyReportService monthlyReportService;

    public AmfiController(AmfiMonthlyNoteService monthlyNoteService,
                           AmfiMonthlyReportService monthlyReportService) {
        this.monthlyNoteService   = monthlyNoteService;
        this.monthlyReportService = monthlyReportService;
    }

    /**
     * GET /api/v1/amfi/status
     * Returns the outcome of the most recent download attempt for each document
     * (null if neither the startup run nor any manual trigger has happened yet).
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("monthlyNote", monthlyNoteService.getLastResult());
        response.put("monthlyReport", monthlyReportService.getLastResult());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/amfi/trigger
     * Manually re-runs both downloads immediately for the previous calendar month.
     * A no-op re-download if the target month's file already exists on disk.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger() {
        AmfiDownloadResult noteResult   = monthlyNoteService.downloadPreviousMonth();
        AmfiDownloadResult reportResult = monthlyReportService.downloadPreviousMonth();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("monthlyNote", noteResult);
        response.put("monthlyReport", reportResult);
        return ResponseEntity.ok(response);
    }
}
