package com.watchlist.global.service;

import com.watchlist.global.client.NsePriceClient;
import com.watchlist.global.client.NseClient;
import com.watchlist.global.entity.GlobalWatchlist;
import com.watchlist.global.model.GlobalWatchlistEntry;
import com.watchlist.global.repository.GlobalWatchlistRepository;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service managing the in-memory global watchlist price cache.
 *
 * <p>On startup ({@link PostConstruct}), seeds the cache from the DB, unions in
 * NIFTY 50 symbols fetched from NSE, and loads all rows into the in-memory map.
 * After full startup, {@link com.watchlist.global.scheduler.GlobalWatchlistScheduler}
 * triggers a price refresh and DB persist via {@link ApplicationReadyEvent}.
 * Prices are then refreshed every 5 minutes during market hours and persisted
 * to the {@code global_watchlist} table at market close.
 */
@Service
public class GlobalWatchlistService {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistService.class);

    /** In-memory cache: NSE symbol → latest price entry. */
    private final ConcurrentHashMap<String, GlobalWatchlistEntry> globalMap = new ConcurrentHashMap<>();

    private final GlobalWatchlistRepository repository;
    private final NseClient                 nseClient;
    private final NsePriceClient            nsePriceClient;
    private final JdbcTemplate              jdbcTemplate;

    /**
     * Constructs the service with required dependencies.
     *
     * @param repository     JPA repository for the {@code global_watchlist} table
     * @param nseClient      client for fetching NIFTY 50 symbols from NSE
     * @param nsePriceClient client for fetching live price data directly from NSE
     * @param jdbcTemplate   JDBC template for raw SQL queries
     */
    public GlobalWatchlistService(GlobalWatchlistRepository repository,
                                  NseClient nseClient,
                                  NsePriceClient nsePriceClient,
                                  JdbcTemplate jdbcTemplate) {
        this.repository     = repository;
        this.nseClient      = nseClient;
        this.nsePriceClient = nsePriceClient;
        this.jdbcTemplate   = jdbcTemplate;
    }

    /**
     * Initialises the in-memory cache on application startup.
     *
     * <p>Steps:
     * <ol>
     *   <li>Fetch NIFTY 50 symbols from NSE</li>
     *   <li>Load all distinct company codes from user watchlists</li>
     *   <li>Union both sets and seed missing rows into {@code global_watchlist}</li>
     *   <li>Load all DB rows into the in-memory map</li>
     * </ol>
     */
    @PostConstruct
    public void init() {
        logger.info("GlobalWatchlistService: initialising in-memory cache...");

        // Step 1: Fetch NIFTY 50 symbols
        List<String> nifty50 = nseClient.fetchNifty50Symbols();

        // Step 2: Load all distinct company codes from user watchlists
        List<String> userWatchlistCodes = jdbcTemplate.queryForList(
            "SELECT DISTINCT company_code FROM watchlist", String.class);

        // Step 3: Union both sets (deduplicated)
        Set<String> allCodes = new LinkedHashSet<>();
        allCodes.addAll(nifty50);
        allCodes.addAll(userWatchlistCodes);
        logger.info("Total companies to seed: {}", allCodes.size());

        // Step 4: Seed global_watchlist table for any missing companies
        for (String code : allCodes) {
            if (!repository.existsByCompanyCode(code)) {
                GlobalWatchlist entity = new GlobalWatchlist();
                entity.setCompanyCode(code);
                repository.save(entity);
                logger.debug("Seeded new company '{}' into global_watchlist", code);
            }
        }

        // Load all DB rows into map
        repository.findAll().forEach(entity -> globalMap.put(entity.getCompanyCode(), toEntry(entity)));
        logger.info("Loaded {} companies into in-memory map", globalMap.size());
        // Price refresh and persist are triggered after full startup via ApplicationReadyEvent
    }

    /**
     * Refreshes live prices for all tracked companies by calling the NSE API directly.
     *
     * <p>Performs a single cookie handshake before the loop, then reuses that
     * session cookie for all company calls in the batch. Called every 5 minutes
     * during market hours and once at startup.
     */
    public void refreshPrices() {
        logger.info("Refreshing prices for {} companies...", globalMap.size());

        String cookies = nsePriceClient.fetchSessionCookies();
        if (cookies.isEmpty()) {
            logger.warn("Could not obtain NSE session cookies — skipping price refresh");
            return;
        }

        int updated = 0;
        for (String code : globalMap.keySet()) {
            NsePriceClient.PriceData price = nsePriceClient.fetchPrice(code, cookies);
            if (price == null) continue;

            GlobalWatchlistEntry entry = globalMap.get(code);
            if (entry == null) continue;

            entry.setCurrentValue(price.getCurrentPrice());
            entry.setTradedVolume(price.getTradedVolume());

            if (price.getWeek52Low() != null)  entry.setWeek52Low(price.getWeek52Low());
            if (price.getWeek52High() != null) entry.setWeek52High(price.getWeek52High());
            if (price.getAllTimeLow() != null)  entry.setAllTimeLow(price.getAllTimeLow());
            if (price.getAllTimeHigh() != null) entry.setAllTimeHigh(price.getAllTimeHigh());

            entry.setLastUpdated(LocalDateTime.now());
            globalMap.put(code, entry);
            updated++;
        }
        logger.info("Price refresh complete — updated {} / {} companies", updated, globalMap.size());
    }

    /**
     * Batch-persists the current in-memory state to the {@code global_watchlist} table.
     *
     * <p>Loads all DB entities in a single query, applies the in-memory values,
     * then saves all changes in one batch — avoiding N individual SELECT queries.
     *
     * <p>Called at 3:30 PM IST (market close) on trading days and once at startup.
     */
    public void persistMarketClose() {
        logger.info("Persisting {} entries to DB...", globalMap.size());

        // Load all entities once — avoids N individual SELECT queries per company
        Map<String, GlobalWatchlist> dbMap = new HashMap<>();
        repository.findAll().forEach(entity -> dbMap.put(entity.getCompanyCode(), entity));

        List<GlobalWatchlist> toSave = new ArrayList<>();
        globalMap.forEach((code, entry) -> {
            GlobalWatchlist entity = dbMap.get(code);
            if (entity == null) {
                logger.warn("Company '{}' found in cache but not in DB — skipping persist", code);
                return;
            }
            entity.setCurrentValue(entry.getCurrentValue());
            entity.setWeek52Low(entry.getWeek52Low());
            entity.setWeek52High(entry.getWeek52High());
            entity.setAllTimeLow(entry.getAllTimeLow());
            entity.setAllTimeHigh(entry.getAllTimeHigh());
            entity.setTradedVolume(entry.getTradedVolume());
            entity.setLastUpdated(LocalDateTime.now());
            toSave.add(entity);
        });

        repository.saveAll(toSave);
        logger.info("Persist complete — saved {} companies", toSave.size());
    }

    /**
     * Adds a company to the global watchlist.
     *
     * <p>If the company is already in the in-memory map, returns the cached entry
     * immediately. Otherwise fetches live price data from NSE, persists to DB,
     * and adds to the in-memory map.
     *
     * @param companyCode the NSE symbol to add (must be non-null and upper-cased by caller)
     * @return the {@link GlobalWatchlistEntry} for the company
     */
    public GlobalWatchlistEntry addCompany(String companyCode) {
        if (globalMap.containsKey(companyCode)) {
            logger.debug("Company '{}' already in global map — returning cached entry", companyCode);
            return globalMap.get(companyCode);
        }

        logger.info("Adding new company '{}' to global watchlist", companyCode);

        String cookies = nsePriceClient.fetchSessionCookies();
        if (cookies.isEmpty()) {
            logger.warn("Could not obtain NSE session cookies — company '{}' will be seeded without price data", companyCode);
        }
        NsePriceClient.PriceData price = cookies.isEmpty() ? null : nsePriceClient.fetchPrice(companyCode, cookies);

        GlobalWatchlist entity = new GlobalWatchlist();
        entity.setCompanyCode(companyCode);
        if (price != null) {
            entity.setCurrentValue(price.getCurrentPrice());
            entity.setWeek52Low(price.getWeek52Low());
            entity.setWeek52High(price.getWeek52High());
            entity.setAllTimeLow(price.getAllTimeLow());
            entity.setAllTimeHigh(price.getAllTimeHigh());
            entity.setTradedVolume(price.getTradedVolume());
        }
        repository.save(entity);

        GlobalWatchlistEntry entry = toEntry(entity);
        globalMap.put(companyCode, entry);
        logger.info("Company '{}' added to global watchlist successfully", companyCode);

        return entry;
    }

    /**
     * Returns a single entry from the in-memory cache.
     *
     * @param companyCode the NSE symbol to look up
     * @return the cached {@link GlobalWatchlistEntry}, or {@code null} if not tracked
     */
    public GlobalWatchlistEntry getEntry(String companyCode) {
        return globalMap.get(companyCode);
    }

    /**
     * Returns all entries currently in the in-memory cache.
     *
     * @return a collection of all {@link GlobalWatchlistEntry} objects
     */
    public Collection<GlobalWatchlistEntry> getAll() {
        return globalMap.values();
    }

    /**
     * Converts a JPA entity to an in-memory value object.
     *
     * @param entity the {@link GlobalWatchlist} entity loaded from DB
     * @return a populated {@link GlobalWatchlistEntry}
     */
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
