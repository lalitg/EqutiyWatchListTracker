package com.equity.sebi.amfi.service;

import com.equity.sebi.amfi.model.AmfiDownloadResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Downloads AMFI's "Monthly Note" PDF for the previous calendar month.
 *
 * <p>Unlike {@link AmfiMonthlyReportService}, this source has NO predictable URL — every
 * filename carries a random hex suffix (e.g. {@code AMFI_Monthly_Note_June2026_110c873c23.pdf})
 * and the month portion is inconsistently formatted month to month (sometimes the full name,
 * sometimes abbreviated, sometimes with an underscore before the year, occasionally with a
 * trailing "_Final"). So this service scrapes the listing page — same curl-then-jsoup pattern
 * already used by {@link com.equity.sebi.service.SebiBuybackService} — and matches the target
 * month by normalized substring rather than constructing the URL directly.
 */
@Service
public class AmfiMonthlyNoteService {

    private static final Logger log = LogManager.getLogger(AmfiMonthlyNoteService.class);

    @Value("${amfi.monthlynote.url}")
    private String listingUrl;

    @Value("${amfi.download.dir}")
    private String downloadDir;

    private final RestTemplate restTemplate;

    private final AtomicReference<AmfiDownloadResult> lastResult = new AtomicReference<>();

    public AmfiMonthlyNoteService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Downloads the previous month's AMFI Monthly Note PDF, unless a file for that month
     * has already been saved — safe to call on every startup and every scheduled run.
     *
     * @return the outcome of this attempt
     */
    public AmfiDownloadResult downloadPreviousMonth() {
        LocalDate targetMonth = LocalDate.now().minusMonths(1);
        String targetLabel = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Path savePath = Path.of(downloadDir, "amfi-monthly-note-" + targetLabel + ".pdf");

        if (Files.exists(savePath)) {
            log.info("AMFI Monthly Note for {} already downloaded at {} — skipping", targetLabel, savePath);
            AmfiDownloadResult cached = AmfiDownloadResult.success(targetLabel, listingUrl, savePath.toString());
            lastResult.set(cached);
            return cached;
        }

        // AMFI's own filenames alternate between full month names ("November2025") and
        // 3-letter abbreviations ("Mar_2026"), so both candidates must be checked.
        String fullName = targetMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ENGLISH);
        String shortName = targetMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ENGLISH);
        int year = targetMonth.getYear();
        List<String> candidates = List.of(fullName + year, shortName + year);

        String html = fetchHtml(listingUrl);
        if (html == null || html.isBlank()) {
            AmfiDownloadResult failure = AmfiDownloadResult.failure(targetLabel, listingUrl, "Listing page fetch returned empty body");
            lastResult.set(failure);
            log.error("AMFI Monthly Note listing page fetch failed for {}", targetLabel);
            return failure;
        }

        Optional<String> matchedUrl = findMatchingPdfUrl(html, candidates);
        if (matchedUrl.isEmpty()) {
            AmfiDownloadResult failure = AmfiDownloadResult.failure(
                    targetLabel, listingUrl, "No PDF link found matching " + candidates);
            lastResult.set(failure);
            log.warn("No AMFI Monthly Note found for {} yet (checked candidates: {})", targetLabel, candidates);
            return failure;
        }

        String pdfUrl = matchedUrl.get();
        try {
            byte[] bytes = restTemplate.getForObject(pdfUrl, byte[].class);
            if (bytes == null || bytes.length == 0) {
                AmfiDownloadResult failure = AmfiDownloadResult.failure(targetLabel, pdfUrl, "Empty PDF response body");
                lastResult.set(failure);
                log.error("AMFI Monthly Note download returned empty body for {} from {}", targetLabel, pdfUrl);
                return failure;
            }

            Files.createDirectories(savePath.getParent());
            Files.write(savePath, bytes);

            log.info("Saved AMFI Monthly Note for {} to {} ({} bytes, source {})", targetLabel, savePath, bytes.length, pdfUrl);
            AmfiDownloadResult result = AmfiDownloadResult.success(targetLabel, pdfUrl, savePath.toString());
            lastResult.set(result);
            return result;

        } catch (IOException e) {
            AmfiDownloadResult failure = AmfiDownloadResult.failure(targetLabel, pdfUrl, "File write failed: " + e.getMessage());
            lastResult.set(failure);
            log.error("Failed to save AMFI Monthly Note for {}: {}", targetLabel, e.getMessage());
            return failure;
        } catch (Exception e) {
            AmfiDownloadResult failure = AmfiDownloadResult.failure(targetLabel, pdfUrl, "Download failed: " + e.getMessage());
            lastResult.set(failure);
            log.error("Failed to download AMFI Monthly Note for {} from {}: {}", targetLabel, pdfUrl, e.getMessage());
            return failure;
        }
    }

    /**
     * Searches all PDF anchors on the listing page for one whose href, normalized (lowercased,
     * with all non-alphanumeric characters stripped), contains either the full or abbreviated
     * target-month token (e.g. "june2026" or "jun2026").
     */
    private Optional<String> findMatchingPdfUrl(String html, List<String> candidates) {
        Document doc = Jsoup.parse(html, listingUrl);
        Elements links = doc.select("a[href$=\".pdf\"]");

        for (Element link : links) {
            String href = link.attr("abs:href");
            String normalized = href.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
            for (String candidate : candidates) {
                if (normalized.contains(candidate)) {
                    return Optional.of(href);
                }
            }
        }
        return Optional.empty();
    }

    private String fetchHtml(String url) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "--silent", "--max-time", "20",
                    "--header", "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "--header", "Referer: https://www.amfiindia.com/",
                    url
            );
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            String html = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = proc.waitFor();
            if (exit != 0) {
                log.error("curl exited with code {} for AMFI Monthly Note listing page", exit);
                return null;
            }
            return html;
        } catch (Exception e) {
            log.error("Failed to fetch AMFI Monthly Note listing page: {}", e.getMessage());
            return null;
        }
    }

    public AmfiDownloadResult getLastResult() {
        return lastResult.get();
    }
}
