package com.watchlist.marketdata.controller;

import com.watchlist.marketdata.client.NSEMarketDataClient;
import com.watchlist.marketdata.client.NSEMarketDataClient.MarketData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final NSEMarketDataClient nseClient;

    public MarketDataController(NSEMarketDataClient nseClient) {
        this.nseClient = nseClient;
    }

    /**
     * Called by global-watchlist-service every 5 minutes per company.
     * Fetches live price data from NSE and returns it.
     */
    @GetMapping("/price/{companyCode}")
    public ResponseEntity<Map<String, Object>> getPrice(@PathVariable String companyCode) {
        MarketData data = nseClient.fetch(companyCode.toUpperCase());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("companyCode", companyCode.toUpperCase());
        response.put("currentPrice", data.currentPrice);
        response.put("week52Low", data.week52Low);
        response.put("week52High", data.week52High);
        response.put("allTimeLow", data.allTimeLow);
        response.put("allTimeHigh", data.allTimeHigh);
        response.put("tradedVolume", data.tradedVolume);

        return ResponseEntity.ok(response);
    }
}
