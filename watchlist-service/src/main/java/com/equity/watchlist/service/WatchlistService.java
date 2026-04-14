package com.equity.watchlist.service;

import com.equity.watchlist.client.GlobalWatchlistClient;
import com.equity.watchlist.client.GlobalWatchlistClient.GlobalWatchlistEntry;
import com.equity.watchlist.dto.UserWatchlistRequest;
import com.equity.watchlist.dto.UserWatchlistView;
import com.equity.watchlist.dto.WatchlistRequest;
import com.equity.watchlist.dto.WatchlistView;
import com.equity.watchlist.entity.UserWatchlist;
import com.equity.watchlist.entity.Watchlist;
import com.equity.watchlist.repository.CompanyRepository;
import com.equity.watchlist.repository.UserWatchlistRepository;
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
 * <p>Prices are NOT stored in the {@code watchlist} table. They are fetched live
 * from global-watchlist-service via {@link GlobalWatchlistClient} on every read.
 * The {@code watchlist} table is a pure membership table: which company codes
 * belong to which named watchlist ({@code user_watchlists}).
 */
@Service
public class WatchlistService {

    private static final Logger logger = LogManager.getLogger(WatchlistService.class);

    private final WatchlistRepository watchlistRepository;
    private final UserWatchlistRepository userWatchlistRepository;
    private final CompanyRepository companyRepository;
    private final GlobalWatchlistClient globalWatchlistClient;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            UserWatchlistRepository userWatchlistRepository,
                            CompanyRepository companyRepository,
                            GlobalWatchlistClient globalWatchlistClient) {
        this.watchlistRepository     = watchlistRepository;
        this.userWatchlistRepository = userWatchlistRepository;
        this.companyRepository       = companyRepository;
        this.globalWatchlistClient   = globalWatchlistClient;
    }

    // -------------------------------------------------------------------------
    // User watchlist (named watchlist) CRUD
    // -------------------------------------------------------------------------

    /**
     * Creates a new named watchlist for the current user.
     *
     * @param request the request containing the watchlist name
     * @return the created {@link UserWatchlistView}
     * @throws IllegalArgumentException if the name is blank or already exists
     */
    public UserWatchlistView createWatchlist(Long userId, UserWatchlistRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Watchlist name is required");
        }
        String name = request.getName().trim();
        if (userWatchlistRepository.existsByUserIdAndName(userId, name)) {
            throw new IllegalArgumentException("A watchlist named '" + name + "' already exists");
        }
        UserWatchlist entity = new UserWatchlist();
        entity.setUserId(userId);
        entity.setName(name);
        UserWatchlist saved = userWatchlistRepository.save(entity);
        logger.info("Created watchlist '{}' (id={}) for userId={}", name, saved.getId(), userId);
        return toUserWatchlistView(saved);
    }

    /**
     * Returns all named watchlists for the current user.
     *
     * @return list of {@link UserWatchlistView}, empty if user has no watchlists
     */
    public List<UserWatchlistView> getWatchlistsForUser(Long userId) {
        return userWatchlistRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(this::toUserWatchlistView)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a named watchlist and all its company entries.
     *
     * @param userWatchlistId the ID of the watchlist to delete
     * @throws IllegalArgumentException if the watchlist does not belong to the current user
     */
    @Transactional
    public void deleteWatchlist(Long userId, Long userWatchlistId) {
        userWatchlistRepository.findByUserIdAndId(userId, userWatchlistId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found: " + userWatchlistId));
        watchlistRepository.deleteByUserWatchlistId(userWatchlistId);
        userWatchlistRepository.deleteById(userWatchlistId);
        logger.info("Deleted watchlist id={} and all its entries", userWatchlistId);
    }

    // -------------------------------------------------------------------------
    // Company entry CRUD
    // -------------------------------------------------------------------------

    /**
     * Adds a new company to a named watchlist.
     *
     * <p>Only the company code and watchlist membership are stored. Price data is
     * fetched from global-watchlist-service to populate the response view, but is
     * NOT persisted in the {@code watchlist} table.
     *
     * @param request the add request (companyCode required; userWatchlistId optional — defaults to first watchlist)
     * @return a {@link WatchlistView} with live price data
     * @throws IllegalArgumentException if the company code is blank
     */
    public WatchlistView addCompany(Long userId, WatchlistRequest request) {
        if (request.getCompanyCode() == null || request.getCompanyCode().isBlank()) {
            throw new IllegalArgumentException("Company code is required");
        }

        String code = request.getCompanyCode().toUpperCase().trim();
        Long userWatchlistId = resolveWatchlistId(userId, request.getUserWatchlistId());
        logger.info("Adding company '{}' to watchlist id={}", code, userWatchlistId);

        if (watchlistRepository.findByUserWatchlistIdAndCompanyCode(userWatchlistId, code).isPresent()) {
            throw new IllegalArgumentException("Company '" + code + "' is already in this watchlist");
        }

        GlobalWatchlistEntry entry = globalWatchlistClient.getEntry(code);
        if (entry == null) {
            logger.info("Company '{}' not in global watchlist — triggering add", code);
            entry = globalWatchlistClient.addCompany(code);
        }

        Watchlist entity = new Watchlist();
        entity.setUserWatchlistId(userWatchlistId);
        entity.setCompanyCode(code);

        Watchlist saved = watchlistRepository.save(entity);
        logger.info("Company '{}' saved to watchlist id={} with row id={}", code, userWatchlistId, saved.getId());
        return toView(saved, entry);
    }

    /**
     * Returns all watchlist entries for a named watchlist, with live prices from global-watchlist-service.
     *
     * @param userWatchlistId the watchlist to fetch; if null, defaults to the first watchlist for the current user
     * @return list of {@link WatchlistView} with live price data
     */
    public List<WatchlistView> getWatchlist(Long userId, Long userWatchlistId) {
        Long resolvedId = resolveWatchlistId(userId, userWatchlistId);
        logger.debug("Fetching watchlist entries for userWatchlistId={}", resolvedId);

        UserWatchlist watchlist = userWatchlistRepository.findById(resolvedId).orElse(null);

        return watchlistRepository.findByUserWatchlistIdOrderByCreatedAtAsc(resolvedId)
                .stream()
                .map(entity -> {
                    GlobalWatchlistEntry entry = globalWatchlistClient.getEntry(entity.getCompanyCode());
                    WatchlistView view = toView(entity, entry);
                    if (watchlist != null) view.setWatchlistName(watchlist.getName());
                    return view;
                })
                .collect(Collectors.toList());
    }

    /**
     * Updates the company code of an existing watchlist entry.
     *
     * <p>Prices are no longer stored in the watchlist table — they are fetched live
     * on every read. This method only allows updating the company code itself.
     *
     * @param companyCode     the current NSE symbol identifying the entry
     * @param userWatchlistId the watchlist the entry belongs to (null → defaults to first)
     * @param request         the request containing the new company code
     * @return the updated {@link WatchlistView} with live price data
     * @throws IllegalArgumentException if no entry exists for the given company code
     */
    public WatchlistView updateCompany(Long userId, String companyCode, Long userWatchlistId, WatchlistRequest request) {
        Long resolvedId = resolveWatchlistId(userId, userWatchlistId);
        logger.info("Updating entry for company '{}' in watchlist id={}", companyCode, resolvedId);

        Watchlist entity = watchlistRepository
                .findByUserWatchlistIdAndCompanyCode(resolvedId, companyCode)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyCode));

        if (request.getCompanyCode() != null && !request.getCompanyCode().isBlank()) {
            entity.setCompanyCode(request.getCompanyCode().toUpperCase().trim());
        }

        Watchlist updated = watchlistRepository.save(entity);
        GlobalWatchlistEntry entry = globalWatchlistClient.getEntry(updated.getCompanyCode());
        logger.info("Company '{}' updated successfully", updated.getCompanyCode());
        return toView(updated, entry);
    }

    /**
     * Removes a company from a named watchlist.
     *
     * @param companyCode     the NSE symbol of the entry to delete
     * @param userWatchlistId the watchlist to remove from (null → defaults to first)
     */
    @Transactional
    public void removeCompany(Long userId, String companyCode, Long userWatchlistId) {
        Long resolvedId = resolveWatchlistId(userId, userWatchlistId);
        logger.info("Removing company '{}' from watchlist id={}", companyCode, resolvedId);
        watchlistRepository.findByUserWatchlistIdAndCompanyCode(resolvedId, companyCode)
                .orElseThrow(() -> new IllegalArgumentException("Company '" + companyCode + "' not found in this watchlist"));
        watchlistRepository.deleteByUserWatchlistIdAndCompanyCode(resolvedId, companyCode);
        logger.info("Company '{}' removed successfully", companyCode);
    }

    /**
     * Bulk-imports a list of company codes into a named watchlist.
     *
     * <p>Skips duplicates (already in the watchlist). No price data is stored —
     * prices are fetched live on every read.
     *
     * @param companyCodes    the list of NSE symbols to import
     * @param userWatchlistId the watchlist to import into (null → defaults to first)
     * @return summary map with keys {@code imported}, {@code skipped}, {@code failed}
     */
    public Map<String, Object> importCompanies(Long userId, List<String> companyCodes, Long userWatchlistId) {
        Long resolvedId = resolveWatchlistId(userId, userWatchlistId);
        int imported = 0, skipped = 0, failed = 0;
        List<String> failedCodes = new ArrayList<>();

        for (String raw : companyCodes) {
            String code = raw.toUpperCase().trim();
            if (code.isBlank()) continue;

            if (watchlistRepository.findByUserWatchlistIdAndCompanyCode(resolvedId, code).isPresent()) {
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
            entity.setUserWatchlistId(resolvedId);
            entity.setCompanyCode(code);
            watchlistRepository.save(entity);
            imported++;
            logger.info("Import: '{}' added to watchlist id={}", code, resolvedId);
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
     * Returns the total count of entries in a named watchlist.
     *
     * @param userWatchlistId the watchlist to count (null → defaults to first)
     * @return the number of company entries
     */
    public long getCount(Long userId, Long userWatchlistId) {
        Long resolvedId = resolveWatchlistId(userId, userWatchlistId);
        long count = watchlistRepository.countByUserWatchlistId(resolvedId);
        logger.debug("Watchlist count for userWatchlistId={}: {}", resolvedId, count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a watchlist ID: if the given ID is non-null it is used as-is;
     * otherwise the first watchlist for the default user is returned.
     * If no watchlist exists yet, a default one is created.
     *
     * @param userWatchlistId the requested watchlist ID, may be null
     * @return a valid watchlist ID
     */
    private Long resolveWatchlistId(Long userId, Long userWatchlistId) {
        if (userWatchlistId != null) return userWatchlistId;
        return userWatchlistRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .map(UserWatchlist::getId)
                .orElseGet(() -> {
                    logger.info("No watchlist found for userId={} — creating default", userId);
                    UserWatchlist defaultList = new UserWatchlist();
                    defaultList.setUserId(userId);
                    defaultList.setName("My Watchlist");
                    return userWatchlistRepository.save(defaultList).getId();
                });
    }

    /**
     * Converts a {@link Watchlist} entity and a live {@link GlobalWatchlistEntry} to a {@link WatchlistView}.
     *
     * @param entity the watchlist membership entity
     * @param entry  live price data from global-watchlist-service; may be null
     * @return the populated view DTO
     */
    private WatchlistView toView(Watchlist entity, GlobalWatchlistEntry entry) {
        WatchlistView view = new WatchlistView();
        view.setCompanyCode(entity.getCompanyCode());
        view.setUserWatchlistId(entity.getUserWatchlistId());

        companyRepository.findBySymbol(entity.getCompanyCode())
                .ifPresent(cm -> view.setCompanyName(cm.getCompanyName()));

        if (entry != null) {
            view.setCurrentValue(entry.getCurrentValue());
            view.setWeek52Low(entry.getWeek52Low());
            view.setWeek52High(entry.getWeek52High());
            view.setAllTimeLow(entry.getAllTimeLow());
            view.setAllTimeHigh(entry.getAllTimeHigh());
            view.setTradedVolume(entry.getTradedVolume());
            view.setPercentChange(entry.getPercentChange());
            view.setChangeValue(entry.getChangeValue());
        }
        return view;
    }

    /**
     * Converts a {@link UserWatchlist} entity to a {@link UserWatchlistView}.
     */
    private UserWatchlistView toUserWatchlistView(UserWatchlist entity) {
        UserWatchlistView view = new UserWatchlistView();
        view.setId(entity.getId());
        view.setUserId(entity.getUserId());
        view.setName(entity.getName());
        view.setCreatedAt(entity.getCreatedAt());
        return view;
    }
}
