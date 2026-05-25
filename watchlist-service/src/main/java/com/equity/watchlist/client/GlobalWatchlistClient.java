package com.equity.watchlist.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST client for the global-watchlist-service.
 *
 * <p>Used during add-company and CSV import flows to check whether a company
 * is already tracked globally and to retrieve its live NSE price data.
 * If a company is not yet in the global watchlist, this client triggers its
 * addition, which causes the global-watchlist-service to fetch a live price
 * from NSE before returning.
 */
@Component
public class GlobalWatchlistClient {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistClient.class);

    /** Base URL of the global-watchlist-service (default: {@code http://localhost:8083}). */
    @Value("${global.watchlist.service.url:http://localhost:8083}")
    private String globalWatchlistServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Fetches a single company entry from the global watchlist.
     *
     * @param companyCode the NSE symbol to look up (e.g. {@code "INFY"})
     * @return the {@link GlobalWatchlistEntry} with live price data,
     *         or {@code null} if the company is not found (404) or the call fails
     */
    public GlobalWatchlistEntry getEntry(String companyCode) {
        String url = globalWatchlistServiceUrl + "/api/global-watchlist/" + companyCode;
        logger.debug("Fetching global watchlist entry for '{}' from {}", companyCode, url);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, null, JsonNode.class);
            return toEntry(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            logger.debug("Company '{}' not found in global watchlist", companyCode);
            return null;
        } catch (Exception e) {
            logger.error("Failed to fetch global watchlist entry for '{}': {}", companyCode, e.getMessage());
            return null;
        }
    }

    /**
     * Adds a company to the global watchlist, triggering a live NSE price fetch.
     *
     * @param companyCode the NSE symbol to add (e.g. {@code "INFY"})
     * @return the newly created {@link GlobalWatchlistEntry} with price data,
     *         or {@code null} if the add operation fails
     */
    public GlobalWatchlistEntry addCompany(String companyCode) {
        String url = globalWatchlistServiceUrl + "/api/global-watchlist/add";
        logger.info("Adding company '{}' to global watchlist via {}", companyCode, url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("companyCode", companyCode), headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            logger.info("Company '{}' successfully added to global watchlist", companyCode);
            return toEntry(response.getBody());
        } catch (Exception e) {
            logger.error("Failed to add company '{}' to global watchlist: {}", companyCode, e.getMessage());
            return null;
        }
    }

    /**
     * Maps a JSON response body from global-watchlist-service to a {@link GlobalWatchlistEntry}.
     *
     * @param body the parsed JSON response node; may be {@code null}
     * @return the populated entry, or {@code null} if {@code body} is {@code null}
     */
    private GlobalWatchlistEntry toEntry(JsonNode body) {
        if (body == null) return null;
        return new GlobalWatchlistEntry(
            body.has("currentValue")  ? body.get("currentValue").decimalValue()  : null,
            body.has("week52Low")     ? body.get("week52Low").decimalValue()     : null,
            body.has("week52High")    ? body.get("week52High").decimalValue()    : null,
            body.has("allTimeLow")    ? body.get("allTimeLow").decimalValue()    : null,
            body.has("allTimeHigh")   ? body.get("allTimeHigh").decimalValue()   : null,
            body.has("tradedVolume")  ? body.get("tradedVolume").decimalValue()  : null,
            body.has("percentChange") ? body.get("percentChange").decimalValue() : null,
            body.has("changeValue")   ? body.get("changeValue").decimalValue()   : null
        );
    }

    /**
     * Immutable value object holding live price data for a company
     * as returned by the global-watchlist-service.
     */
    public static class GlobalWatchlistEntry {

        /** Latest traded price from NSE. */
        private final BigDecimal currentValue;

        /** 52-week low price. */
        private final BigDecimal week52Low;

        /** 52-week high price. */
        private final BigDecimal week52High;

        /** All-time low price recorded in the global watchlist. */
        private final BigDecimal allTimeLow;

        /** All-time high price recorded in the global watchlist. */
        private final BigDecimal allTimeHigh;

        /** Total traded volume for the latest session. */
        private final BigDecimal tradedVolume;

        /** Percentage change from previous close (e.g. -1.28 means -1.28%). */
        private final BigDecimal percentChange;

        /** Absolute change from previous close (e.g. -16.5). */
        private final BigDecimal changeValue;

        public GlobalWatchlistEntry(BigDecimal currentValue, BigDecimal week52Low, BigDecimal week52High,
                                    BigDecimal allTimeLow, BigDecimal allTimeHigh, BigDecimal tradedVolume,
                                    BigDecimal percentChange, BigDecimal changeValue) {
            this.currentValue  = currentValue;
            this.week52Low     = week52Low;
            this.week52High    = week52High;
            this.allTimeLow    = allTimeLow;
            this.allTimeHigh   = allTimeHigh;
            this.tradedVolume  = tradedVolume;
            this.percentChange = percentChange;
            this.changeValue   = changeValue;
        }

        public BigDecimal getCurrentValue()  { return currentValue; }
        public BigDecimal getWeek52Low()     { return week52Low; }
        public BigDecimal getWeek52High()    { return week52High; }
        public BigDecimal getAllTimeLow()     { return allTimeLow; }
        public BigDecimal getAllTimeHigh()    { return allTimeHigh; }
        public BigDecimal getTradedVolume()  { return tradedVolume; }
        public BigDecimal getPercentChange() { return percentChange; }
        public BigDecimal getChangeValue()   { return changeValue; }
    }
}
