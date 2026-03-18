package com.nseevents.nse_events_scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nseevents.nse_events_scheduler.dto.EventItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single responsibility — calls NSE Event Calendar API and parses the response.
 * Knows nothing about saving, scheduling, or deduplication.
 *
 * NSE API: https://www.nseindia.com/api/event-calendar?index=equities
 * Returns both upcoming and past events for all NSE-listed companies.
 */
@Service
public class NseEventFetchService {

    private static final Logger log = LoggerFactory.getLogger(NseEventFetchService.class);

    private static final String NSE_EVENTS_URL =
        "https://www.nseindia.com/api/event-calendar?index=equities";

    private final ObjectMapper objectMapper;

    public NseEventFetchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all events from NSE Event Calendar API.
     * NSE returns both upcoming and past board meeting events.
     * We extract: symbol, bm_desc (as event), purpose, date.
     *
     * @return List of EventItem — each carrying symbol, event, purpose, date
     */
    public List<EventItem> fetchAllEvents() {
        List<EventItem> result = new ArrayList<>();

        try {
            // Build HTTP request with browser headers
            // WHY browser headers: NSE blocks requests that don't look like a real browser
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NSE_EVENTS_URL))
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
                client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("NSE Events API returned status: {}", response.statusCode());
                return result;
            }

            // Parse JSON array response into List of Maps
            // Each Map = one event with all NSE fields
            List<Map<String, Object>> events = objectMapper.readValue(
                response.body(),
                new TypeReference<List<Map<String, Object>>>() {}
            );

            for (Map<String, Object> event : events) {
                String symbol  = getString(event, "symbol");
                String bmDesc  = getString(event, "bm_desc");   // event description
                String purpose = getString(event, "purpose");    // event category
                String date    = getString(event, "date");       // event date

                // Skip events missing critical fields
                if (symbol == null || date == null) continue;

                EventItem item = new EventItem(date, bmDesc, purpose);
                item.setSymbol(symbol);
                result.add(item);
            }

            log.info("NSE Events fetch complete — {} events retrieved", result.size());

        } catch (Exception e) {
            log.error("Error fetching from NSE Events API: {}", e.getMessage());
        }

        return result;
    }

    // Safely extracts a String from a Map — returns null if missing
    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}

