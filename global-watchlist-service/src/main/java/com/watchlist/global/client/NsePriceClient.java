package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Fetches live price data for NSE stocks via the Yahoo Finance v8 chart API.
 *
 * <p>NSE stocks on Yahoo Finance carry a ".NS" suffix — RELIANCE → RELIANCE.NS.
 * Yahoo Finance's chart endpoint does not require session cookies, crumbs, or API keys,
 * unlike NSE's own API which uses Akamai Bot Manager and blocks programmatic access.
 *
 * <p>Typical usage — call once per refresh batch; no cookie handshake required:
 * <pre>{@code
 * for (String symbol : symbols) {
 *     PriceData price = nsePriceClient.fetchPrice(symbol);
 * }
 * }</pre>
 */
@Component
public class NsePriceClient {

    private static final Logger logger = LogManager.getLogger(NsePriceClient.class);

    // ?interval=1d&range=1d requests a single daily candle — smallest payload;
    // the meta object always contains the latest real-time price regardless.
    private static final String YAHOO_CHART_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%s.NS?interval=1d&range=1d";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient   httpClient   = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns a non-empty marker string so the empty-cookie guard in
     * {@link com.watchlist.global.service.GlobalWatchlistService#refreshPrices()} passes through.
     * Yahoo Finance does not require a session cookie handshake.
     */
    public String fetchSessionCookies() {
        return "yahoo";
    }

    /**
     * Fetches live price data for an NSE stock symbol.
     * The {@code cookies} parameter is unused — kept for caller compatibility.
     */
    public PriceData fetchPrice(String symbol, String cookies) {
        return fetchPrice(symbol);
    }

    /**
     * Fetches live price data for an NSE stock from Yahoo Finance.
     *
     * @param symbol the NSE stock symbol without suffix (e.g. {@code "RELIANCE"}, {@code "M&M"})
     * @return a {@link PriceData} snapshot, or {@code null} if the fetch fails
     */
    public PriceData fetchPrice(String symbol) {
        try {
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String url = String.format(YAHOO_CHART_URL, encoded);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode meta = root.path("chart").path("result").path(0).path("meta");

            if (meta.isMissingNode()) {
                JsonNode err = root.path("chart").path("error");
                logger.warn("No data from Yahoo Finance for '{}': {}", symbol,
                            err.isMissingNode() ? "no result in response" : err.toString());
                return null;
            }

            BigDecimal currentPrice  = decimal(meta, "regularMarketPrice");
            BigDecimal previousClose = decimal(meta, "regularMarketPreviousClose");
            BigDecimal pChange       = decimal(meta, "regularMarketChangePercent");
            BigDecimal tradedVolume  = decimal(meta, "regularMarketVolume");
            BigDecimal week52High    = decimal(meta, "fiftyTwoWeekHigh");
            BigDecimal week52Low     = decimal(meta, "fiftyTwoWeekLow");
            BigDecimal changeValue   = (currentPrice != null && previousClose != null)
                                       ? currentPrice.subtract(previousClose) : null;

            logger.debug("Yahoo price for '{}': price={}, pChange={}", symbol, currentPrice, pChange);
            return new PriceData(currentPrice, week52Low, week52High, week52Low, week52High,
                                 tradedVolume, previousClose, changeValue, pChange);
        } catch (Exception e) {
            logger.error("Failed to fetch price for '{}': {}", symbol, e.getMessage());
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        try { return f.decimalValue(); } catch (Exception ignored) { return null; }
    }

    // -------------------------------------------------------------------------
    // Value object
    // -------------------------------------------------------------------------

    /** Immutable price snapshot from Yahoo Finance. */
    public static class PriceData {

        private final BigDecimal currentPrice;
        private final BigDecimal week52Low;
        private final BigDecimal week52High;
        private final BigDecimal allTimeLow;
        private final BigDecimal allTimeHigh;
        private final BigDecimal tradedVolume;
        private final BigDecimal previousClose;
        private final BigDecimal changeValue;
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
