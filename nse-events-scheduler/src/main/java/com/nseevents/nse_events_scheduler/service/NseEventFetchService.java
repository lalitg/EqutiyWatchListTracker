package com.nseevents.nse_events_scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nseevents.nse_events_scheduler.dto.EventItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches NSE Event Calendar data from the NSE public API.
 * Single responsibility: HTTP call and JSON parsing only.
 *
 * NSE API: https://www.nseindia.com/api/event-calendar?index=equities
 */
@Service
public class NseEventFetchService {

    private static final Logger log = LoggerFactory.getLogger(NseEventFetchService.class);

    private static final String NSE_EVENTS_URL =
        "https://www.nseindia.com/api/event-calendar?index=equities";

    /** Shared HTTP client — created once, reused across all requests. */
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${events.nse.timeout.seconds:10}")
    private int timeoutSeconds;

    public NseEventFetchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Single instance — HttpClient is thread-safe and designed for reuse.
        // Creating one per request is wasteful and skips connection pooling.
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Fetches all events from the NSE Event Calendar API.
     * Extracts: symbol, bm_desc (as event), purpose, date.
     *
     * <p>NSE blocks non-browser clients — browser headers are required.</p>
     *
     * @return list of {@link EventItem}; empty list on error or empty API response
     */
    public List<EventItem> fetchAllEvents() {
        List<EventItem> result = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NSE_EVENTS_URL))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                // NSE blocks requests that don't include browser-like headers
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.nseindia.com")
                .GET()
                .build();

            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("NSE Events API returned HTTP {}", response.statusCode());
                return result;
            }

            List<Map<String, Object>> events = objectMapper.readValue(
                response.body(),
                new TypeReference<List<Map<String, Object>>>() {}
            );

            for (Map<String, Object> event : events) {
                String symbol  = getString(event, "symbol");
                String bmDesc  = getString(event, "bm_desc");
                String purpose = getString(event, "purpose");
                String date    = getString(event, "date");

                if (symbol == null || date == null) continue;

                EventItem item = new EventItem(date, bmDesc, purpose);
                item.setSymbol(symbol);
                result.add(item);
            }

            log.info("NSE Events fetch complete — {} events retrieved", result.size());

        } catch (Exception e) {
            log.error("NSE Events API fetch failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Safely extracts a String value from the given map.
     *
     * @param map source map from the NSE API response
     * @param key field name to extract
     * @return string value, or {@code null} if the key is absent or value is null
     */
    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
