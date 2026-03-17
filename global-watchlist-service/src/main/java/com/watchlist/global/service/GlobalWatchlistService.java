package com.watchlist.global.service;

import com.watchlist.global.client.MarketDataClient;
import com.watchlist.global.client.NseClient;
import com.watchlist.global.entity.GlobalWatchlist;
import com.watchlist.global.model.GlobalWatchlistEntry;
import com.watchlist.global.repository.GlobalWatchlistRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GlobalWatchlistService {

    private final ConcurrentHashMap<String, GlobalWatchlistEntry> globalMap = new ConcurrentHashMap<>();

    private final GlobalWatchlistRepository repository;
    private final NseClient nseClient;
    private final MarketDataClient marketDataClient;
    private final JdbcTemplate jdbcTemplate;

    public GlobalWatchlistService(GlobalWatchlistRepository repository,
                                  NseClient nseClient,
                                  MarketDataClient marketDataClient,
                                  JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.nseClient = nseClient;
        this.marketDataClient = marketDataClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        System.out.println("GlobalWatchlistService: initializing...");

        // Step 1: Fetch NIFTY 50 symbols
        List<String> nifty50 = nseClient.fetchNifty50Symbols();

        // Step 2: Load all distinct company codes from user watchlists
        List<String> userWatchlistCodes = jdbcTemplate.queryForList(
            "SELECT DISTINCT company_code FROM watchlist", String.class);

        // Step 3: Union both sets (deduplicated)
        Set<String> allCodes = new LinkedHashSet<>();
        allCodes.addAll(nifty50);
        allCodes.addAll(userWatchlistCodes);
        System.out.println("Total companies to seed: " + allCodes.size());

        // Step 4: Seed global_watchlist table for any missing companies, then load all into map
        for (String code : allCodes) {
            if (!repository.existsByCompanyCode(code)) {
                GlobalWatchlist entity = new GlobalWatchlist();
                entity.setCompanyCode(code);
                repository.save(entity);
            }
        }

        // Load all DB rows into map
        repository.findAll().forEach(entity -> globalMap.put(entity.getCompanyCode(), toEntry(entity)));
        System.out.println("Loaded " + globalMap.size() + " companies into in-memory map");

        // Step 5: Fetch initial prices
        refreshPrices();
    }

    /** Called by scheduler every 5 min during market hours. */
    public void refreshPrices() {
        System.out.println("Refreshing prices for " + globalMap.size() + " companies...");
        for (String code : globalMap.keySet()) {
            MarketDataClient.PriceData price = marketDataClient.fetchPrice(code);
            if (price == null) continue;

            GlobalWatchlistEntry entry = globalMap.get(code);
            if (entry == null) continue;

            entry.setCurrentValue(price.currentPrice);
            entry.setTradedVolume(price.tradedVolume);

            if (price.week52Low != null)
                entry.setWeek52Low(price.week52Low);
            if (price.week52High != null)
                entry.setWeek52High(price.week52High);
            if (price.allTimeLow != null)
                entry.setAllTimeLow(price.allTimeLow);
            if (price.allTimeHigh != null)
                entry.setAllTimeHigh(price.allTimeHigh);

            entry.setLastUpdated(LocalDateTime.now());
            globalMap.put(code, entry);
        }
    }

    /** Called by scheduler at 3:30 PM — batch saves map to DB. */
    public void persistMarketClose() {
        System.out.println("Market close: persisting " + globalMap.size() + " entries to DB...");
        List<GlobalWatchlist> toSave = new ArrayList<>();

        globalMap.forEach((code, entry) -> {
            repository.findByCompanyCode(code).ifPresent(entity -> {
                entity.setCurrentValue(entry.getCurrentValue());
                entity.setWeek52Low(entry.getWeek52Low());
                entity.setWeek52High(entry.getWeek52High());
                entity.setAllTimeLow(entry.getAllTimeLow());
                entity.setAllTimeHigh(entry.getAllTimeHigh());
                entity.setTradedVolume(entry.getTradedVolume());
                entity.setLastUpdated(LocalDateTime.now());
                toSave.add(entity);
            });
        });

        repository.saveAll(toSave);
        System.out.println("Market close snapshot saved for " + toSave.size() + " companies");
    }

    /** Called by watchlist-service when user adds a company not yet in global watchlist. */
    public GlobalWatchlistEntry addCompany(String companyCode) {
        if (globalMap.containsKey(companyCode)) {
            return globalMap.get(companyCode);
        }

        // Fetch price from market-data-service
        MarketDataClient.PriceData price = marketDataClient.fetchPrice(companyCode);

        // Persist to DB
        GlobalWatchlist entity = new GlobalWatchlist();
        entity.setCompanyCode(companyCode);
        if (price != null) {
            entity.setCurrentValue(price.currentPrice);
            entity.setWeek52Low(price.week52Low);
            entity.setWeek52High(price.week52High);
            entity.setAllTimeLow(price.allTimeLow);
            entity.setAllTimeHigh(price.allTimeHigh);
            entity.setTradedVolume(price.tradedVolume);
        }
        repository.save(entity);

        // Add to map
        GlobalWatchlistEntry entry = toEntry(entity);
        globalMap.put(companyCode, entry);

        return entry;
    }

    public GlobalWatchlistEntry getEntry(String companyCode) {
        return globalMap.get(companyCode);
    }

    public Collection<GlobalWatchlistEntry> getAll() {
        return globalMap.values();
    }

    private GlobalWatchlistEntry toEntry(GlobalWatchlist entity) {
        GlobalWatchlistEntry entry = new GlobalWatchlistEntry();
        entry.setCompanyCode(entity.getCompanyCode());
        entry.setCurrentValue(entity.getCurrentValue());
        entry.setWeek52Low(entity.getWeek52Low());
        entry.setWeek52High(entity.getWeek52High());
        entry.setAllTimeLow(entity.getAllTimeLow());
        entry.setAllTimeHigh(entity.getAllTimeHigh());
        entry.setTradedVolume(entity.getTradedVolume());
        entry.setLastUpdated(entity.getLastUpdated());
        return entry;
    }
}
