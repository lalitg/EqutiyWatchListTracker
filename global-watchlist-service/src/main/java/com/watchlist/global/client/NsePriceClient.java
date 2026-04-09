package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HTTP client for fetching live price data for individual NSE stocks.
 *
 * <p>Calls the NSE {@code quote-equity} API for each symbol to retrieve current
 * price, 52-week range, and traded volume. NSE requires a valid browser session
 * cookie — callers must first obtain a cookie via {@link #fetchSessionCookies()}
 * and pass it to {@link #fetchPrice(String, String)} for the duration of a batch.
 *
 * <p>The underlying {@link HttpClient} is shared across all calls — it is
 * thread-safe and intended to be reused, not recreated per request.
 *
 * <p>Typical usage in a price-refresh batch:
 * <pre>{@code
 * String cookies = nsePriceClient.fetchSessionCookies();
 * for (String symbol : symbols) {
 *     PriceData price = nsePriceClient.fetchPrice(symbol, cookies);
 * }
 * }</pre>
 */
@Component
public class NsePriceClient {

    private static final Logger logger = LogManager.getLogger(NsePriceClient.class);

    private static final String NSE_HOME       = "https://www.nseindia.com";
    private static final String QUOTE_URL      = "https://www.nseindia.com/api/quote-equity?symbol=";
    private static final String TRADE_INFO_URL = "https://www.nseindia.com/api/quote-equity?symbol=%s&section=trade_info";

    /** Shared, thread-safe HTTP client — reused across all NSE API calls. */
    private final HttpClient    httpClient     = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
    private final ObjectMapper  objectMapper   = new ObjectMapper();

    /**
     * Performs a cookie handshake with the NSE home page and returns the session
     * cookie string required for subsequent API calls.
     *
     * <p>Call this once before a price-refresh batch, then reuse the returned
     * cookie string for all {@link #fetchPrice} calls in that batch.
     *
     * @return a semicolon-separated cookie string (e.g. {@code "nsit=abc; nseappid=xyz"}),
     *         or an empty string if the handshake fails
     */
    public String fetchSessionCookies() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NSE_HOME))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String cookies = response.headers().allValues("set-cookie")
                    .stream()
                    .map(c -> c.split(";")[0])
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);

            logger.debug("NSE session cookies obtained");
            return cookies;
        } catch (Exception e) {
            logger.error("Failed to obtain NSE session cookies: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Fetches live price data for a single NSE stock symbol.
     *
     * <p>Makes two NSE API calls:
     * <ol>
     *   <li>{@code quote-equity?symbol=X} — current price, 52-week high/low</li>
     *   <li>{@code quote-equity?symbol=X&section=trade_info} — traded volume</li>
     * </ol>
     *
     * @param symbol  the NSE stock symbol (e.g. {@code "INFY"}), must be upper-cased
     * @param cookies the session cookie string from {@link #fetchSessionCookies()}
     * @return a {@link PriceData} object with the fetched values,
     *         or {@code null} if either API call fails
     */
    public PriceData fetchPrice(String symbol, String cookies) {
        try {
            // Call 1: current price and 52-week range
            String quoteJson = get(QUOTE_URL + symbol, cookies);
            JsonNode quoteBody = objectMapper.readTree(quoteJson);

            BigDecimal currentPrice = null;
            BigDecimal week52High   = null;
            BigDecimal week52Low    = null;

            BigDecimal previousClose = null;
            BigDecimal changeValue   = null;
            BigDecimal pChange       = null;

            if (quoteBody != null && quoteBody.has("priceInfo")) {
                JsonNode priceInfo = quoteBody.get("priceInfo");
                if (priceInfo.has("lastPrice"))
                    currentPrice = priceInfo.get("lastPrice").decimalValue();
                if (priceInfo.has("previousPrice"))
                    previousClose = priceInfo.get("previousPrice").decimalValue();
                if (priceInfo.has("change"))
                    changeValue = priceInfo.get("change").decimalValue();
                if (priceInfo.has("pChange"))
                    pChange = priceInfo.get("pChange").decimalValue();
                if (priceInfo.has("weekHighLow")) {
                    JsonNode whl = priceInfo.get("weekHighLow");
                    if (whl.has("max")) week52High = whl.get("max").decimalValue();
                    if (whl.has("min")) week52Low  = whl.get("min").decimalValue();
                }
            }

            // Call 2: traded volume
            BigDecimal tradedVolume = null;
            String tradeJson = get(String.format(TRADE_INFO_URL, symbol), cookies);
            JsonNode tradeBody = objectMapper.readTree(tradeJson);

            if (tradeBody != null && tradeBody.has("marketDeptOrderBook")) {
                JsonNode tradeInfo = tradeBody.get("marketDeptOrderBook").get("tradeInfo");
                if (tradeInfo != null && tradeInfo.has("totalTradedVolume"))
                    tradedVolume = tradeInfo.get("totalTradedVolume").decimalValue();
            }

            logger.debug("Fetched price for '{}': currentPrice={}, pChange={}", symbol, currentPrice, pChange);
            return new PriceData(currentPrice, week52Low, week52High, week52Low, week52High, tradedVolume,
                                 previousClose, changeValue, pChange);

        } catch (Exception e) {
            logger.error("Failed to fetch price for '{}': {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Makes an authenticated GET request to an NSE API endpoint using the shared {@link HttpClient}.
     *
     * @param url     the NSE API endpoint URL
     * @param cookies the session cookie string obtained from {@link #fetchSessionCookies()}
     * @return the raw JSON response body
     * @throws Exception if the HTTP request fails
     */
    private String get(String url, String cookies) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", NSE_HOME)
                .header("Cookie", cookies)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    // -------------------------------------------------------------------------
    // Value object
    // -------------------------------------------------------------------------

    /**
     * Immutable value object holding the price snapshot returned by the NSE API.
     */
    public static class PriceData {

        /** Latest traded price. */
        private final BigDecimal currentPrice;

        /** 52-week low price. */
        private final BigDecimal week52Low;

        /** 52-week high price. */
        private final BigDecimal week52High;

        /** All-time low price (currently set equal to 52-week low). */
        private final BigDecimal allTimeLow;

        /** All-time high price (currently set equal to 52-week high). */
        private final BigDecimal allTimeHigh;

        /** Total traded volume for the latest session. */
        private final BigDecimal tradedVolume;

        /** Previous closing price. */
        private final BigDecimal previousClose;

        /** Absolute change from previous close. */
        private final BigDecimal changeValue;

        /** Percentage change from previous close. */
        private final BigDecimal pChange;

        public PriceData(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High,
                         BigDecimal allTimeLow, BigDecimal allTimeHigh, BigDecimal tradedVolume,
                         BigDecimal previousClose, BigDecimal changeValue, BigDecimal pChange) {
            this.currentPrice  = currentPrice;
            this.week52Low     = week52Low;
            this.week52High    = week52High;
            this.allTimeLow    = allTimeLow;
            this.allTimeHigh   = allTimeHigh;
            this.tradedVolume  = tradedVolume;
            this.previousClose = previousClose;
            this.changeValue   = changeValue;
            this.pChange       = pChange;
        }

        public BigDecimal getCurrentPrice()  { return currentPrice; }
        public BigDecimal getWeek52Low()     { return week52Low; }
        public BigDecimal getWeek52High()    { return week52High; }
        public BigDecimal getAllTimeLow()     { return allTimeLow; }
        public BigDecimal getAllTimeHigh()    { return allTimeHigh; }
        public BigDecimal getTradedVolume()  { return tradedVolume; }
        public BigDecimal getPreviousClose() { return previousClose; }
        public BigDecimal getChangeValue()   { return changeValue; }
        public BigDecimal getPChange()       { return pChange; }
    }
}
