package com.equity.watchlist.controller;

import com.equity.watchlist.dto.WatchlistRequest;
import com.equity.watchlist.dto.WatchlistView;
import com.equity.watchlist.service.WatchlistService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing watchlist CRUD endpoints.
 *
 * <p>All endpoints are prefixed with {@code /api/v1/watchlist}.
 * Each operation delegates to {@link WatchlistService} for business logic.
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private static final Logger logger = LogManager.getLogger(WatchlistController.class);

    private final WatchlistService watchlistService;

    /**
     * Constructs the controller with the required service dependency.
     *
     * @param watchlistService the service handling watchlist business logic
     */
    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * Adds a new company to the watchlist.
     * Fetches live price data from global-watchlist-service before persisting.
     *
     * @param request the request body containing the company code and optional price fields
     * @return {@code 201 Created} with the saved {@link WatchlistView}
     */
    @PostMapping({"", "/addCompany"})
    public ResponseEntity<WatchlistView> addCompany(@RequestBody WatchlistRequest request) {
        logger.info("POST /api/v1/watchlist — adding company '{}'", request.getCompanyCode());
        WatchlistView created = watchlistService.addCompany(request);
        logger.info("Company '{}' added successfully", created.getCompanyCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns all watchlist entries for the current user, ordered by creation time.
     *
     * @return {@code 200 OK} with a list of {@link WatchlistView} entries
     */
    @GetMapping({"", "/getAllCompanies"})
    public ResponseEntity<List<WatchlistView>> getWatchlist() {
        logger.info("GET /api/v1/watchlist — fetching all entries");
        List<WatchlistView> entries = watchlistService.getWatchlist();
        logger.debug("Returning {} watchlist entries", entries.size());
        return ResponseEntity.ok(entries);
    }

    /**
     * Updates the price fields of an existing watchlist entry.
     *
     * @param companyCode the NSE symbol identifying the entry to update
     * @param request     the request body with updated price fields
     * @return {@code 200 OK} with the updated {@link WatchlistView}
     */
    @PutMapping({"/{companyCode}", "/updateCompany/{companyCode}"})
    public ResponseEntity<WatchlistView> updateCompany(
            @PathVariable String companyCode,
            @RequestBody WatchlistRequest request) {
        logger.info("PUT /api/v1/watchlist/{} — updating entry", companyCode);
        WatchlistView updated = watchlistService.updateCompany(companyCode, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Removes a company from the watchlist.
     *
     * @param companyCode the NSE symbol of the entry to delete
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping({"/{companyCode}", "/deleteCompany/{companyCode}"})
    public ResponseEntity<Void> removeCompany(@PathVariable String companyCode) {
        logger.info("DELETE /api/v1/watchlist/{} — removing entry", companyCode);
        watchlistService.removeCompany(companyCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bulk-imports company codes into the watchlist, typically from a CSV upload.
     * Skips duplicates and reports failed symbols that could not be resolved.
     *
     * @param body a JSON object with key {@code "companyCodes"} containing a list of NSE symbols
     * @return {@code 200 OK} with a summary map containing {@code imported}, {@code skipped},
     *         {@code failed}, and optionally {@code failedCodes}
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importCompanies(@RequestBody Map<String, List<String>> body) {
        List<String> companyCodes = body.get("companyCodes");
        if (companyCodes == null || companyCodes.isEmpty()) {
            logger.warn("POST /api/v1/watchlist/import — request body missing 'companyCodes'");
            return ResponseEntity.badRequest().build();
        }
        logger.info("POST /api/v1/watchlist/import — importing {} codes", companyCodes.size());
        Map<String, Object> result = watchlistService.importCompanies(companyCodes);
        logger.info("Import complete — result: {}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the total number of entries in the current user's watchlist.
     *
     * @return {@code 200 OK} with a map containing key {@code "count"}
     */
    @GetMapping("/getCount")
    public ResponseEntity<Map<String, Long>> getCount() {
        logger.debug("GET /api/v1/watchlist/getCount");
        long count = watchlistService.getCount();
        return ResponseEntity.ok(Map.of("count", count));
    }
}
