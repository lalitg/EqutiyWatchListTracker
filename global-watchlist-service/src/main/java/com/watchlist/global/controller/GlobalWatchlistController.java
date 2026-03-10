package com.watchlist.global.controller;

import com.watchlist.global.service.GlobalWatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/global-watchlist")
public class GlobalWatchlistController {

    private final GlobalWatchlistService service;

    public GlobalWatchlistController(GlobalWatchlistService service) {
        this.service = service;
    }

    @PostMapping("/add/{symbol}")
    public ResponseEntity<String> add(@PathVariable String symbol) {
        boolean added = service.addSymbol(symbol.toUpperCase().trim());
        if (added) return ResponseEntity.ok("Added " + symbol + " to global watchlist");
        return ResponseEntity.ok("Symbol " + symbol + " already exists or not found");
    }
}
