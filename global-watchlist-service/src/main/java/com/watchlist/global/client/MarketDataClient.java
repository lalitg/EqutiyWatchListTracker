package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * REST client for market-data-service.
 * Calls GET /api/market/price/{companyCode} to fetch latest price data.
 */
@Component
public class MarketDataClient {

    @Value("${market.data.service.url}")
    private String marketDataServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public PriceData fetchPrice(String companyCode) {
        try {
            String url = marketDataServiceUrl + "/api/market/price/" + companyCode;
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, null, JsonNode.class);

            JsonNode body = response.getBody();
            if (body != null) {
                PriceData data = new PriceData();
                data.currentPrice = body.has("currentPrice") ? body.get("currentPrice").decimalValue() : null;
                data.week52Low    = body.has("week52Low")    ? body.get("week52Low").decimalValue()    : null;
                data.week52High   = body.has("week52High")   ? body.get("week52High").decimalValue()   : null;
                data.allTimeLow   = body.has("allTimeLow")   ? body.get("allTimeLow").decimalValue()   : null;
                data.allTimeHigh  = body.has("allTimeHigh")  ? body.get("allTimeHigh").decimalValue()  : null;
                data.tradedVolume = body.has("tradedVolume") ? body.get("tradedVolume").decimalValue() : null;
                return data;
            }
        } catch (Exception e) {
            System.out.println("Failed to fetch price for " + companyCode + ": " + e.getMessage());
        }
        return null;
    }

    public static class PriceData {
        public BigDecimal currentPrice;
        public BigDecimal week52Low;
        public BigDecimal week52High;
        public BigDecimal allTimeLow;
        public BigDecimal allTimeHigh;
        public BigDecimal tradedVolume;
    }
}
