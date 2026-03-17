package com.watchlist.global.controller;

import com.watchlist.global.model.GlobalWatchlistEntry;
import com.watchlist.global.service.GlobalWatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/global-watchlist")
public class GlobalWatchlistController {

    private final GlobalWatchlistService service;

    public GlobalWatchlistController(GlobalWatchlistService service) {
        this.service = service;
    }

    /** Returns full global watchlist from in-memory map. */
    @GetMapping
    public ResponseEntity<Collection<GlobalWatchlistEntry>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    /** Returns a single company entry from map. Returns 404 if not tracked. */
    @GetMapping("/{companyCode}")
    public ResponseEntity<GlobalWatchlistEntry> getOne(@PathVariable String companyCode) {
        GlobalWatchlistEntry entry = service.getEntry(companyCode);
        if (entry == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(entry);
    }

    /** Returns just the current price for a company. Used by other services. */
    @GetMapping("/{companyCode}/price")
    public ResponseEntity<Map<String, BigDecimal>> getPrice(@PathVariable String companyCode) {
        GlobalWatchlistEntry entry = service.getEntry(companyCode);
        if (entry == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("currentPrice", entry.getCurrentValue()));
    }

    /**
     * Called by watchlist-service when user adds a company not yet in global watchlist.
     * Fetches price from market-data-service, saves to DB + map, returns the entry.
     */
    @PostMapping("/add")
    public ResponseEntity<GlobalWatchlistEntry> addCompany(@RequestBody Map<String, String> body) {
        String companyCode = body.get("companyCode");
        if (companyCode == null || companyCode.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        GlobalWatchlistEntry entry = service.addCompany(companyCode.toUpperCase());
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }
}
