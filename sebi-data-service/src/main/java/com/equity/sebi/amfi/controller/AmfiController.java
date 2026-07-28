package com.equity.sebi.amfi.controller;

import com.equity.sebi.amfi.model.AmfiDownloadResult;
import com.equity.sebi.amfi.service.AmfiMonthlyNoteService;
import com.equity.sebi.amfi.service.AmfiMonthlyReportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/amfi")
public class AmfiController {

    private final AmfiMonthlyNoteService monthlyNoteService;
    private final AmfiMonthlyReportService monthlyReportService;

    @Value("${amfi.download.dir}")
    private String downloadDir;

    public AmfiController(AmfiMonthlyNoteService monthlyNoteService,
                           AmfiMonthlyReportService monthlyReportService) {
        this.monthlyNoteService   = monthlyNoteService;
        this.monthlyReportService = monthlyReportService;
    }

    /** GET /api/v1/amfi/status */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("monthlyNote", monthlyNoteService.getLastResult());
        response.put("monthlyReport", monthlyReportService.getLastResult());
        return ResponseEntity.ok(response);
    }

    /** POST /api/v1/amfi/trigger — manually re-run downloads for the previous month */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger() {
        AmfiDownloadResult noteResult   = monthlyNoteService.downloadPreviousMonth();
        AmfiDownloadResult reportResult = monthlyReportService.downloadPreviousMonth();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("monthlyNote", noteResult);
        response.put("monthlyReport", reportResult);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/amfi/files
     * Lists available months by scanning the download directory.
     * Returns [{month, noteAvailable, reportAvailable}] sorted newest first.
     */
    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> listFiles() throws IOException {
        Path dir = Path.of(downloadDir);
        List<Map<String, Object>> result = new ArrayList<>();

        if (!Files.exists(dir)) return ResponseEntity.ok(result);

        // Collect all yyyy-MM months that have at least one file
        Files.list(dir)
            .filter(p -> p.getFileName().toString().startsWith("amfi-monthly"))
            .map(p -> extractMonth(p.getFileName().toString()))
            .filter(m -> m != null)
            .distinct()
            .sorted((a, b) -> b.compareTo(a)) // newest first
            .forEach(month -> {
                boolean noteAvailable   = Files.exists(Path.of(downloadDir, "amfi-monthly-note-" + month + ".pdf"));
                boolean reportAvailable = Files.exists(Path.of(downloadDir, "amfi-monthly-" + month + ".xls"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("month", month);
                entry.put("noteAvailable", noteAvailable);
                entry.put("reportAvailable", reportAvailable);
                result.add(entry);
            });

        return ResponseEntity.ok(result);
    }

    /** GET /api/v1/amfi/files/{month}/note — serve the monthly note PDF */
    @GetMapping("/files/{month}/note")
    public ResponseEntity<byte[]> serveNote(@PathVariable String month) throws IOException {
        Path file = Path.of(downloadDir, "amfi-monthly-note-" + month + ".pdf");
        if (!Files.exists(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"amfi-note-" + month + ".pdf\"")
            .body(Files.readAllBytes(file));
    }

    /** GET /api/v1/amfi/files/{month}/report — serve the monthly report Excel */
    @GetMapping("/files/{month}/report")
    public ResponseEntity<byte[]> serveReport(@PathVariable String month) throws IOException {
        Path file = Path.of(downloadDir, "amfi-monthly-" + month + ".xls");
        if (!Files.exists(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"amfi-report-" + month + ".xls\"")
            .body(Files.readAllBytes(file));
    }

    private String extractMonth(String filename) {
        // amfi-monthly-note-2026-06.pdf  → 2026-06
        // amfi-monthly-2026-06.xls       → 2026-06
        try {
            String stripped = filename.replaceFirst("^amfi-monthly(-note)?-", "").replaceFirst("\\.(pdf|xls)$", "");
            if (stripped.matches("\\d{4}-\\d{2}")) return stripped;
        } catch (Exception ignored) {}
        return null;
    }
}
