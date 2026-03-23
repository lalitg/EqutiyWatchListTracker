package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.WatchlistAddedRequest;
import com.companynews.newsscheduler.service.RssFetcher;
import com.companynews.newsscheduler.service.NseFetcher;
import com.companynews.newsscheduler.service.SeqIdWindow;
import com.companynews.newsscheduler.service.UrlWindow;
import com.companynews.newsscheduler.service.NewsWorker;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal REST controller that handles events from the watchlist service.
 *
 * <p>Exposes {@code POST /api/internal/watchlist/added} which is called by the
 * watchlist microservice whenever a new company symbol is added to the global watchlist.
 * This triggers an immediate on-demand news fetch for the new symbol so the user sees
 * fresh news right away rather than waiting for the next scheduled run.
 *
 * <p>This endpoint is internal — it should not be exposed beyond the service mesh or VPC.
 * No authentication is applied here; network-level access control is assumed.
 */
@RestController
@RequestMapping("/api/internal")
public class WatchlistEventController {

    private static final Logger log = LogManager.getLogger(WatchlistEventController.class);

    private final RssFetcher rssFetcher;
    private final NseFetcher nseFetcher;
    private final NewsWorker newsWorker;
    private final UrlWindow urlWindow;
    private final SeqIdWindow seqIdWindow;

    /**
     * Constructs a {@code WatchlistEventController} with all required dependencies injected by Spring.
     *
     * @param rssFetcher   fetches news articles from Google RSS feeds
     * @param nseFetcher   fetches corporate announcements from the NSE API
     * @param newsWorker   handles deduplication and persistence of news items
     * @param urlWindow    in-memory sliding window for URL-based deduplication
     * @param seqIdWindow  in-memory sliding window for NSE sequence ID deduplication
     */
    public WatchlistEventController(RssFetcher rssFetcher,
                                     NseFetcher nseFetcher,
                                     NewsWorker newsWorker,
                                     UrlWindow urlWindow,
                                     SeqIdWindow seqIdWindow) {
        this.rssFetcher  = rssFetcher;
        this.nseFetcher  = nseFetcher;
        this.newsWorker  = newsWorker;
        this.urlWindow   = urlWindow;
        this.seqIdWindow = seqIdWindow;
    }

    /**
     * Handles a new-symbol-added event from the watchlist service.
     *
     * <p>Request: {@code POST /api/internal/watchlist/added}
     * Body: {@code { "symbol": "WIPRO" }}
     *
     * <p>Triggers an immediate on-demand fetch from both Google RSS and NSE for the given symbol.
     * {@code @Valid} on {@link WatchlistAddedRequest} ensures the symbol is non-null and non-blank
     * before this method runs. {@link GlobalExceptionHandler} converts violations to 400 Bad Request.
     *
     * @param body the request body containing the newly added company symbol
     * @return 200 OK with a JSON body containing {@code symbol}, {@code itemsSaved}, and a
     *         human-readable {@code message} describing the outcome
     */
    @PostMapping("/watchlist/added")
    public ResponseEntity<Map<String, Object>> onNewSymbolAdded(
            @Valid @RequestBody WatchlistAddedRequest body) {

        final String symbol = body.getSymbol().trim().toUpperCase();
        log.info("Watchlist event received — new symbol: {} — triggering immediate on-demand fetch", symbol);

        int totalSaved = 0;

        // Google RSS fetch for the new symbol
        List<NewsItem> googleItems = rssFetcher.fetch(symbol);
        List<NewsItem> googleNew = googleItems.stream()
            .filter(item -> urlWindow.addIfAbsent(item.getLink()))
            .toList();

        if (!googleNew.isEmpty()) {
            newsWorker.saveNews(symbol, googleNew);
            totalSaved += googleNew.size();
            log.info("Google RSS: {} items saved for new symbol: {}", googleNew.size(), symbol);
        } else {
            log.debug("Google RSS: no new items for symbol: {}", symbol);
        }

        // NSE fetch filtered for the new symbol
        List<NseAnnouncement> nseAnnouncements = nseFetcher.fetch();
        List<NewsItem> nseMatching = nseAnnouncements.stream()
            .filter(a -> symbol.equalsIgnoreCase(a.getNewsItem().getSymbol()))
            .filter(a -> {
                if (seqIdWindow.contains(a.getSeqId())) return false;
                seqIdWindow.add(a.getSeqId());
                return true;
            })
            .map(NseAnnouncement::getNewsItem)
            .toList();

        if (!nseMatching.isEmpty()) {
            newsWorker.saveNews(symbol, nseMatching);
            totalSaved += nseMatching.size();
            log.info("NSE: {} items saved for new symbol: {}", nseMatching.size(), symbol);
        } else {
            log.debug("NSE: no matching announcements for symbol: {}", symbol);
        }

        log.info("On-demand fetch complete for symbol: {} — total items saved: {}", symbol, totalSaved);

        return ResponseEntity.ok(Map.of(
            "symbol",     symbol,
            "itemsSaved", totalSaved,
            "message",    totalSaved > 0
                ? "News fetched and saved successfully"
                : "No news found at this time — will be fetched at next scheduled run"
        ));
    }
}
