package com.equity.sebi.amfi.service;

import com.equity.sebi.amfi.model.AmfiDownloadResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Downloads AMFI's monthly Excel report (the "AMFI Monthly" page) for the previous
 * calendar month.
 *
 * <p>Unlike the Monthly Note (see {@link AmfiMonthlyNoteService}), this source has a fully
 * predictable URL — no scraping needed. Verified live against portal.amfiindia.com:
 * <pre>
 *   https://portal.amfiindia.com/spages/am{3-letter-lowercase-month}{4-digit-year}repo.xls
 *   e.g. amjun2026repo.xls, ammay2026repo.xls
 * </pre>
 * A PDF variant exists at the same base name but is intentionally not downloaded here —
 * only the Excel file is needed.
 */
@Service
public class AmfiMonthlyReportService {

    private static final Logger log = LogManager.getLogger(AmfiMonthlyReportService.class);

    @Value("${amfi.monthly-report.base-url}")
    private String baseUrl;

    @Value("${amfi.download.dir}")
    private String downloadDir;

    private final RestTemplate restTemplate;

    private final AtomicReference<AmfiDownloadResult> lastResult = new AtomicReference<>();

    public AmfiMonthlyReportService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Downloads the previous month's AMFI Monthly Excel report, unless a file for that
     * month has already been saved — safe to call on every startup and every scheduled run.
     *
     * @return the outcome of this attempt
     */
    public AmfiDownloadResult downloadPreviousMonth() {
        LocalDate targetMonth = LocalDate.now().minusMonths(1);
        String monthAbbrev = targetMonth.getMonth()
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                .toLowerCase(Locale.ENGLISH);
        int year = targetMonth.getYear();
        String targetLabel = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        String url = baseUrl + "/am" + monthAbbrev + year + "repo.xls";
        Path savePath = Path.of(downloadDir, "amfi-monthly-" + targetLabel + ".xls");

        if (Files.exists(savePath)) {
            log.info("AMFI Monthly report for {} already downloaded at {} — skipping", targetLabel, savePath);
            AmfiDownloadResult cached = AmfiDownloadResult.success(targetLabel, url, savePath.toString());
            lastResult.set(cached);
            return cached;
        }

        log.info("Downloading AMFI Monthly report for {} from {}", targetLabel, url);
        try {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                AmfiDownloadResult failure = AmfiDownloadResult.failure(targetLabel, url, "Empty response body");
                lastResult.set(failure);
                log.error("AMFI Monthly report download returned empty body for {}", targetLabel);
                return failure;
            }

            Files.createDirectories(savePath.getParent());
            Files.write(savePath, bytes);

            log.info("Saved AMFI Monthly report for {} to {} ({} bytes)", targetLabel, savePath, bytes.length);
            AmfiDownloadResult result = AmfiDownloadResult.success(targetLabel, url, savePath.toString());
            lastResult.set(result);
            return result;

        } catch (IOException e) {
            AmfiDownloadResult failure = AmfiDownloadResult.failure(targetLabel, url, "File write failed: " + e.getMessage());
            lastResult.set(failure);
            log.error("Failed to save AMFI Monthly report for {}: {}", targetLabel, e.getMessage());
            return failure;
        } catch (Exception e) {
            AmfiDownloadResult failure = AmfiDownloadResult.failure(targetLabel, url, "Download failed: " + e.getMessage());
            lastResult.set(failure);
            log.error("Failed to download AMFI Monthly report for {} from {}: {}", targetLabel, url, e.getMessage());
            return failure;
        }
    }

    public AmfiDownloadResult getLastResult() {
        return lastResult.get();
    }
}
