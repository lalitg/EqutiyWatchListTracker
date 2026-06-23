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
 * Fetches NSE event data from three public NSE APIs and merges the results.
 *
 * APIs:
 *   1. /api/event-calendar          — corporate event calendar
 *   2. /api/corporate-board-meetings — board meeting announcements
 *   3. /api/corporates-corporateActions — dividends, splits, buybacks, etc.
 *
 * NSE blocks non-browser clients, so all requests include browser-like headers.
 * Each API may return either a JSON array at the root or a {"data": [...]} wrapper;
 * fetchFromUrl() handles both formats transparently.
 */
@Service
public class NseEventFetchService {

    private static final Logger log = LoggerFactory.getLogger(NseEventFetchService.class);

    private static final String EVENT_CALENDAR_URL =
        "https://www.nseindia.com/api/event-calendar?index=equities";
    private static final String BOARD_MEETINGS_URL =
        "https://www.nseindia.com/api/corporate-board-meetings?index=equities";
    private static final String CORP_ACTIONS_URL =
        "https://www.nseindia.com/api/corporates-corporateActions?index=equities";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${events.nse.timeout.seconds:10}")
    private int timeoutSeconds;

    public NseEventFetchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Fetches events from all three NSE APIs and returns a merged flat list.
     * Deduplication across sources is handled in EventWorkerService before persisting.
     */
    public List<EventItem> fetchAllEvents() {
        List<EventItem> all = new ArrayList<>();
        all.addAll(fetchFromUrl(EVENT_CALENDAR_URL, "event-calendar"));
        all.addAll(fetchFromUrl(BOARD_MEETINGS_URL, "board-meetings"));
        all.addAll(fetchFromUrl(CORP_ACTIONS_URL, "corp-actions"));
        log.info("Total events fetched from all 3 sources: {}", all.size());
        return all;
    }

    /**
     * Fetches and parses events from a single NSE API URL.
     *
     * Handles two response shapes:
     *   - Direct JSON array:    [{...}, {...}]
     *   - Wrapped JSON object:  {"data": [{...}, {...}]}
     *
     * Field name fallbacks per attribute (NSE field names differ across endpoints):
     *   symbol  — "symbol", "bm_symbol", "sm_symbol"
     *   date    — "date", "bm_date", "sm_date", "exDate"
     *   event   — "bm_desc", "subject", "description", "desc"
     *   purpose — "purpose", "bm_purpose", "sm_purpose"
     */
    private List<EventItem> fetchFromUrl(String url, String sourceName) {
        List<EventItem> result = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
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
                log.error("[{}] NSE API returned HTTP {}", sourceName, response.statusCode());
                return result;
            }

            List<Map<String, Object>> rawRows = parseResponse(response.body(), sourceName);

            for (Map<String, Object> row : rawRows) {
                String symbol  = firstNonNull(row, "symbol", "bm_symbol", "sm_symbol");
                String date    = firstNonNull(row, "date", "bm_date", "sm_date", "exDate");
                String event   = firstNonNull(row, "bm_desc", "subject", "description", "desc");
                String purpose = firstNonNull(row, "purpose", "bm_purpose", "sm_purpose");

                if (symbol == null || date == null) continue;

                EventItem item = new EventItem(date, event, purpose);
                item.setSymbol(symbol);
                item.setSource(sourceName);
                result.add(item);
            }

            log.info("[{}] {} events parsed", sourceName, result.size());

        } catch (Exception e) {
            log.error("[{}] fetch failed: {}", sourceName, e.getMessage());
        }

        return result;
    }

    /**
     * Parses the raw JSON body into a list of row maps.
     * Tries direct-array format first; falls back to {"data": [...]} wrapper.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResponse(String body, String sourceName) {
        // Try direct array first (event-calendar format)
        try {
            return objectMapper.readValue(body,
                new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignored) {}

        // Try {"data": [...]} wrapper (corp-actions / board-meetings format)
        try {
            Map<String, Object> wrapper = objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() {});
            Object data = wrapper.get("data");
            if (data instanceof List) {
                return objectMapper.convertValue(data,
                    new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception ex) {
            log.error("[{}] could not parse response: {}", sourceName, ex.getMessage());
        }

        return List.of();
    }

    /**
     * Returns the string value of the first key found in the map, or null.
     * Skips blank strings — treats empty string the same as absent.
     */
    private String firstNonNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isBlank()) return val.toString().trim();
        }
        return null;
    }
}
