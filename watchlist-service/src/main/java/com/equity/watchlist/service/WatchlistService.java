package com.equity.watchlist.service;

import com.equity.watchlist.client.GlobalWatchlistClient;
import com.equity.watchlist.client.GlobalWatchlistClient.GlobalWatchlistEntry;
import com.equity.watchlist.dto.WatchlistRequest;
import com.equity.watchlist.dto.WatchlistView;
import com.equity.watchlist.entity.Watchlist;
import com.equity.watchlist.repository.CompanyRepository;
import com.equity.watchlist.repository.WatchlistRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service layer handling all business logic for watchlist operations.
 *
 * <p>Coordinates between {@link WatchlistRepository}, {@link CompanyRepository},
 * and {@link GlobalWatchlistClient} to provide a complete watchlist management
 * experience with live NSE price data.
 */
@Service
public class WatchlistService {

    private static final Logger logger = LogManager.getLogger(WatchlistService.class);

    /** Default user ID used until multi-user auth is introduced. */
    private static final Long DEFAULT_USER_ID = 1L;

    private final WatchlistRepository watchlistRepository;
    private final CompanyRepository companyRepository;
    private final GlobalWatchlistClient globalWatchlistClient;

    /**
     * Constructs the service with required dependencies.
     *
     * @param watchlistRepository  repository for watchlist table CRUD
     * @param companyRepository    repository for company_master lookups
     * @param globalWatchlistClient REST client for global-watchlist-service
     */
    public WatchlistService(WatchlistRepository watchlistRepository,
                            CompanyRepository companyRepository,
                            GlobalWatchlistClient globalWatchlistClient) {
        this.watchlistRepository = watchlistRepository;
        this.companyRepository = companyRepository;
        this.globalWatchlistClient = globalWatchlistClient;
    }

    /**
     * Adds a new company to the user's watchlist.
     *
     * <p>Live price data (currentValue, 52-week high/low, all-time high/low,
     * traded volume) is fetched from global-watchlist-service. If the company
     * is not yet tracked globally, it is added first (triggering an NSE fetch).
     *
     * @param request the add request containing the company code
     * @return a {@link WatchlistView} representing the saved entry
     * @throws IllegalArgumentException if the company code is null or blank
     */
    public WatchlistView addCompany(WatchlistRequest request) {
        if (request.getCompanyCode() == null || request.getCompanyCode().isBlank()) {
            throw new IllegalArgumentException("Company code is required");
        }

        String code = request.getCompanyCode().toUpperCase().trim();
        logger.info("Adding company '{}' to watchlist", code);

        GlobalWatchlistEntry entry = globalWatchlistClient.getEntry(code);
        if (entry == null) {
            logger.info("Company '{}' not in global watchlist — triggering add", code);
            entry = globalWatchlistClient.addCompany(code);
        }

        Watchlist entity = new Watchlist();
        entity.setUserId(DEFAULT_USER_ID);
        entity.setCompanyCode(code);

        if (entry != null) {
            entity.setWeek52Low(entry.getWeek52Low());
            entity.setWeek52High(entry.getWeek52High());
            entity.setAllTimeLow(entry.getAllTimeLow());
            entity.setAllTimeHigh(entry.getAllTimeHigh());
            entity.setCurrentValue(entry.getCurrentValue());
            entity.setTradedVolume(entry.getTradedVolume());
        } else {
            logger.warn("No price data available for '{}' — saving with null price fields", code);
        }

        Watchlist saved = watchlistRepository.save(entity);
        logger.info("Company '{}' saved to watchlist with id={}", saved.getCompanyCode(), saved.getId());
        return toView(saved);
    }

    /**
     * Returns all watchlist entries for the current user, ordered by creation time (oldest first).
     *
     * @return a list of {@link WatchlistView} DTOs; empty list if the watchlist is empty
     */
    public List<WatchlistView> getWatchlist() {
        logger.debug("Fetching watchlist for userId={}", DEFAULT_USER_ID);
        return watchlistRepository.findByUserIdOrderByCreatedAtAsc(DEFAULT_USER_ID)
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /**
     * Updates the price fields of an existing watchlist entry.
     *
     * @param companyCode the NSE symbol identifying the entry to update
     * @param request     the update request containing the new price values
     * @return the updated {@link WatchlistView}
     * @throws IllegalArgumentException if no entry exists for the given company code
     */
    public WatchlistView updateCompany(String companyCode, WatchlistRequest request) {
        logger.info("Updating watchlist entry for company '{}'", companyCode);

        Watchlist entity = watchlistRepository
                .findByUserIdAndCompanyCode(DEFAULT_USER_ID, companyCode)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyCode));

