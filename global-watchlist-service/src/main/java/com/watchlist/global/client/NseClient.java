package com.watchlist.global.client;

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
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for fetching NIFTY 50 company symbols from the NSE API.
 *
 * <p>NSE blocks plain HTTP calls without a valid browser session cookie.
 * This client performs a two-step cookie handshake — first hitting the NSE home
 * page to obtain session cookies, then using those cookies to call the
 * equity-stockIndices API.
 */
@Component
public class NseClient {

    private static final Logger logger = LogManager.getLogger(NseClient.class);

    private static final String NSE_HOME = "https://www.nseindia.com";
    private static final String NIFTY50_URL = "https://www.nseindia.com/api/equity-stockIndices?index=NIFTY%2050";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetches the list of NIFTY 50 stock symbols from the NSE API.
     *
     * <p>Performs a cookie handshake before the API call to satisfy NSE's
     * browser-session requirement. The index entry itself ({@code "NIFTY 50"}) is
     * excluded from the returned list.
     *
     * @return a list of NSE stock symbols (e.g. {@code ["INFY", "TCS", ...]});
     *         returns an empty list if the fetch fails
     */
    public List<String> fetchNifty50Symbols() {
        List<String> symbols = new ArrayList<>();
        logger.info("Fetching NIFTY 50 symbols from NSE API");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(Redirect.ALWAYS)
                    .build();

            // Step 1: cookie handshake — hit NSE home to obtain session cookies
            String cookies = fetchSessionCookies(client);
            logger.debug("Session cookies obtained from NSE home");

            // Step 2: fetch NIFTY 50 index data with session cookies
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
            logger.info("Fetched {} NIFTY 50 symbols from NSE", symbols.size());

        } catch (Exception e) {
            logger.error("Failed to fetch NIFTY 50 from NSE: {} — skipping NIFTY 50 seed", e.getMessage());
        }
        return symbols;
    }

    /**
     * Hits the NSE home page to obtain session cookies required for subsequent API calls.
     *
     * @param client the {@link HttpClient} to use for the request
     * @return a semicolon-separated cookie string (e.g. {@code "nsit=abc; nseappid=xyz"})
     * @throws Exception if the HTTP request fails
     */
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

    /**
     * Makes an authenticated GET request to the given NSE API URL using session cookies.
     *
     * @param client  the {@link HttpClient} to use for the request
     * @param url     the NSE API endpoint URL
     * @param cookies the session cookie string obtained from {@link #fetchSessionCookies}
     * @return the raw JSON response body as a string
     * @throws Exception if the HTTP request fails
     */
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
