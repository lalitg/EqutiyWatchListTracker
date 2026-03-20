package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class NseFetchService {

    private static final Logger log = LoggerFactory.getLogger(NseFetchService.class);

    private static final String NSE_URL =
        "https://www.nseindia.com/api/corporate-announcements?index=equities";

    private final ObjectMapper objectMapper;

    public NseFetchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all corporate announcements from NSE.
     * Returns List<NseAnnouncement> — each carrying a NewsItem + seqId separately.
     * seqId travels with the announcement for window checking but is never saved to DB.
     */
    public List<NseAnnouncement> fetchAllAnnouncements() {
        List<NseAnnouncement> result = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NSE_URL))
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
                log.error("NSE API returned status: {}", response.statusCode());
                return result;
            }

            List<Map<String, Object>> announcements = objectMapper.readValue(
                response.body(),
                new TypeReference<List<Map<String, Object>>>() {}
            );

            for (Map<String, Object> announcement : announcements) {
                String symbol = getString(announcement, "symbol");
                String anDt   = getString(announcement, "an_dt");
                String text   = getString(announcement, "attchmntText");
                String file   = getString(announcement, "attchmntFile");
                String seqStr = getString(announcement, "seq_id");

                // Skip if missing critical fields
                if (symbol == null || seqStr == null) continue;

                // Parse seqId as Long — it's a number, TreeSet needs Long not String
                Long seqId;
                try {
                    seqId = Long.parseLong(seqStr);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse seqId: {} — skipping", seqStr);
                    continue;
                }

                // Build NewsItem WITHOUT seqId — seqId lives separately
                String labelledText = (text != null) ? text + " - NSE" : null;

                NewsItem item = new NewsItem(anDt, labelledText, file);
                item.setSymbol(symbol);

                // Wrap them together in NseAnnouncement
                result.add(new NseAnnouncement(item, seqId));
            }

            log.info("NSE fetch complete — {} announcements retrieved", result.size());

        } catch (Exception e) {
            log.error("Error fetching from NSE API: {}", e.getMessage());
        }

        return result;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
