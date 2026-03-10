package com.companynews.newsscheduler.controller;
 
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.model.CompanyUpcomingEvents;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.repository.CompanyUpcomingEventsRepository;
import com.companynews.newsscheduler.service.ArchiveService;
import com.companynews.newsscheduler.service.GoogleRssService;
import com.companynews.newsscheduler.service.NseFetchService;
import lombok.RequiredArgsConstructor;
import com.companynews.newsscheduler.service.CompanySymbolService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
import java.util.List;
import java.util.Optional;
 
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // Allow React frontend to call this
public class NewsController {
 
    private final CompanyUpcomingEventsRepository eventsRepo;
    private final CompanyNewsRepository newsRepo;
    private final NseFetchService nseFetchService;
    private final GoogleRssService googleRssService;
    private final ArchiveService archiveService;
    private final CompanySymbolService companySymbolService;
 
    // ── Events endpoints ───────────────────────────────────────
 
    // GET /api/news/events/{symbol}
    // e.g. /api/news/events/INFY
    //      /api/news/events/SECTOR_IT
    //      /api/news/events/GLOBAL_US
    @GetMapping("/events/{symbol}")
    public ResponseEntity<?> getEvents(@PathVariable String symbol) {
        Optional<CompanyUpcomingEvents> row =
            eventsRepo.findByCompanySymbol(symbol.toUpperCase());
        return row.map(r -> ResponseEntity.ok((Object) r))
                  .orElse(ResponseEntity.ok("{}"));
    }
 
    // GET /api/news/events/all — returns all rows
    @GetMapping("/events/all")
    public ResponseEntity<List<CompanyUpcomingEvents>> getAllEvents() {
        return ResponseEntity.ok(eventsRepo.findAll());
    }
 
    // ── News endpoints ─────────────────────────────────────────
 
    // GET /api/news/company/{symbol}
    @GetMapping("/company/{symbol}")
    public ResponseEntity<?> getNews(@PathVariable String symbol) {
        Optional<CompanyNews> row =
            newsRepo.findByCompanySymbol(symbol.toUpperCase());
        return row.map(r -> ResponseEntity.ok((Object) r))
                  .orElse(ResponseEntity.ok("{}"));
    }
 
    // GET /api/news/company/all
    @GetMapping("/company/all")
    public ResponseEntity<List<CompanyNews>> getAllNews() {
        return ResponseEntity.ok(newsRepo.findAll());
    }
 
    // ── Sector endpoints (domestic) ────────────────────────────
    // GET /api/news/sector/IT  → looks up symbol SECTOR_IT
    @GetMapping("/sector/{sector}")
    public ResponseEntity<?> getSectorNews(@PathVariable String sector) {
        String symbol = "SECTOR_" + sector.toUpperCase();
        Optional<CompanyNews> row = newsRepo.findByCompanySymbol(symbol);
        return row.map(r -> ResponseEntity.ok((Object) r))
                  .orElse(ResponseEntity.ok("{}"));
    }
 
    // ── Global endpoints ───────────────────────────────────────
    // GET /api/news/global/US  → looks up symbol GLOBAL_US
    @GetMapping("/global/{region}")
    public ResponseEntity<?> getGlobalNews(@PathVariable String region) {
        String symbol = "GLOBAL_" + region.toUpperCase();
        Optional<CompanyNews> row = newsRepo.findByCompanySymbol(symbol);
        return row.map(r -> ResponseEntity.ok((Object) r))
                  .orElse(ResponseEntity.ok("{}"));
    }
 
    // ── Manual trigger endpoints (for testing) ─────────────────
 
    // POST /api/news/fetch  — triggers full fetch manually
    @PostMapping("/fetch")
    public ResponseEntity<String> triggerFetch() {
        nseFetchService.fetchAll();
        googleRssService.fetchAllRssNews();
        return ResponseEntity.ok("Fetch triggered successfully");
    }
 
    // POST /api/news/archive  — manually triggers archive job
    @PostMapping("/archive")
    public ResponseEntity<String> triggerArchive() {
        archiveService.archiveExpiredEvents();
        return ResponseEntity.ok("Archive job triggered successfully");
    }

    // POST /api/news/refresh-symbols
    // Reloads the company list from the database without restarting.
    // Useful after NSE Scheduler adds new companies.
    @PostMapping("/refresh-symbols")
    public ResponseEntity<String> refreshSymbols() {
        companySymbolService.refreshSymbols();
        int count = companySymbolService.getAllSymbols().size();
        return ResponseEntity.ok(
            "Symbol list refreshed. Total symbols loaded: " + count);
    }

}
