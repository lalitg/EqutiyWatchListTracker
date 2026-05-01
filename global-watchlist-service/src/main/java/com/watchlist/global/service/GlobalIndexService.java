package com.watchlist.global.service;

import com.watchlist.global.client.YahooFinanceClient;
import com.watchlist.global.model.GlobalIndexEntry;
import com.watchlist.global.model.GlobalIndexEntry.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class GlobalIndexService {

    private static final Logger logger = LogManager.getLogger(GlobalIndexService.class);

    private final YahooFinanceClient yahooClient;

    // Thread-safe snapshot — replaced atomically on each refresh
    private volatile List<GlobalIndexEntry> cache = new CopyOnWriteArrayList<>();

    public GlobalIndexService(YahooFinanceClient yahooClient) {
        this.yahooClient = yahooClient;
    }

    public void refreshAll() {
        List<GlobalIndexEntry> fresh = yahooClient.fetchAll();
        if (!fresh.isEmpty()) {
            cache = fresh;
            logger.info("Global index cache refreshed — {} entries", fresh.size());
        } else {
            logger.warn("Yahoo Finance returned empty result — keeping stale cache");
        }
    }

    /** Returns global indices grouped by region for the API response. */
    public Map<String, List<GlobalIndexEntry>> getGroupedByRegion() {
        Map<String, List<GlobalIndexEntry>> grouped = new LinkedHashMap<>();
        grouped.put("usMarkets", new ArrayList<>());
        grouped.put("europeanMarkets", new ArrayList<>());
        grouped.put("asianMarkets", new ArrayList<>());

        for (GlobalIndexEntry e : cache) {
            if (e.getRegion() == Region.US_MARKETS)        grouped.get("usMarkets").add(e);
            else if (e.getRegion() == Region.EUROPEAN_MARKETS) grouped.get("europeanMarkets").add(e);
            else if (e.getRegion() == Region.ASIAN_MARKETS)    grouped.get("asianMarkets").add(e);
        }
        return grouped;
    }
}
