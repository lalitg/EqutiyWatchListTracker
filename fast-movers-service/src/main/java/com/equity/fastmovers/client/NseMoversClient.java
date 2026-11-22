package com.equity.fastmovers.client;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NSE HTTP client for the fast-movers-service.
 *
 * <p>Handles the NSE browser-session cookie handshake and provides:
 * <ul>
 *   <li>Top gainers from the NSE live-analysis-variations API</li>
 *   <li>Top losers from the NSE live-analysis-variations API</li>
 *   <li>Historical OHLC for a symbol (used for daily close price backfill)</li>
 * </ul>
 */
@Component
public class NseMoversClient {

    private static final Logger logger = LogManager.getLogger(NseMoversClient.class);

    private static final String NSE_HOME      = "https://www.nseindia.com";
    private static final String GAINERS_URL   = "https://www.nseindia.com/api/live-analysis-variations?index=gainers";
    private static final String LOSERS_URL    = "https://www.nseindia.com/api/live-analysis-variations?index=loosers";
    private static final String HISTORY_URL   = "https://www.nseindia.com/api/historical/cm/equity?symbol=%s&series=[%%22EQ%%22]&from=%s&to=%s";
    // New NSE archives URL — date format: DDMMYYYY (e.g., 22042026)
    private static final String BHAVCOPY_URL  = "https://nsearchives.nseindia.com/products/content/sec_bhavdata_full_%s%s%s.csv";
    private static final DateTimeFormatter NSE_DATE_FMT      = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter BHAVCOPY_YEAR_FMT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter BHAVCOPY_MON_FMT  = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter BHAVCOPY_DAY_FMT  = DateTimeFormatter.ofPattern("dd");

