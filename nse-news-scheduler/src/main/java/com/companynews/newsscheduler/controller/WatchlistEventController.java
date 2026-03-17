package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.service.GoogleRssService;
import com.companynews.newsscheduler.service.NseFetchService;
import com.companynews.newsscheduler.service.SeqIdWindowService;
import com.companynews.newsscheduler.service.UrlWindowService;
import com.companynews.newsscheduler.service.WorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
public class WatchlistEventController {

    private static final Logger log =
        LoggerFactory.getLogger(WatchlistEventController.class);

    private final GoogleRssService googleRssService;
    private final NseFetchService nseFetchService;
    private final WorkerService workerService;
    private final UrlWindowService urlWindowService;
    private final SeqIdWindowService seqIdWindowService;

    public WatchlistEventController(GoogleRssService googleRssService,
                                     NseFetchService nseFetchService,
                                     WorkerService workerService,
                                     UrlWindowService urlWindowService,
                                     SeqIdWindowService seqIdWindowService) {
        this.googleRssService = googleRssService;
        this.nseFetchService = nseFetchService;
        this.workerService = workerService;
        this.urlWindowService = urlWindowService;
        this.seqIdWindowService = seqIdWindowService;
    }

    /**
     * POST /api/internal/watchlist/added
     * Body: { "symbol": "WIPRO" }
     *
     * Called by watchlist-service when a user adds a new company
     * to their global watchlist.
     *
     * What it does:
     * 1. Fetches Google RSS news for the new symbol immediately
     * 2. Fetches NSE announcements and filters for this symbol
     * 3. Saves whatever is found
     * 4. Returns how many items were saved
     *
     * WHY a separate controller and not in NewsController:
     * This is an internal service-to-service endpoint.
     * It is not called by the frontend or users directly.
     * Keeping it separate under /api/internal makes it clear
     * this is a backend integration endpoint.
     *
     * HOW watchlist-service calls this:
     * After inserting a new row into global_watchlist table,
     * watchlist-service makes a POST request to:
     * http://localhost:8084/api/internal/watchlist/added
     * with body { "symbol": "WIPRO" }
     */
    @PostMapping("/watchlist/added")
    public ResponseEntity<Map<String, Object>> onNewSymbolAdded(
            @RequestBody Map<String, String> body) {

        String rawSymbol = body.get("symbol");
        if (rawSymbol == null || rawSymbol.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "symbol is required"));
        }

        // Create a new final variable — this is what lambdas below will use
        // WHY final: Java requires variables used inside lambda expressions
        // to never be reassigned. By creating a new variable instead of
        // reassigning the old one, we satisfy this requirement.
        final String symbol = rawSymbol.trim().toUpperCase();
        log.info("New symbol added to watchlist: {} — triggering immediate fetch", symbol);

        int totalSaved = 0;

        // Step 1: Fetch from Google RSS for this symbol
        List<NewsItem> googleItems = googleRssService.fetchNews(symbol);
        List<NewsItem> googleNew = googleItems.stream()
            .filter(item -> urlWindowService.checkAndMark(item.getLink()))
            .toList();

        if (!googleNew.isEmpty()) {
            workerService.processAndSave(symbol, googleNew);
            totalSaved += googleNew.size();
            log.info("Google RSS: saved {} items for new symbol: {}", googleNew.size(), symbol);
        }

        // Step 2: Fetch from NSE and filter for this symbol
        List<NseAnnouncement> nseAnnouncements = nseFetchService.fetchAllAnnouncements();
        List<NewsItem> nseMatching = nseAnnouncements.stream()
            .filter(a -> symbol.equalsIgnoreCase(a.getNewsItem().getSymbol()))
            .filter(a -> {
                if (seqIdWindowService.isAlreadySeen(a.getSeqId())) return false;
                seqIdWindowService.markAsSeen(a.getSeqId());
                return true;
            })
            .map(NseAnnouncement::getNewsItem)
            .toList();

        if (!nseMatching.isEmpty()) {
            workerService.processAndSave(symbol, nseMatching);
            totalSaved += nseMatching.size();
            log.info("NSE: saved {} items for new symbol: {}", nseMatching.size(), symbol);
        }

        return ResponseEntity.ok(Map.of(
            "symbol", symbol,
            "itemsSaved", totalSaved,
            "message", totalSaved > 0
                ? "News fetched and saved successfully"
                : "No news found at this time — will be fetched at next scheduled run"
        ));
    }
}
