package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Primary;
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
 * Yahoo Finance-backed override of {@link NsePriceClient}.
 *
 * <p>NSE's quote-equity API is protected by Akamai Bot Manager, which requires
 * JavaScript execution to set valid session tokens — Java's HttpClient cannot
 * satisfy that challenge regardless of headers sent.
 *
 * <p>This {@code @Primary} bean replaces {@link NsePriceClient} in the
 * global-watchlist-service ApplicationContext and fetches prices from Yahoo
 * Finance instead. NSE stocks on Yahoo Finance carry a ".NS" suffix
 * (RELIANCE → RELIANCE.NS, M&M → M%26M.NS after URL-encoding).
 *
 * <p>Placed in {@code com.watchlist.global.client} so
 * {@code GlobalWatchlistApplication}'s component scan picks it up.
 */
@Component
@Primary
public class YahooNsePriceClient extends NsePriceClient {

    private static final Logger logger = LogManager.getLogger(YahooNsePriceClient.class);

    // ?interval=1d&range=1d → smallest payload; meta always holds latest real-time price
    private static final String YAHOO_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%s.NS?interval=1d&range=1d";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient   httpClient   = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns a non-empty marker so the empty-cookie guard in
     * {@code GlobalWatchlistService.refreshPrices()} lets the batch proceed.
     * Yahoo Finance does not require a session cookie handshake.
     */
    @Override
    public String fetchSessionCookies() {
        return "yahoo";
    }

    /**
     * Fetches live price data from Yahoo Finance.
     * The {@code cookies} parameter is ignored — present only for interface compatibility.
     */
    @Override
    public PriceData fetchPrice(String symbol, String cookies) {
        try {
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String url = String.format(YAHOO_URL, encoded);

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
                logger.warn("No Yahoo Finance data for '{}': {}", symbol,
                            err.isMissingNode() ? "no result" : err.toString());
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
            logger.error("Failed to fetch price for '{}' from Yahoo Finance: {}", symbol, e.getMessage());
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        try { return f.decimalValue(); } catch (Exception ignored) { return null; }
    }
}
