package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchlist.global.model.IndexSummary;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Yahoo Finance-backed override of {@link NseClient} for index headers and NIFTY 50 symbols.
 *
 * <p>NSE's equity-stockIndices API is protected by Akamai Bot Manager (same as quote-equity).
 * This {@code @Primary} bean replaces {@link NseClient} in the global-watchlist-service context:
 * <ul>
 *   <li>{@link #fetchNifty50Symbols()} — returns a hardcoded list; avoids the blocked endpoint.</li>
 *   <li>{@link #fetchIndexHeader(String)} — queries Yahoo Finance for major NSE indices.</li>
 *   <li>All other methods ({@code fetchIndexCompanies}, {@code fetchCompanyInfo}) delegate to
 *       the parent implementation and fail gracefully when NSE blocks them.</li>
 * </ul>
 *
 * <p>Placed in {@code com.watchlist.global.client} so {@code GlobalWatchlistApplication}'s
 * component scan picks it up alongside the original {@code NseClient}.
 */
@Component
@Primary
public class YahooNseClient extends NseClient {

    private static final Logger logger = LogManager.getLogger(YahooNseClient.class);

    // Maps NSE index names to Yahoo Finance tickers. Indices absent from this map return null.
    private static final Map<String, String> NSE_TO_YAHOO = Map.ofEntries(
        Map.entry("NIFTY 50",                 "^NSEI"),
        Map.entry("NIFTY 100",                "^CNX100"),
        Map.entry("NIFTY 200",                "^CNX200"),
        Map.entry("NIFTY 500",                "^CRSLDX"),
        Map.entry("NIFTY MIDCAP 100",         "^NSEMDCP50"),
        Map.entry("NIFTY SMLCAP 100",         "^CNXSC"),
        Map.entry("NIFTY BANK",               "^NSEBANK"),
        Map.entry("NIFTY IT",                 "^CNXIT"),
        Map.entry("NIFTY AUTO",               "^CNXAUTO"),
        Map.entry("NIFTY PHARMA",             "^CNXPHARMA"),
        Map.entry("NIFTY FMCG",               "^CNXFMCG"),
        Map.entry("NIFTY FINANCIAL SERVICES", "^CNXFIN"),
        Map.entry("NIFTY METAL",              "^CNXMETAL"),
        Map.entry("NIFTY REALTY",             "^CNXREALTY"),
        Map.entry("NIFTY ENERGY",             "^CNXENERGY"),
        Map.entry("NIFTY MEDIA",              "^CNXMEDIA")
    );

    // Hardcoded NIFTY 50 composition (May 2025).
    // Update this list when NSE announces a rebalancing.
    private static final List<String> NIFTY_50_SYMBOLS = List.of(
        "ADANIENT",   "ADANIPORTS", "APOLLOHOSP", "ASIANPAINT",  "AXISBANK",
        "BAJAJ-AUTO", "BAJAJFINSV", "BAJFINANCE", "BEL",         "BHARTIARTL",
        "BPCL",       "BRITANNIA",  "CIPLA",      "COALINDIA",   "DRREDDY",
        "EICHERMOT",  "ETERNAL",    "GRASIM",     "HCLTECH",     "HDFCBANK",
        "HDFCLIFE",   "HEROMOTOCO", "HINDALCO",   "HINDUNILVR",  "ICICIBANK",
        "INDUSINDBK", "INFY",       "ITC",        "JIOFIN",      "JSWSTEEL",
        "KOTAKBANK",  "LT",         "M&M",        "MARUTI",      "NESTLEIND",
        "NTPC",       "ONGC",       "POWERGRID",  "RELIANCE",    "SBILIFE",
        "SBIN",       "SHRIRAMFIN", "SUNPHARMA",  "TATACONSUM",  "TATAMOTORS",
        "TATASTEEL",  "TCS",        "TECHM",      "TITAN",       "WIPRO"
    );

    private static final String YAHOO_CHART_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient   httpClient   = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns the hardcoded NIFTY 50 symbol list instead of calling the NSE endpoint.
     * NSE's equity-stockIndices API is blocked by Akamai Bot Manager.
     */
    @Override
    public List<String> fetchNifty50Symbols() {
        logger.info("Using hardcoded NIFTY 50 symbol list ({} symbols)", NIFTY_50_SYMBOLS.size());
        return NIFTY_50_SYMBOLS;
    }

    /**
     * Fetches the index-level price summary from Yahoo Finance.
     * Returns {@code null} for indices with no Yahoo Finance equivalent
     * (e.g. NIFTY PRIVATE BANK, NIFTY LARGEMIDCAP 250).
     */
    @Override
    public IndexSummary fetchIndexHeader(String indexName) {
        String yahooTicker = NSE_TO_YAHOO.get(indexName);
        if (yahooTicker == null) {
            logger.debug("No Yahoo Finance ticker mapped for '{}' — skipping", indexName);
            return null;
        }
        try {
            String encoded = URLEncoder.encode(yahooTicker, StandardCharsets.UTF_8);
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
                logger.warn("No Yahoo Finance data for index '{}' ({})", indexName, yahooTicker);
                return null;
            }

            BigDecimal ltp     = decimal(meta, "regularMarketPrice");
            BigDecimal prev    = decimal(meta, "regularMarketPreviousClose");
            BigDecimal pChange = decimal(meta, "regularMarketChangePercent");
            BigDecimal change  = (ltp != null && prev != null) ? ltp.subtract(prev) : null;

            IndexSummary summary = new IndexSummary(indexName, indexName, true);
            summary.setLtp(ltp);
            summary.setChange(change);
            summary.setChangePercent(pChange);
            summary.setLastUpdated(LocalDateTime.now());
            logger.debug("Yahoo index '{}': ltp={}, pChange={}", indexName, ltp, pChange);
            return summary;
        } catch (Exception e) {
            logger.error("Failed to fetch Yahoo index header for '{}': {}", indexName, e.getMessage());
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        try { return f.decimalValue(); } catch (Exception ignored) { return null; }
    }
}
