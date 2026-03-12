package com.companycode.nse.service;

import com.companycode.nse.entity.SectorCompanies;
import com.companycode.nse.repository.SectorCompaniesRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for syncing NSE sectoral index data into the database.
 *
 * Flow:
 *   1. Establish a session with NSE by hitting the home page to obtain cookies.
 *   2. Fetch all NSE indices and filter down to SECTORAL INDICES only (19 sectors).
 *   3. For each sector, fetch its constituent stocks and upsert into sector_companies table.
 *
 * NSE's API requires a valid browser-like session (cookies) — plain HTTP calls are blocked.
 * This service handles that by doing a cookie handshake before making any API calls.
 */
@Service
public class NseSectorSyncService {

    // NSE home page — hit first to establish a session and get cookies
    private static final String NSE_HOME = "https://www.nseindia.com";

    // Returns all 135 NSE indices across all categories
    private static final String ALL_INDICES_URL = "https://www.nseindia.com/api/allIndices";

    // Returns constituent stocks for a given index — append URL-encoded index name
    private static final String STOCK_INDICES_URL = "https://www.nseindia.com/api/equity-stockIndices?index=";

    // The category value we filter on — only want sector-based indices, not broad market or strategy
    private static final String SECTORAL = "SECTORAL INDICES";

    private final SectorCompaniesRepository repository;
    private final ObjectMapper objectMapper;

    public NseSectorSyncService(SectorCompaniesRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Main sync method — fetches all 19 NSE sectoral indices and their constituent
     * stocks, then upserts each sector into the sector_companies table.
     *
     * Wrapped in @Transactional so if anything fails mid-sync, no partial data is saved.
     */
    @Transactional
    public void syncSectors() {
        try {
            // HttpClient with redirect following — NSE sometimes redirects on first hit
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(Redirect.ALWAYS)
                    .build();

            // Step 1: hit NSE home page to get session cookies
            // NSE blocks API calls without a valid session — this mimics what a browser does
            String cookies = fetchSessionCookies(client);

            // Step 2: fetch all indices and filter down to SECTORAL INDICES only
            String allIndicesJson = fetchWithCookies(client, ALL_INDICES_URL, cookies);
            JsonNode root = objectMapper.readTree(allIndicesJson);
            JsonNode data = root.get("data");

            List<String> sectorNames = new ArrayList<>();
            for (JsonNode index : data) {
                if (SECTORAL.equals(index.get("key").asText())) {
                    sectorNames.add(index.get("index").asText());
                }
            }
            // sectorNames now holds 19 sector names e.g. ["NIFTY IT", "NIFTY PHARMA", ...]

            // Step 3: for each sector, fetch its stocks and upsert into DB
            for (String sectorName : sectorNames) {

                // URL-encode the sector name — spaces become %20, & becomes %26
                String encodedSector = sectorName.replace(" ", "%20").replace("&", "%26");
                String stocksJson = fetchWithCookies(client, STOCK_INDICES_URL + encodedSector, cookies);

                JsonNode stocksRoot = objectMapper.readTree(stocksJson);
                JsonNode stockData = stocksRoot.get("data");

                // Extract just the symbol from each stock entry in the response
                // Skip the first entry — NSE includes the index itself (e.g. "NIFTY IT") as the first item
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

                // Serialize symbol list to JSON string e.g. ["INFY","TCS","WIPRO"]
                String companiesJson = objectMapper.writeValueAsString(symbols);

                // Upsert: if sector row already exists update it, otherwise create new
                SectorCompanies sector = repository.findBySectorName(sectorName)
                        .orElse(new SectorCompanies());
                sector.setSectorName(sectorName);
                sector.setCompanies(companiesJson);
                sector.setLastUpdated(LocalDateTime.now());
                repository.save(sector);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to sync NSE sectors", e);
        }
    }

    /**
     * Hits the NSE home page and extracts session cookies from the response headers.
     * NSE sets cookies like "nsit" and "nseappid" which are required for API access.
     * We strip the cookie attributes (Path, Secure, etc.) and keep only name=value pairs.
     */
    private String fetchSessionCookies(HttpClient client) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NSE_HOME))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Each set-cookie header looks like "nsit=abc123; Path=/; Secure"
        // We split on ";" and take only the first part (name=value)
        // Then join all cookies into one header string e.g. "nsit=abc; nseappid=xyz"
        return response.headers().allValues("set-cookie")
                .stream()
                .map(c -> c.split(";")[0])
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
    }

    /**
     * Makes an authenticated GET request to a NSE API endpoint using the session cookies.
     * Sets browser-like headers (User-Agent, Referer) to avoid being blocked by NSE.
     */
    private String fetchWithCookies(HttpClient client, String url, String cookies) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", NSE_HOME)  // NSE checks the Referer header
                .header("Cookie", cookies)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