    private final HttpClient   httpClient  = HttpClient.newBuilder()
            .followRedirects(Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Session cookies
    // -------------------------------------------------------------------------

    /**
     * Performs the NSE cookie handshake and returns the session cookie string.
     * Call once before a batch of API calls, reuse the string for the whole batch.
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

    // -------------------------------------------------------------------------
    // Gainers / Losers
    // -------------------------------------------------------------------------

    /** Fetches top 10 gainers from the NSE live API. */
    public List<MoverData> fetchGainers(String cookies) {
        return fetchMovers(GAINERS_URL, cookies, "gainers");
    }

    /** Fetches top 10 losers from the NSE live API. */
    public List<MoverData> fetchLosers(String cookies) {
        return fetchMovers(LOSERS_URL, cookies, "losers");
    }

    private List<MoverData> fetchMovers(String url, String cookies, String label) {
        List<MoverData> result = new ArrayList<>();
        try {
            String json = get(url, cookies);
            JsonNode root = objectMapper.readTree(json);
            // NSE response: { "allSec": { "data": [ { "symbol", "ltp", "net_price" }, ... ] }, ... }
            JsonNode data = root.path("allSec").path("data");
            if (data.isMissingNode() || !data.isArray()) {
                logger.warn("Unexpected {} response — no 'allSec.data' array", label);
                return result;
            }
            int rank = 1;
            for (JsonNode item : data) {
                if (rank > 10) break;
                String symbol      = item.has("symbol")    ? item.get("symbol").asText()          : null;
                BigDecimal ltp     = item.has("ltp")       ? item.get("ltp").decimalValue()       : null;
                BigDecimal pChange = item.has("net_price") ? item.get("net_price").decimalValue() : null;

                if (symbol == null || symbol.isBlank()) continue;
                result.add(new MoverData(rank++, symbol, null, ltp, pChange));
            }
            logger.debug("Fetched {} {} from NSE", result.size(), label);
        } catch (Exception e) {
            logger.error("Failed to fetch {} from NSE: {}", label, e.getMessage());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Historical close price
    // -------------------------------------------------------------------------

    /**
     * Fetches historical OHLC data for a symbol between fromDate and toDate (inclusive).
     * Returns a list of daily close prices.
     *
     * @param symbol  NSE symbol (e.g. "INFY")
     * @param from    start date
     * @param to      end date
     * @param cookies session cookie string
     */
    public List<HistoricalClose> fetchHistoricalClose(String symbol, LocalDate from, LocalDate to, String cookies) {
        List<HistoricalClose> result = new ArrayList<>();
        try {
            String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String url = String.format(HISTORY_URL,
                    encodedSymbol,
                    from.format(NSE_DATE_FMT),
                    to.format(NSE_DATE_FMT));

            String json = get(url, cookies);
            JsonNode root = objectMapper.readTree(json);
            // NSE response: { "data": [ { "CH_SYMBOL", "CH_TIMESTAMP", "CH_CLOSING_PRICE" }, ... ] }
            JsonNode data = root.has("data") ? root.get("data") : null;
            if (data == null || !data.isArray()) {
                logger.debug("No historical data for '{}' between {} and {}", symbol, from, to);
                return result;
            }
            for (JsonNode row : data) {
                String dateStr   = row.has("CH_TIMESTAMP")     ? row.get("CH_TIMESTAMP").asText()     : null;
                BigDecimal close = row.has("CH_CLOSING_PRICE") ? row.get("CH_CLOSING_PRICE").decimalValue() : null;
                if (dateStr == null || close == null) continue;
                try {
                    LocalDate date = LocalDate.parse(dateStr); // format: "2026-04-10"
                    result.add(new HistoricalClose(date, close));
                } catch (Exception parseEx) {
                    logger.debug("Skipping unparseable date '{}' for symbol '{}'", dateStr, symbol);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch historical data for '{}': {}", symbol, e.getMessage());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Bhavcopy (bulk daily close prices)
    // -------------------------------------------------------------------------

    /**
     * Downloads NSE's Bhavcopy CSV for the given trading date and returns a map of
     * symbol → closing price for all EQ-series stocks.
     * One HTTP request replaces 2000+ per-symbol historical API calls.
     */
    public Map<String, BigDecimal> fetchBhavcopy(LocalDate date) {
        Map<String, BigDecimal> result = new HashMap<>();
        String day   = date.format(BHAVCOPY_DAY_FMT);
        String month = date.format(BHAVCOPY_MON_FMT);
        String year  = date.format(BHAVCOPY_YEAR_FMT);
        // URL format: sec_bhavdata_full_DDMMYYYY.csv
        String url   = String.format(BHAVCOPY_URL, day, month, year);
        logger.info("Downloading Bhavcopy from: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Bhavcopy not available for {} — HTTP {}", date, response.statusCode());
                return result;
            }
            // CSV columns: SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, ...
            String[] lines = response.body().split("\n");
            boolean header = true;
            for (String line : lines) {
                if (header) { header = false; continue; }
                String[] cols = line.split(",");
                if (cols.length < 9) continue;
                String symbol = cols[0].trim();
                String series = cols[1].trim();
                if (!"EQ".equals(series)) continue;
                try {
                    result.put(symbol, new BigDecimal(cols[8].trim()));
                } catch (NumberFormatException ignored) { }
            }
            logger.info("Bhavcopy parsed: {} EQ symbols for {}", result.size(), date);
        } catch (Exception e) {
            logger.error("Failed to fetch Bhavcopy for {}: {}", date, e.getMessage());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // HTTP helper
    // -------------------------------------------------------------------------

    private String get(String url, String cookies) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", NSE_HOME)
                .header("Cookie", cookies)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    // -------------------------------------------------------------------------
    // Value objects
    // -------------------------------------------------------------------------

    public static class MoverData {
        private final int rank;
        private final String symbol;
        private final String companyName;
        private final BigDecimal currentPrice;
        private final BigDecimal pctChange;

        public MoverData(int rank, String symbol, String companyName,
                         BigDecimal currentPrice, BigDecimal pctChange) {
            this.rank         = rank;
            this.symbol       = symbol;
            this.companyName  = companyName;
            this.currentPrice = currentPrice;
            this.pctChange    = pctChange;
        }

        public int getRank()               { return rank; }
        public String getSymbol()          { return symbol; }
        public String getCompanyName()     { return companyName; }
        public BigDecimal getCurrentPrice(){ return currentPrice; }
        public BigDecimal getPctChange()   { return pctChange; }
    }

    public static class HistoricalClose {
        private final LocalDate date;
        private final BigDecimal closePrice;

        public HistoricalClose(LocalDate date, BigDecimal closePrice) {
            this.date       = date;
            this.closePrice = closePrice;
        }

        public LocalDate getDate()           { return date; }
        public BigDecimal getClosePrice()    { return closePrice; }
    }
}
