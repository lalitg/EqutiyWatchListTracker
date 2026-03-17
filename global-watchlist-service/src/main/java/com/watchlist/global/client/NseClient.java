package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches NIFTY 50 company symbols from NSE API.
 * Uses Java HttpClient with cookie handshake — same pattern as NseSectorSyncService.
 * NSE blocks RestTemplate/plain HTTP calls without a valid browser session cookie.
 */
@Component
public class NseClient {

    private static final String NSE_HOME = "https://www.nseindia.com";
    private static final String NIFTY50_URL = "https://www.nseindia.com/api/equity-stockIndices?index=NIFTY%2050";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> fetchNifty50Symbols() {
        List<String> symbols = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(Redirect.ALWAYS)
                    .build();

            // Step 1: cookie handshake — hit NSE home to get session cookies
            String cookies = fetchSessionCookies(client);

            // Step 2: fetch NIFTY 50 index data
            String json = fetchWithCookies(client, NIFTY50_URL, cookies);
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");

            if (data != null) {
                for (JsonNode item : data) {
                    String symbol = item.get("symbol").asText();
                    // Skip the index row itself
                    if (!symbol.equalsIgnoreCase("NIFTY 50")) {
                        symbols.add(symbol);
                    }
                }
            }
            System.out.println("Fetched " + symbols.size() + " NIFTY 50 symbols from NSE");

        } catch (Exception e) {
            System.out.println("Failed to fetch NIFTY 50 from NSE: " + e.getMessage() + " — skipping NIFTY 50 seed");
        }
        return symbols;
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

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
