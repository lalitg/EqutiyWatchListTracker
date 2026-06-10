package com.watchlist.global.controller;

import com.watchlist.global.client.NseClient;
import com.watchlist.global.dto.AddCompanyRequest;
import com.watchlist.global.exception.CompanyNotFoundException;
import com.watchlist.global.model.CompanyMembershipsResponse;
import com.watchlist.global.model.CompanyMembershipsResponse.IndexLabel;
import com.watchlist.global.model.GlobalIndexEntry;
import com.watchlist.global.model.GlobalWatchlistEntry;
import com.watchlist.global.model.IndexCompanyEntry;
import com.watchlist.global.model.IndexSummary;
import com.watchlist.global.service.DomesticIndexService;
import com.watchlist.global.service.GlobalIndexService;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private final GlobalIndexService     globalIndexService;
    private final DomesticIndexService   domesticIndexService;

    public GlobalWatchlistController(GlobalWatchlistService service,
                                     GlobalIndexService globalIndexService,
                                     DomesticIndexService domesticIndexService) {
        this.service              = service;
        this.globalIndexService   = globalIndexService;
        this.domesticIndexService = domesticIndexService;
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
     * Returns all entries flagged as NIFTY 50 from the in-memory cache.
     *
     * @return {@code 200 OK} with a list of {@link GlobalWatchlistEntry} objects
     */
    @GetMapping("/nifty50")
    public ResponseEntity<List<GlobalWatchlistEntry>> getNifty50() {
        logger.debug("GET /api/global-watchlist/nifty50");
        return ResponseEntity.ok(service.getNifty50());
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

    // -------------------------------------------------------------------------
    // Global indices (Yahoo Finance)
    // -------------------------------------------------------------------------

    @GetMapping("/global-indices")
    public ResponseEntity<Map<String, List<GlobalIndexEntry>>> getGlobalIndices() {
        logger.debug("GET /api/global-watchlist/global-indices");
        return ResponseEntity.ok(globalIndexService.getGroupedByRegion());
    }

    // -------------------------------------------------------------------------
    // Domestic broad-market indices (NSE)
    // -------------------------------------------------------------------------

    @GetMapping("/domestic-indices")
    public ResponseEntity<List<IndexSummary>> getDomesticIndices() {
        logger.debug("GET /api/global-watchlist/domestic-indices");
        return ResponseEntity.ok(domesticIndexService.getAllDomesticIndices());
    }

    @GetMapping("/domestic-indices/{indexKey}/companies")
    public ResponseEntity<List<IndexCompanyEntry>> getDomesticIndexCompanies(@PathVariable String indexKey) {
        logger.info("GET /api/global-watchlist/domestic-indices/{}/companies", indexKey);
        String nseParam = domesticIndexService.resolveNseParam(indexKey);
        return ResponseEntity.ok(domesticIndexService.getIndexCompanies(nseParam));
    }

    // -------------------------------------------------------------------------
    // Sector indices (NSE)
    // -------------------------------------------------------------------------

    @GetMapping("/sector-indices")
    public ResponseEntity<List<IndexSummary>> getSectorIndices() {
        logger.debug("GET /api/global-watchlist/sector-indices");
        return ResponseEntity.ok(domesticIndexService.getAllSectorIndices());
    }

    @GetMapping("/sector-indices/{sectorKey}/companies")
    public ResponseEntity<List<IndexCompanyEntry>> getSectorCompanies(@PathVariable String sectorKey) {
        logger.info("GET /api/global-watchlist/sector-indices/{}/companies", sectorKey);
        String nseParam = domesticIndexService.resolveNseParam(sectorKey);
        return ResponseEntity.ok(domesticIndexService.getIndexCompanies(nseParam));
    }

    // -------------------------------------------------------------------------
    // Company detail
    // -------------------------------------------------------------------------

    @GetMapping("/company/{symbol}")
    public ResponseEntity<GlobalWatchlistEntry> getCompanyDetail(@PathVariable String symbol) {
        String upperSymbol = symbol.toUpperCase();
        logger.debug("GET /api/global-watchlist/company/{}", upperSymbol);
        GlobalWatchlistEntry entry = service.getEntry(upperSymbol);
        if (entry == null) {
            logger.info("Company '{}' not in cache — fetching live from NSE", upperSymbol);
            entry = service.addCompany(upperSymbol);
        }
        return ResponseEntity.ok(entry);
    }

    /**
     * Returns the company name and all NSE index/sector memberships for a symbol.
     * Uses the precomputed reverse map built from constituent lists during each
     * index refresh — avoids calling the unreliable per-symbol NSE quote-equity API.
     */
    @GetMapping("/company/{symbol}/memberships")
    public ResponseEntity<CompanyMembershipsResponse> getCompanyMemberships(@PathVariable String symbol) {
        String upperSymbol = symbol.toUpperCase();
        logger.info("GET /api/global-watchlist/company/{}/memberships", upperSymbol);

        List<IndexLabel> labels = domesticIndexService.getSymbolMemberships(upperSymbol);

        GlobalWatchlistEntry entry = service.getEntry(upperSymbol);
        String companyName = (entry != null) ? entry.getCompanyName() : null;

        logger.info("Memberships for '{}': {} labels found", upperSymbol, labels.size());
        return ResponseEntity.ok(new CompanyMembershipsResponse(upperSymbol, companyName, labels));
    }
}
