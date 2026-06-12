package com.equity.watchlist.controller;

import com.equity.watchlist.dto.UserWatchlistRequest;
import com.equity.watchlist.dto.UserWatchlistView;
import com.equity.watchlist.dto.WatchlistRequest;
import com.equity.watchlist.dto.WatchlistView;
import com.equity.watchlist.service.WatchlistService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing watchlist CRUD endpoints.
 *
 * <p>All endpoints are prefixed with {@code /api/v1/watchlist}.
 * Named watchlist management is under {@code /api/v1/watchlist/lists}.
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private static final Logger logger = LogManager.getLogger(WatchlistController.class);

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    // -------------------------------------------------------------------------
    // Named watchlist (user_watchlists) endpoints
    // -------------------------------------------------------------------------

    /**
     * Creates a new named watchlist for the current user.
     *
     * @param request body with {@code name} field
     * @return {@code 201 Created} with the created {@link UserWatchlistView}
     */
    @PostMapping("/lists")
    public ResponseEntity<UserWatchlistView> createWatchlist(Authentication authentication, @RequestBody UserWatchlistRequest request) {
        logger.info("POST /api/v1/watchlist/lists — creating watchlist '{}'", request.getName());
        Long userId = (Long) authentication.getPrincipal();
        UserWatchlistView created = watchlistService.createWatchlist(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns all named watchlists for the current user.
     *
     * @return {@code 200 OK} with a list of {@link UserWatchlistView}
     */
    @GetMapping("/lists")
    public ResponseEntity<List<UserWatchlistView>> getWatchlists(Authentication authentication) {
        logger.info("GET /api/v1/watchlist/lists — fetching all watchlists");
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(watchlistService.getWatchlistsForUser(userId));
    }

    /**
     * Deletes a named watchlist and all its company entries.
     *
     * @param id the ID of the watchlist to delete
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping("/lists/{id}")
    public ResponseEntity<Void> deleteWatchlist(Authentication authentication, @PathVariable Long id) {
        logger.info("DELETE /api/v1/watchlist/lists/{} — deleting watchlist", id);
        Long userId = (Long) authentication.getPrincipal();
        watchlistService.deleteWatchlist(userId, id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Company entry endpoints
    // -------------------------------------------------------------------------

    /**
     * Adds a new company to a named watchlist.
     * Prices are fetched live from global-watchlist-service and returned in the response
     * but are NOT stored in the watchlist table.
     *
     * @param request body with {@code companyCode} (required) and optional {@code userWatchlistId}
     * @return {@code 201 Created} with the {@link WatchlistView} including live prices
     */
    @PostMapping({"", "/addCompany"})
    public ResponseEntity<WatchlistView> addCompany(Authentication authentication, @RequestBody WatchlistRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        logger.info("POST /api/v1/watchlist — adding company '{}' for userId={}", request.getCompanyCode(), userId);
        WatchlistView created = watchlistService.addCompany(userId, request);
        logger.info("Company '{}' added successfully", created.getCompanyCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns all company entries for a named watchlist with live prices.
     *
     * @param watchlistId optional watchlist ID (defaults to first watchlist if omitted)
     * @return {@code 200 OK} with a list of {@link WatchlistView} entries
     */
    @GetMapping({"", "/getAllCompanies"})
    public ResponseEntity<List<WatchlistView>> getWatchlist(Authentication authentication,
            @RequestParam(required = false) Long watchlistId) {
        Long userId = (Long) authentication.getPrincipal();
        logger.info("GET /api/v1/watchlist — fetching all entries for userId={}, watchlistId={}", userId, watchlistId);
        List<WatchlistView> entries = watchlistService.getWatchlist(userId, watchlistId);
        logger.debug("Returning {} watchlist entries", entries.size());
        return ResponseEntity.ok(entries);
    }

    /**
     * Updates the company code of an existing watchlist entry.
     *
     * @param companyCode the current NSE symbol
     * @param watchlistId optional watchlist ID (defaults to first watchlist if omitted)
     * @param request     body with optional new {@code companyCode}
     * @return {@code 200 OK} with the updated {@link WatchlistView}
     */
    @PutMapping({"/{companyCode}", "/updateCompany/{companyCode}"})
    public ResponseEntity<WatchlistView> updateCompany(Authentication authentication,
            @PathVariable String companyCode,
            @RequestParam(required = false) Long watchlistId,
            @RequestBody WatchlistRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        logger.info("PUT /api/v1/watchlist/{} — updating entry for userId={}", companyCode, userId);
        WatchlistView updated = watchlistService.updateCompany(userId, companyCode, watchlistId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Removes a company from a named watchlist.
     *
     * @param companyCode the NSE symbol of the entry to delete
     * @param watchlistId optional watchlist ID (defaults to first watchlist if omitted)
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping({"/{companyCode}", "/deleteCompany/{companyCode}"})
    public ResponseEntity<Void> removeCompany(Authentication authentication,
            @PathVariable String companyCode,
            @RequestParam(required = false) Long watchlistId) {
        Long userId = (Long) authentication.getPrincipal();
        logger.info("DELETE /api/v1/watchlist/{} — removing entry for userId={}", companyCode, userId);
        watchlistService.removeCompany(userId, companyCode, watchlistId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bulk-imports codes into a named watchlist, typically from a CSV upload.
     *
     * @param body JSON with {@code companyCodes} list, optional {@code mode} ("SYMBOL"|"ISIN"),
     *             and optional {@code userWatchlistId}
     * @return {@code 200 OK} with summary map: {@code imported}, {@code skipped}, {@code failed}, {@code failedCodes}
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importCompanies(Authentication authentication, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> companyCodes = (List<String>) body.get("companyCodes");
        if (companyCodes == null || companyCodes.isEmpty()) {
            logger.warn("POST /api/v1/watchlist/import — request body missing 'companyCodes'");
            return ResponseEntity.badRequest().build();
        }
        String mode = (body.containsKey("mode") && body.get("mode") != null) ? body.get("mode").toString() : "SYMBOL";
        Long userWatchlistId = (body.containsKey("userWatchlistId") && body.get("userWatchlistId") != null)
                ? Long.valueOf(body.get("userWatchlistId").toString())
                : null;
        logger.info("POST /api/v1/watchlist/import — mode={}, importing {} codes into watchlistId={}", mode, companyCodes.size(), userWatchlistId);
        Long userId = (Long) authentication.getPrincipal();
        Map<String, Object> result = watchlistService.importCompanies(userId, companyCodes, mode, userWatchlistId);
        logger.info("Import complete — result: {}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the total number of entries in a named watchlist.
     *
     * @param watchlistId optional watchlist ID (defaults to first watchlist if omitted)
     * @return {@code 200 OK} with a map containing key {@code "count"}
     */
    @GetMapping("/getCount")
    public ResponseEntity<Map<String, Long>> getCount(Authentication authentication,
            @RequestParam(required = false) Long watchlistId) {
        Long userId = (Long) authentication.getPrincipal();
        logger.debug("GET /api/v1/watchlist/getCount for userId={}, watchlistId={}", userId, watchlistId);
        long count = watchlistService.getCount(userId, watchlistId);
        return ResponseEntity.ok(Map.of("count", count));
    }
}
