package com.companycode.nse.service;

import com.companycode.nse.entity.SectorCompanies;
import com.companycode.nse.repository.SectorCompaniesRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for syncing NSE sectoral index data into the database.
 *
 * <p>Fetches all 19 NSE sectoral indices and their constituent stocks from the
 * NSE API, then upserts each sector into the {@code sector_companies} table.
 *
 * <p>NSE's API requires a valid browser session cookie — plain HTTP calls are
 * blocked. This service performs a cookie handshake against the NSE home page
 * before making any API calls.
 *
 * <p>The underlying {@link HttpClient} is shared across all calls within a sync
 * run — it is thread-safe and reused rather than recreated per request.
 */
@Service
public class NseSectorSyncService {

    private static final Logger logger = LogManager.getLogger(NseSectorSyncService.class);

    private static final String NSE_HOME         = "https://www.nseindia.com";
    private static final String ALL_INDICES_URL  = "https://www.nseindia.com/api/allIndices";
    private static final String STOCK_INDICES_URL = "https://www.nseindia.com/api/equity-stockIndices?index=";

    /** Category value used to filter down to sector-based indices only. */
    private static final String SECTORAL = "SECTORAL INDICES";

    /** Shared, thread-safe HTTP client — reused across all NSE API calls. */
    private final HttpClient                httpClient = HttpClient.newBuilder()
                                                                   .followRedirects(Redirect.ALWAYS)
                                                                   .build();
    private final SectorCompaniesRepository repository;
    private final ObjectMapper              objectMapper;

    /**
     * Constructs the service with required dependencies.
     *
     * @param repository   JPA repository for the {@code sector_companies} table
     * @param objectMapper Jackson mapper for parsing NSE JSON responses
     */
    public NseSectorSyncService(SectorCompaniesRepository repository, ObjectMapper objectMapper) {
        this.repository   = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all 19 NSE sectoral indices and upserts each into the
     * {@code sector_companies} table.
     *
     * <p>Steps:
     * <ol>
     *   <li>Obtain NSE session cookies via a home-page handshake</li>
     *   <li>Fetch all NSE indices and filter down to {@code SECTORAL INDICES}</li>
     *   <li>For each sector, fetch its constituent stocks from the NSE API</li>
     *   <li>Upsert the sector row (insert if new, update if existing)</li>
     * </ol>
     *
     * @throws RuntimeException wrapping any HTTP or parsing error encountered during sync
     */
    @Transactional
    public void syncSectors() {
        logger.info("Starting sector sync from NSE API...");
        try {
            // Step 1: cookie handshake — NSE blocks API calls without a valid session
            String cookies = fetchSessionCookies();
            if (cookies.isEmpty()) {
                logger.warn("Could not obtain NSE session cookies — sector sync aborted");
                return;
            }
            logger.debug("NSE session cookies obtained");

            // Step 2: fetch all indices and extract only SECTORAL INDICES names
            String allIndicesJson = fetchWithCookies(ALL_INDICES_URL, cookies);
            JsonNode data = objectMapper.readTree(allIndicesJson).get("data");

            List<String> sectorNames = new ArrayList<>();
            for (JsonNode index : data) {
                if (SECTORAL.equals(index.get("key").asText())) {
                    sectorNames.add(index.get("index").asText());
                }
            }
            logger.info("Found {} sectoral indices to sync", sectorNames.size());

            // Step 3: for each sector fetch its stocks and upsert into DB
            int synced = 0;
            for (String sectorName : sectorNames) {
                // URL-encode the sector name — spaces become %20, & becomes %26
                String encodedSector = sectorName.replace(" ", "%20").replace("&", "%26");
                String stocksJson = fetchWithCookies(STOCK_INDICES_URL + encodedSector, cookies);

                JsonNode stockData = objectMapper.readTree(stocksJson).get("data");

                // Extract stock symbols — skip the first entry (NSE includes the index itself)
                List<String> symbols = new ArrayList<>();
                if (stockData != null) {
                    boolean first = true;
                    for (JsonNode stock : stockData) {
                        if (first) { first = false; continue; }
                        JsonNode symbolNode = stock.get("symbol");
                        if (symbolNode != null && !symbolNode.asText().isBlank()) {
                            symbols.add(symbolNode.asText());
                        }
                    }
                }

                // Upsert: update existing row or create a new one
                SectorCompanies sector = repository.findBySectorName(sectorName)
                        .orElse(new SectorCompanies());
                sector.setSectorName(sectorName);
                sector.setCompanies(objectMapper.writeValueAsString(symbols));
                sector.setLastUpdated(LocalDateTime.now());
                repository.save(sector);

                logger.debug("Synced sector '{}' with {} stocks", sectorName, symbols.size());
                synced++;
            }

            logger.info("Sector sync complete — synced {} sectors", synced);

        } catch (Exception e) {
            logger.error("Sector sync failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to sync NSE sectors", e);
        }
    }

    /**
     * Hits the NSE home page to obtain session cookies required for subsequent API calls.
     *
     * <p>NSE sets cookies like {@code nsit} and {@code nseappid} that must be present
     * on all API requests. Cookie attributes (Path, Secure, etc.) are stripped —
     * only the {@code name=value} pairs are retained.
     *
     * @return a semicolon-separated cookie string (e.g. {@code "nsit=abc; nseappid=xyz"}),
     *         or an empty string if the handshake fails
     * @throws Exception if the HTTP request fails
     */
    private String fetchSessionCookies() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NSE_HOME))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Strip cookie attributes (Path, Secure, etc.) — keep only "name=value"
        return response.headers().allValues("set-cookie")
                .stream()
                .map(c -> c.split(";")[0])
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
    }

    /**
     * Makes an authenticated GET request to a NSE API endpoint using the shared
     * {@link HttpClient} and the provided session cookies.
     *
     * <p>Sets browser-like headers ({@code User-Agent}, {@code Referer}) to avoid
     * being blocked by NSE's request validation.
     *
     * @param url     the NSE API endpoint URL
     * @param cookies the session cookie string from {@link #fetchSessionCookies()}
     * @return the raw JSON response body
     * @throws Exception if the HTTP request fails
     */
    private String fetchWithCookies(String url, String cookies) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", NSE_HOME)
                .header("Cookie", cookies)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
