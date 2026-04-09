package com.watchlist.global.service;

import com.watchlist.global.client.NsePriceClient;
import com.watchlist.global.client.NseClient;
import com.watchlist.global.entity.GlobalWatchlist;
import com.watchlist.global.model.GlobalWatchlistEntry;
import com.watchlist.global.repository.GlobalWatchlistRepository;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service managing the in-memory global watchlist price cache.
 *
 * <p>On startup ({@link PostConstruct}), seeds NIFTY 50 symbols into the
 * {@code global_watchlist} table (if not already present), then loads all rows
 * into the in-memory map. Non-NIFTY 50 companies enter the cache organically
 * when {@code watchlist-service} calls {@link #addCompany(String)}.
 *
 * <p>Prices are refreshed every 5 minutes during market hours and persisted
 * to the {@code global_watchlist} table at market close (3:30 PM IST).
 */
@Service
public class GlobalWatchlistService {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistService.class);

    /** In-memory cache: NSE symbol → latest price entry. */
    private final ConcurrentHashMap<String, GlobalWatchlistEntry> globalMap = new ConcurrentHashMap<>();

    private final GlobalWatchlistRepository repository;
    private final NseClient                 nseClient;
    private final NsePriceClient            nsePriceClient;

    /**
     * Constructs the service with required dependencies.
     *
     * @param repository     JPA repository for the {@code global_watchlist} table
     * @param nseClient      client for fetching NIFTY 50 symbols from NSE
     * @param nsePriceClient client for fetching live price data directly from NSE
     */
    public GlobalWatchlistService(GlobalWatchlistRepository repository,
                                  NseClient nseClient,
                                  NsePriceClient nsePriceClient) {
        this.repository     = repository;
        this.nseClient      = nseClient;
        this.nsePriceClient = nsePriceClient;
    }

    /**
     * Initialises the in-memory cache on application startup.
     *
     * <p>Steps:
     * <ol>
     *   <li>Fetch NIFTY 50 symbols from NSE. For each symbol, insert a row into
     *       {@code global_watchlist} if one does not already exist (skipped on restarts).</li>
     *   <li>Load ALL rows from {@code global_watchlist} into the in-memory map — covers
     *       NIFTY 50 plus any companies added by users in previous runs.</li>
     * </ol>
     *
     * <p>This service has no dependency on the {@code watchlist} table. Non-NIFTY 50
     * companies enter the cache organically via {@link #addCompany(String)}, which
     * {@code watchlist-service} calls before adding any company to a user's watchlist.
     */
    @PostConstruct
    public void init() {
        logger.info("GlobalWatchlistService: initialising in-memory cache...");

        // Step 1: Fetch NIFTY 50 and insert any symbols not yet in global_watchlist, set is_nifty50 flag
        List<String> nifty50 = nseClient.fetchNifty50Symbols();
        logger.info("Fetched {} NIFTY 50 symbols from NSE", nifty50.size());
        Set<String> nifty50Set = new HashSet<>(nifty50);
        for (String code : nifty50) {
            if (!repository.existsByCompanyCode(code)) {
                GlobalWatchlist entity = new GlobalWatchlist();
                entity.setCompanyCode(code);
                entity.setNifty50(true);
                repository.save(entity);
                logger.debug("Inserted NIFTY 50 company '{}' into global_watchlist", code);
            } else {
                repository.findByCompanyCode(code).ifPresent(entity -> {
                    if (!entity.isNifty50()) {
                        entity.setNifty50(true);
                        repository.save(entity);
                    }
                });
            }
        }
        // Clear is_nifty50 for any company no longer in the index
        repository.findAll().forEach(entity -> {
            if (entity.isNifty50() && !nifty50Set.contains(entity.getCompanyCode())) {
                entity.setNifty50(false);
                repository.save(entity);
                logger.info("Cleared is_nifty50 for '{}' (no longer in NIFTY 50)", entity.getCompanyCode());
            }
        });

        // Step 2: Load ALL rows into cache (NIFTY 50 + user-added companies from previous runs)
        repository.findAll().forEach(entity -> globalMap.put(entity.getCompanyCode(), toEntry(entity)));
        logger.info("Loaded {} companies into in-memory map", globalMap.size());
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

            if (price.getWeek52Low() != null)    entry.setWeek52Low(price.getWeek52Low());
            if (price.getWeek52High() != null)   entry.setWeek52High(price.getWeek52High());
            if (price.getAllTimeLow() != null)    entry.setAllTimeLow(price.getAllTimeLow());
            if (price.getAllTimeHigh() != null)   entry.setAllTimeHigh(price.getAllTimeHigh());
            if (price.getPreviousClose() != null) entry.setPreviousClose(price.getPreviousClose());
            if (price.getChangeValue() != null)   entry.setChangeValue(price.getChangeValue());
            if (price.getPChange() != null)       entry.setPercentChange(price.getPChange());

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
            entity.setPreviousClose(entry.getPreviousClose());
            entity.setChangeValue(entry.getChangeValue());
            entity.setPercentChange(entry.getPercentChange());
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
            entity.setPreviousClose(price.getPreviousClose());
            entity.setChangeValue(price.getChangeValue());
            entity.setPercentChange(price.getPChange());
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
    /**
     * Re-fetches NIFTY 50 composition from NSE and updates the {@code is_nifty50} flag
     * in both the DB and in-memory map. Called by the bi-weekly scheduler.
     */
    public void refreshNifty50Composition() {
        logger.info("Refreshing NIFTY 50 composition...");
        List<String> nifty50 = nseClient.fetchNifty50Symbols();
        Set<String> nifty50Set = new HashSet<>(nifty50);

        // Mark entrants true
        for (String code : nifty50Set) {
            if (!repository.existsByCompanyCode(code)) {
                GlobalWatchlist entity = new GlobalWatchlist();
                entity.setCompanyCode(code);
                entity.setNifty50(true);
                repository.save(entity);
                GlobalWatchlistEntry entry = toEntry(entity);
                globalMap.put(code, entry);
                logger.info("New NIFTY 50 entrant '{}' added", code);
            } else {
                repository.findByCompanyCode(code).ifPresent(entity -> {
                    if (!entity.isNifty50()) {
                        entity.setNifty50(true);
                        repository.save(entity);
                        logger.info("'{}' re-entered NIFTY 50", code);
                    }
                    globalMap.computeIfPresent(code, (k, e) -> { e.setNifty50(true); return e; });
                });
            }
        }

        // Mark exits false
        repository.findAll().forEach(entity -> {
            if (entity.isNifty50() && !nifty50Set.contains(entity.getCompanyCode())) {
                entity.setNifty50(false);
                repository.save(entity);
                logger.info("'{}' exited NIFTY 50", entity.getCompanyCode());
                globalMap.computeIfPresent(entity.getCompanyCode(), (k, e) -> { e.setNifty50(false); return e; });
            }
        });

        logger.info("NIFTY 50 composition refresh complete — {} symbols", nifty50Set.size());
    }

    /**
     * Returns all entries where {@code isNifty50} is true, from the in-memory cache.
     *
     * @return list of NIFTY 50 entries
     */
    public List<GlobalWatchlistEntry> getNifty50() {
        List<GlobalWatchlistEntry> result = new ArrayList<>();
        globalMap.values().forEach(e -> { if (e.isNifty50()) result.add(e); });
        return result;
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
        entry.setPreviousClose(entity.getPreviousClose());
        entry.setChangeValue(entity.getChangeValue());
        entry.setPercentChange(entity.getPercentChange());
        entry.setNifty50(entity.isNifty50());
        entry.setLastUpdated(entity.getLastUpdated());
        return entry;
    }
}
