package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchlist.global.model.IndexCompanyEntry;
import com.watchlist.global.model.IndexSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class NseClient {

    private static final Logger logger = LogManager.getLogger(NseClient.class);

    private static final String NSE_HOME         = "https://www.nseindia.com";
    private static final String INDEX_URL_TPL    = "https://www.nseindia.com/api/equity-stockIndices?index=";
    private static final String QUOTE_EQUITY_URL = "https://www.nseindia.com/api/quote-equity?symbol=";
    private static final String STOCK_INDICES_URL= "https://www.nseindia.com/api/stock-indices?symbol=";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Backward-compat wrapper — returns NIFTY 50 stock symbols only. */
    public List<String> fetchNifty50Symbols() {
        return fetchIndexSymbols("NIFTY 50");
    }

    /** Returns the list of stock symbols for any NSE index (excludes the index row itself). */
    public List<String> fetchIndexSymbols(String indexName) {
        List<IndexCompanyEntry> companies = fetchIndexCompanies(indexName);
        List<String> symbols = new ArrayList<>(companies.size());
        for (IndexCompanyEntry e : companies) symbols.add(e.getSymbol());
        return symbols;
    }

    /**
     * Returns the index-level summary (LTP, change, chg%) for the given NSE index.
     * This is the first row returned by the equity-stockIndices API.
     */
    public IndexSummary fetchIndexHeader(String indexName) {
        try {
            JsonNode data = fetchData(indexName);
            if (data == null || !data.isArray() || data.size() == 0) return null;
            JsonNode row = data.get(0);
            IndexSummary summary = new IndexSummary(indexName, indexName, true);
            summary.setLtp(decimal(row, "lastPrice"));
            summary.setChange(decimal(row, "change"));
            summary.setChangePercent(decimal(row, "pChange"));
            summary.setLastUpdated(LocalDateTime.now());
            logger.debug("Fetched header for '{}': ltp={}, pChange={}", indexName, summary.getLtp(), summary.getChangePercent());
            return summary;
        } catch (Exception e) {
            logger.error("Failed to fetch index header for '{}': {}", indexName, e.getMessage());
            return null;
        }
    }

    /**
     * Returns all constituent companies for the given NSE index with price data.
     * The index row itself (first item) is excluded.
     */
    public List<IndexCompanyEntry> fetchIndexCompanies(String indexName) {
        try {
            JsonNode data = fetchData(indexName);
            if (data == null || !data.isArray()) return Collections.emptyList();

            List<IndexCompanyEntry> result = new ArrayList<>();
            boolean first = true;
            for (JsonNode item : data) {
                if (first) { first = false; continue; } // skip index row
                IndexCompanyEntry entry = new IndexCompanyEntry();
                entry.setSymbol(item.path("symbol").asText());
                entry.setLtp(decimal(item, "lastPrice"));
                entry.setChange(decimal(item, "change"));
                entry.setChangePercent(decimal(item, "pChange"));
                entry.setWeek52High(decimal(item, "yearHigh"));
                entry.setWeek52Low(decimal(item, "yearLow"));
                entry.setPreviousClose(decimal(item, "previousClose"));
                entry.setTradedVolume(decimal(item, "totalTradedVolume"));
                result.add(entry);
            }
            logger.info("Fetched {} companies for index '{}'", result.size(), indexName);
            return result;
        } catch (Exception e) {
            logger.error("Failed to fetch companies for index '{}': {}", indexName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Returns the full registered company name from NSE's quote-equity API, or null on failure. */
    public String fetchCompanyName(String symbol) {
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
            String cookies = fetchSessionCookies(client);
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String json = fetchWithCookies(client, QUOTE_EQUITY_URL + encoded, cookies);
            JsonNode root = objectMapper.readTree(json);
            String name = root.path("info").path("companyName").asText(null);
            logger.debug("Company name for '{}': {}", symbol, name);
            return name;
        } catch (Exception e) {
            logger.error("Failed to fetch company name for '{}': {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Returns all NSE index keys (e.g. "NIFTY PSU BANK", "NIFTY 50") that the given
     * stock belongs to, by calling NSE's stock-indices API.
     */
    public List<String> fetchStockIndexMemberships(String symbol) {
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
            String cookies = fetchSessionCookies(client);
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String json = fetchWithCookies(client, STOCK_INDICES_URL + encoded, cookies);
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) return Collections.emptyList();
            List<String> indices = new ArrayList<>();
            for (JsonNode item : data) {
                String key = item.path("indexSymbol").asText(null);
                if (key != null && !key.isBlank()) indices.add(key.toUpperCase());
            }
            logger.info("Index memberships for '{}': {}", symbol, indices);
            return indices;
        } catch (Exception e) {
            logger.error("Failed to fetch index memberships for '{}': {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private JsonNode fetchData(String indexName) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
        String cookies = fetchSessionCookies(client);
        String encoded = URLEncoder.encode(indexName, StandardCharsets.UTF_8);
        String json = fetchWithCookies(client, INDEX_URL_TPL + encoded, cookies);
        JsonNode root = objectMapper.readTree(json);
        return root.get("data");
    }

    private String fetchSessionCookies(HttpClient client) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NSE_HOME))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.headers().allValues("set-cookie")
                .stream()
                .map(c -> c.split(";")[0])
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
    }

    private String fetchWithCookies(HttpClient client, String url, String cookies) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", NSE_HOME)
                .header("Cookie", cookies)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        try { return f.decimalValue(); } catch (Exception e) { return null; }
    }
}
