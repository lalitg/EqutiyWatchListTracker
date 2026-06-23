package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchlist.global.model.IndexCompanyEntry;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class NseClient {

    private static final Logger logger = LogManager.getLogger(NseClient.class);

    private static final String NSE_HOME         = "https://www.nseindia.com";
    private static final String INDEX_URL_TPL    = "https://www.nseindia.com/api/equity-stockIndices?index=";
    private static final String QUOTE_EQUITY_URL = "https://www.nseindia.com/api/quote-equity?symbol=";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> fetchNifty50Symbols() {
        return fetchIndexSymbols("NIFTY 50");
    }

    public List<String> fetchIndexSymbols(String indexName) {
        List<IndexCompanyEntry> companies = fetchIndexCompanies(indexName);
        List<String> symbols = new ArrayList<>(companies.size());
        for (IndexCompanyEntry e : companies) symbols.add(e.getSymbol());
        return symbols;
    }

    public List<IndexCompanyEntry> fetchIndexCompanies(String indexName) {
        try {
            JsonNode data = fetchData(indexName);
            if (data == null || !data.isArray()) return Collections.emptyList();

            List<IndexCompanyEntry> result = new ArrayList<>();
            boolean first = true;
            for (JsonNode item : data) {
                if (first) { first = false; continue; }
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

    public CompanyInfo fetchCompanyInfo(String symbol) {
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).version(HttpClient.Version.HTTP_1_1).build();
            String cookies = fetchSessionCookies(client);
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String json = fetchWithCookies(client, QUOTE_EQUITY_URL + encoded, cookies);
            JsonNode root = objectMapper.readTree(json);

            String name = root.path("info").path("companyName").asText(null);

            List<String> indices = new ArrayList<>();
            JsonNode indAll = root.path("metadata").path("pdSectorIndAll");
            if (indAll.isArray()) {
                for (JsonNode n : indAll) {
                    String key = n.asText(null);
                    if (key != null && !key.isBlank()) indices.add(key.toUpperCase());
                }
            }

            logger.info("CompanyInfo for '{}': name={}, indices={}", symbol, name, indices.size());
            return new CompanyInfo(name, indices);
        } catch (Exception e) {
            logger.error("Failed to fetch company info for '{}': {}", symbol, e.getMessage());
            return new CompanyInfo(null, Collections.emptyList());
        }
    }

    public String fetchCompanyName(String symbol) {
        return fetchCompanyInfo(symbol).companyName;
    }

    public static class CompanyInfo {
        public final String companyName;
        public final List<String> indexKeys;
        public CompanyInfo(String companyName, List<String> indexKeys) {
            this.companyName = companyName;
            this.indexKeys   = indexKeys;
        }
    }

    private JsonNode fetchData(String indexName) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).version(HttpClient.Version.HTTP_1_1).build();
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
