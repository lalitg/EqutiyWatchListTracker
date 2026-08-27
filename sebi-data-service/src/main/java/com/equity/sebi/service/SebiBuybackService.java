package com.equity.sebi.service;

import com.equity.sebi.model.BuybackMatchResult;
import com.equity.sebi.model.BuybackPocResponse;
import com.equity.sebi.model.NseMatchResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class SebiBuybackService {

    private static final Logger log = LogManager.getLogger(SebiBuybackService.class);

    private static final long CACHE_TTL_MS = 15 * 60 * 1000L;

    private final AtomicReference<BuybackPocResponse> cachedResponse = new AtomicReference<>();
    private volatile Instant cacheExpiry = Instant.EPOCH;

    @Value("${sebi.buybacks.url}")
    private String buybacksUrl;

    private final NseCompanyStore nseStore;

    public SebiBuybackService(NseCompanyStore nseStore) {
        this.nseStore = nseStore;
    }

    public BuybackPocResponse fetchAndMatch() {
        if (Instant.now().isBefore(cacheExpiry) && cachedResponse.get() != null) {
            log.info("Buybacks cache hit — returning cached response (expires {})", cacheExpiry);
            return cachedResponse.get();
        }

        String fetchedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        List<BuybackRow> rows = scrapeRows();
        log.info("Scraped {} buyback rows from SEBI", rows.size());

        List<BuybackMatchResult> matches = new ArrayList<>();
        int unmatched = 0;

        for (BuybackRow row : rows) {
            Optional<NseMatchResult> hit = nseStore.match(row.companyName());
            if (hit.isPresent()) {
                NseMatchResult m = hit.get();
                log.info("BUYBACK MATCH '{}' → {} ({})", row.companyName(), m.company().symbol(), m.tier());
                matches.add(new BuybackMatchResult(
                        m.company().symbol(),
                        m.company().name(),
                        row.companyName(),
                        m.tier(),
                        row.date(),
                        row.url()
                ));
            } else {
                unmatched++;
                log.debug("BUYBACK NO MATCH: '{}'", row.companyName());
            }
        }

        log.info("Buyback matched {}/{}, unmatched {}", matches.size(), rows.size(), unmatched);
        BuybackPocResponse response = new BuybackPocResponse(fetchedAt, rows.size(), matches.size(), matches);

        if (!rows.isEmpty()) {
            cachedResponse.set(response);
            cacheExpiry = Instant.now().plusMillis(CACHE_TTL_MS);
            log.info("Buybacks response cached for 15 minutes");
        }
        return response;
    }

    /**
     * Returns only buyback matches for a specific NSE symbol.
     */
    public List<BuybackMatchResult> getMatchesForSymbol(String symbol) {
        BuybackPocResponse full = fetchAndMatch();
        String upper = symbol.toUpperCase();
        return full.matches().stream()
                .filter(m -> upper.equals(m.nseSymbol()))
                .collect(Collectors.toList());
    }

    private List<BuybackRow> scrapeRows() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "--silent", "--max-time", "20",
                    "--header", "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "--header", "Referer: https://www.sebi.gov.in/",
                    buybacksUrl
            );
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            String html = new String(proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            proc.waitFor();
            if (html.isBlank()) {
                log.error("curl returned empty body for buybacks page");
                return List.of();
            }
            Document doc = Jsoup.parse(html, buybacksUrl);
            Elements rows = doc.select("table#sample_1 tbody tr");
            List<BuybackRow> result = new ArrayList<>();

            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() < 2) continue;
                String date = cols.get(0).text().trim();
                Element anchor = cols.get(1).selectFirst("a.points");
                if (anchor == null) continue;
                String companyName = anchor.attr("title").trim();
                if (companyName.isBlank()) companyName = anchor.text().trim();
                String url = anchor.attr("href").trim();
                if (!date.isBlank() && !companyName.isBlank()) {
                    result.add(new BuybackRow(date, companyName, url));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to scrape SEBI buybacks page: {}", e.getMessage());
            return List.of();
        }
    }

    private record BuybackRow(String date, String companyName, String url) {}
}
