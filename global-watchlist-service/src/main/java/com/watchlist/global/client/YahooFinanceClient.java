package com.watchlist.global.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchlist.global.model.GlobalIndexEntry;
import com.watchlist.global.model.GlobalIndexEntry.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the local Python yfinance wrapper (yfinance_wrapper.py) to get live
 * global index data. The Python wrapper handles Yahoo Finance auth internally
 * via the yfinance library — no API key or cookie management needed here.
 *
 * <p>Start the wrapper before this service:
 * <pre>  python yfinance_wrapper.py   # listens on port 5002</pre>
 */
@Component
public class YahooFinanceClient {

    private static final Logger logger = LogManager.getLogger(YahooFinanceClient.class);

    private static final Map<String, Region> REGION_MAP = Map.of(
        "US_MARKETS",        Region.US_MARKETS,
        "EUROPEAN_MARKETS",  Region.EUROPEAN_MARKETS,
        "ASIAN_MARKETS",     Region.ASIAN_MARKETS
    );

    private final HttpClient   httpClient   = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String       baseUrl;

    public YahooFinanceClient(
            @Value("${yfinance.wrapper.base-url:http://localhost:5002}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<GlobalIndexEntry> fetchAll() {
        List<GlobalIndexEntry> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/global-indices"))
                    .header("Accept", "application/json")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<Map<String, Object>> rows = objectMapper.readValue(
                response.body(), new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> row : rows) {
                String  sym    = str(row, "symbol");
                String  name   = str(row, "name");
                String  flag   = str(row, "flagEmoji");
                Region  region = REGION_MAP.getOrDefault(str(row, "region"), Region.US_MARKETS);

                GlobalIndexEntry entry = new GlobalIndexEntry(sym, name, flag, region);
                entry.setLtp(decimal(row, "ltp"));
                entry.setChange(decimal(row, "change"));
                entry.setChangePercent(decimal(row, "changePercent"));
                entry.setLastUpdated(LocalDateTime.now());
                result.add(entry);
            }
            logger.info("yfinance wrapper: fetched {} global indices", result.size());
        } catch (Exception e) {
            logger.error("Failed to fetch global indices from yfinance wrapper: {}", e.getMessage());
        }
        return result;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private BigDecimal decimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
