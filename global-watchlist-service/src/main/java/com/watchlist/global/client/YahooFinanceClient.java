package com.watchlist.global.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchlist.global.model.GlobalIndexEntry;
import com.watchlist.global.model.GlobalIndexEntry.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gets live global index data by running yfinance_wrapper.py as a short-lived child
 * process (see {@link PythonProcessRunner}) rather than calling a persistent Flask
 * server. The Python wrapper handles Yahoo Finance auth internally via the yfinance
 * library — no API key or cookie management needed here.
 *
 * <p>Nothing Python-related stays running between calls to {@link #fetchAll()} — the
 * process is spawned, does the batch fetch, prints its result, and exits.
 */
@Component
public class YahooFinanceClient {

    private static final Logger logger = LogManager.getLogger(YahooFinanceClient.class);

    private static final Map<String, Region> REGION_MAP = Map.of(
        "US_MARKETS",        Region.US_MARKETS,
        "EUROPEAN_MARKETS",  Region.EUROPEAN_MARKETS,
        "ASIAN_MARKETS",     Region.ASIAN_MARKETS,
        "INDIAN_MARKETS",    Region.INDIAN_MARKETS,
        "COMMODITIES",       Region.COMMODITIES
    );

    private final PythonProcessRunner processRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String scriptPath;

    public YahooFinanceClient(
            PythonProcessRunner processRunner,
            @Value("${python.global-indices.script:yfinance_wrapper.py}") String scriptPath) {
        this.processRunner = processRunner;
        this.scriptPath = scriptPath;
    }

    public List<GlobalIndexEntry> fetchAll() {
        List<GlobalIndexEntry> result = new ArrayList<>();
        try {
            String json = processRunner.run(scriptPath);
            if (json == null || json.isBlank()) {
                logger.error("yfinance_wrapper.py produced no output — see PythonProcessRunner logs above");
                return Collections.emptyList();
            }

            List<Map<String, Object>> rows = objectMapper.readValue(
                json, new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> row : rows) {
                String  sym    = str(row, "symbol");
                String  name   = str(row, "name");
                String  flag   = str(row, "flagEmoji");
                Region  region = REGION_MAP.getOrDefault(str(row, "region"), Region.US_MARKETS);

                GlobalIndexEntry entry = new GlobalIndexEntry(sym, name, flag, region);
                entry.setLtp(decimal(row, "ltp"));
                entry.setChange(decimal(row, "change"));
                entry.setChangePercent(decimal(row, "changePercent"));
                entry.setLastUpdated(LocalDateTime.now());
                result.add(entry);
            }
            logger.info("yfinance wrapper: fetched {} global indices", result.size());
        } catch (Exception e) {
            logger.error("Failed to fetch global indices from yfinance wrapper: {}", e.getMessage());
        }
        return result;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private BigDecimal decimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}