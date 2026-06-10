package com.equity.watchlist.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class NseHolidayClient {

    private static final Logger logger = LogManager.getLogger(NseHolidayClient.class);

    private static final String NSE_HOME    = "https://www.nseindia.com";
    private static final String HOLIDAY_URL = "https://www.nseindia.com/api/holiday-master?type=trading";
    private static final DateTimeFormatter NSE_DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record NseHoliday(LocalDate date, String name) {}

    public List<NseHoliday> fetchHolidays() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(Redirect.ALWAYS)
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            String cookies = fetchSessionCookies(client);
            String json = fetchWithCookies(client, HOLIDAY_URL, cookies);
            JsonNode root = objectMapper.readTree(json);
            JsonNode cm = root.path("CM");
            if (!cm.isArray()) {
                logger.warn("NSE holiday-master response missing 'CM' array");
                return Collections.emptyList();
            }
            List<NseHoliday> result = new ArrayList<>();
            for (JsonNode item : cm) {
                String tradingDate = item.path("tradingDate").asText(null);
                String description = item.path("description").asText("Holiday");
                if (tradingDate == null) continue;
                try {
                    LocalDate date = LocalDate.parse(tradingDate.trim(), NSE_DATE_FMT);
                    result.add(new NseHoliday(date, description.trim()));
                } catch (Exception e) {
                    logger.warn("Could not parse date '{}': {}", tradingDate, e.getMessage());
                }
            }
            logger.info("Fetched {} NSE holidays from API", result.size());
            return result;
        } catch (Exception e) {
            logger.error("Failed to fetch NSE holidays: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String fetchSessionCookies(HttpClient client) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NSE_HOME))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.headers().allValues("set-cookie").stream()
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
}
