package com.watchlist.global.controller;

import com.watchlist.global.dto.AddCompanyRequest;
import com.watchlist.global.exception.CompanyNotFoundException;
import com.watchlist.global.model.GlobalIndexEntry;
import com.watchlist.global.model.GlobalWatchlistEntry;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/global-watchlist")
public class GlobalWatchlistController {

    private static final Logger logger = LogManager.getLogger(GlobalWatchlistController.class);

    private final GlobalWatchlistService service;
    private final GlobalIndexService     globalIndexService;

    public GlobalWatchlistController(GlobalWatchlistService service,
                                     GlobalIndexService globalIndexService) {
        this.service            = service;
        this.globalIndexService = globalIndexService;
    }

    @GetMapping
    public ResponseEntity<Collection<GlobalWatchlistEntry>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{companyCode}")
    public ResponseEntity<GlobalWatchlistEntry> getOne(@PathVariable String companyCode) {
        GlobalWatchlistEntry entry = service.getEntry(companyCode);
        if (entry == null) throw new CompanyNotFoundException(companyCode);
        return ResponseEntity.ok(entry);
    }

    @GetMapping("/{companyCode}/price")
    public ResponseEntity<Map<String, BigDecimal>> getPrice(@PathVariable String companyCode) {
        GlobalWatchlistEntry entry = service.getEntry(companyCode);
        if (entry == null) throw new CompanyNotFoundException(companyCode);
        return ResponseEntity.ok(Map.of("currentPrice", entry.getCurrentValue()));
    }

    @GetMapping("/nifty50")
    public ResponseEntity<List<GlobalWatchlistEntry>> getNifty50() {
        return ResponseEntity.ok(service.getNifty50());
    }

    @PostMapping("/add")
    public ResponseEntity<GlobalWatchlistEntry> addCompany(@Valid @RequestBody AddCompanyRequest request) {
        String companyCode = request.getCompanyCode().toUpperCase();
        logger.info("POST /api/global-watchlist/add — adding company '{}'", companyCode);
        GlobalWatchlistEntry entry = service.addCompany(companyCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @GetMapping("/global-indices")
    public ResponseEntity<Map<String, List<GlobalIndexEntry>>> getGlobalIndices() {
        return ResponseEntity.ok(globalIndexService.getGroupedByRegion());
    }

    @GetMapping("/company/{symbol}")
    public ResponseEntity<GlobalWatchlistEntry> getCompanyDetail(@PathVariable String symbol) {
        String upperSymbol = symbol.toUpperCase();
        GlobalWatchlistEntry entry = service.getEntry(upperSymbol);
        if (entry == null) entry = service.addCompany(upperSymbol);
        return ResponseEntity.ok(entry);
    }
}
