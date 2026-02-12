package com.equity.watchlist.controller;

import com.equity.watchlist.dto.WatchlistRequest;
import com.equity.watchlist.dto.WatchlistView;
import com.equity.watchlist.service.WatchlistService;
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
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /** Adds a new company to the watchlist. */
    @PostMapping("/addCompany")
    public ResponseEntity<WatchlistView> addCompany(@RequestBody WatchlistRequest request) {
        WatchlistView created = watchlistService.addCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Returns all watchlist entries. */
    @GetMapping("/getAllCompanies")
    public ResponseEntity<List<WatchlistView>> getWatchlist() {
        return ResponseEntity.ok(watchlistService.getWatchlist());
    }

    /** Updates a watchlist entry by company code. */
    @PutMapping("/updateCompany/{companyCode}")
    public ResponseEntity<WatchlistView> updateCompany(
            @PathVariable String companyCode,
            @RequestBody WatchlistRequest request) {
        WatchlistView updated = watchlistService.updateCompany(companyCode, request);
        return ResponseEntity.ok(updated);
    }

    /** Deletes a watchlist entry by company code. */
    @DeleteMapping("/deleteCompany/{companyCode}")
    public ResponseEntity<Void> removeCompany(@PathVariable String companyCode) {
        watchlistService.removeCompany(companyCode);
        return ResponseEntity.noContent().build();
    }

    /** Returns the count of watchlist entries. */
    @GetMapping("/getCount")
    public ResponseEntity<Map<String, Long>> getCount() {
        long count = watchlistService.getCount();
        return ResponseEntity.ok(Map.of("count", count));
    }
}
