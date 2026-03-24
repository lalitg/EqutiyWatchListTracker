package com.watchlist.global.controller;

import com.watchlist.global.dto.AddCompanyRequest;
import com.watchlist.global.exception.CompanyNotFoundException;
import com.watchlist.global.model.GlobalWatchlistEntry;
import com.watchlist.global.service.GlobalWatchlistService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * REST controller exposing the global watchlist endpoints.
 *
 * <p>All endpoints are prefixed with {@code /api/global-watchlist}.
 * Data is served from the in-memory {@link java.util.concurrent.ConcurrentHashMap}
 * maintained by {@link GlobalWatchlistService} — DB is only hit during startup
 * and market-close persistence.
 */
@RestController
@RequestMapping("/api/global-watchlist")
public class GlobalWatchlistController {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistController.class);

    private final GlobalWatchlistService service;

    /**
     * Constructs the controller with the required service dependency.
     *
     * @param service the service managing the in-memory price cache
     */
    public GlobalWatchlistController(GlobalWatchlistService service) {
        this.service = service;
    }

    /**
     * Returns all entries in the global watchlist from the in-memory cache.
     *
     * @return {@code 200 OK} with a collection of {@link GlobalWatchlistEntry} objects
     */
    @GetMapping
    public ResponseEntity<Collection<GlobalWatchlistEntry>> getAll() {
        logger.debug("GET /api/global-watchlist — returning {} entries", service.getAll().size());
        return ResponseEntity.ok(service.getAll());
    }

    /**
     * Returns a single company entry from the in-memory cache.
     *
     * @param companyCode the NSE symbol to look up (e.g. {@code "INFY"})
     * @return {@code 200 OK} with the entry, or {@code 404 Not Found} if not tracked
     */
    @GetMapping("/{companyCode}")
    public ResponseEntity<GlobalWatchlistEntry> getOne(@PathVariable String companyCode) {
        logger.debug("GET /api/global-watchlist/{}", companyCode);
        GlobalWatchlistEntry entry = service.getEntry(companyCode);
        if (entry == null) {
            logger.debug("Company '{}' not found in global watchlist", companyCode);
            throw new CompanyNotFoundException(companyCode);
        }
        return ResponseEntity.ok(entry);
    }

    /**
     * Returns only the current price for a given company.
     * Intended for lightweight inter-service price lookups.
     *
     * @param companyCode the NSE symbol to look up
     * @return {@code 200 OK} with a map containing {@code "currentPrice"},
     *         or {@code 404 Not Found} if not tracked
     */
    @GetMapping("/{companyCode}/price")
    public ResponseEntity<Map<String, BigDecimal>> getPrice(@PathVariable String companyCode) {
        logger.debug("GET /api/global-watchlist/{}/price", companyCode);
        GlobalWatchlistEntry entry = service.getEntry(companyCode);
        if (entry == null) {
            logger.debug("Company '{}' not found in global watchlist for price lookup", companyCode);
            throw new CompanyNotFoundException(companyCode);
        }
        return ResponseEntity.ok(Map.of("currentPrice", entry.getCurrentValue()));
    }

    /**
     * Adds a company to the global watchlist.
     *
     * <p>Called by watchlist-service when a user adds a company not yet tracked globally.
     * Fetches live price data directly from NSE, persists to DB, adds to the
     * in-memory cache, and returns the populated entry.
     *
     * <p>The request body is validated before the method is invoked — a blank or missing
     * {@code companyCode} results in a {@code 400 Bad Request} with field-level error details.
     *
     * @param request the validated request body containing the NSE symbol
     * @return {@code 201 Created} with the new {@link GlobalWatchlistEntry}
     */
    @PostMapping("/add")
    public ResponseEntity<GlobalWatchlistEntry> addCompany(@Valid @RequestBody AddCompanyRequest request) {
        String companyCode = request.getCompanyCode().toUpperCase();
        logger.info("POST /api/global-watchlist/add — adding company '{}'", companyCode);
        GlobalWatchlistEntry entry = service.addCompany(companyCode);
        logger.info("Company '{}' added to global watchlist", companyCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }
}