        if (request.getCompanyCode() != null && !request.getCompanyCode().isBlank()) {
            entity.setCompanyCode(request.getCompanyCode().toUpperCase().trim());
        }
        entity.setWeek52Low(request.getWeek52Low());
        entity.setWeek52High(request.getWeek52High());
        entity.setAllTimeLow(request.getAllTimeLow());
        entity.setAllTimeHigh(request.getAllTimeHigh());
        entity.setCurrentValue(request.getCurrentValue());
        entity.setTradedVolume(request.getTradedVolume());

        Watchlist updated = watchlistRepository.save(entity);
        logger.info("Company '{}' updated successfully", updated.getCompanyCode());
        return toView(updated);
    }

    /**
     * Removes a company from the user's watchlist.
     *
     * @param companyCode the NSE symbol of the entry to delete
     */
    @Transactional
    public void removeCompany(String companyCode) {
        logger.info("Removing company '{}' from watchlist", companyCode);
        watchlistRepository.deleteByUserIdAndCompanyCode(DEFAULT_USER_ID, companyCode);
        logger.info("Company '{}' removed successfully", companyCode);
    }

    /**
     * Bulk-imports a list of company codes into the watchlist.
     *
     * <p>For each code, the method:
     * <ol>
     *   <li>Skips if already present in the user's watchlist</li>
     *   <li>Checks global-watchlist-service for live price data</li>
     *   <li>If not found globally, triggers an add (fetches live price from NSE)</li>
     *   <li>Persists the entry into the watchlist table</li>
     * </ol>
     *
     * @param companyCodes the list of NSE symbols to import
     * @return a summary map with keys {@code imported}, {@code skipped}, {@code failed},
     *         and optionally {@code failedCodes} listing unresolvable symbols
     */
    public Map<String, Object> importCompanies(List<String> companyCodes) {
        int imported = 0, skipped = 0, failed = 0;
        List<String> failedCodes = new ArrayList<>();

        for (String raw : companyCodes) {
            String code = raw.toUpperCase().trim();
            if (code.isBlank()) continue;

            if (watchlistRepository.findByUserIdAndCompanyCode(DEFAULT_USER_ID, code).isPresent()) {
                logger.debug("Import: '{}' already in watchlist — skipping", code);
                skipped++;
                continue;
            }

            GlobalWatchlistEntry entry = globalWatchlistClient.getEntry(code);
            if (entry == null) {
                logger.info("Import: '{}' not in global watchlist — triggering add", code);
                entry = globalWatchlistClient.addCompany(code);
            }

            if (entry == null) {
                logger.warn("Import: failed to resolve price data for '{}' — skipping", code);
                failed++;
                failedCodes.add(code);
                continue;
            }

            Watchlist entity = new Watchlist();
            entity.setUserId(DEFAULT_USER_ID);
            entity.setCompanyCode(code);
            entity.setCurrentValue(entry.getCurrentValue());
            entity.setWeek52Low(entry.getWeek52Low());
            entity.setWeek52High(entry.getWeek52High());
            entity.setAllTimeLow(entry.getAllTimeLow());
            entity.setAllTimeHigh(entry.getAllTimeHigh());
            entity.setTradedVolume(entry.getTradedVolume());
            watchlistRepository.save(entity);
            imported++;
            logger.info("Import: '{}' added to watchlist", code);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("failed", failed);
        if (!failedCodes.isEmpty()) result.put("failedCodes", failedCodes);
        logger.info("Import complete — imported={}, skipped={}, failed={}", imported, skipped, failed);
        return result;
    }

    /**
     * Returns the total count of entries in the current user's watchlist.
     *
     * @return the number of watchlist entries
     */
    public long getCount() {
        long count = watchlistRepository.countByUserId(DEFAULT_USER_ID);
        logger.debug("Watchlist count for userId={}: {}", DEFAULT_USER_ID, count);
        return count;
    }

    /**
     * Converts a {@link Watchlist} entity to a {@link WatchlistView} DTO.
     *
     * <p>Performs a lookup in {@code company_master} to populate the human-readable
     * company name. If no match is found, {@code companyName} remains {@code null}.
     *
     * @param entity the watchlist entity to convert
     * @return the corresponding view DTO
     */
    private WatchlistView toView(Watchlist entity) {
        WatchlistView view = new WatchlistView();
        view.setCompanyCode(entity.getCompanyCode());

        companyRepository.findBySymbol(entity.getCompanyCode())
                .ifPresent(cm -> view.setCompanyName(cm.getCompanyName()));

        view.setWeek52Low(entity.getWeek52Low());
        view.setWeek52High(entity.getWeek52High());
        view.setAllTimeLow(entity.getAllTimeLow());
        view.setAllTimeHigh(entity.getAllTimeHigh());
        view.setCurrentValue(entity.getCurrentValue());
        view.setTradedVolume(entity.getTradedVolume());
        return view;
    }
}
