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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
public class WatchlistEventController {

    private static final Logger log = LoggerFactory.getLogger(WatchlistEventController.class);

    private final RssFetcher rssFetcher;
    private final NseFetcher nseFetcher;
    private final NewsWorker newsWorker;
    private final UrlWindow urlWindow;
    private final SeqIdWindow seqIdWindow;

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
     * POST /api/internal/watchlist/added
     * Body: { "symbol": "WIPRO" }
     *
     * Called by watchlist-service when a new company is added to the global watchlist.
     * Triggers an immediate on-demand fetch so the user sees news right away instead
     * of waiting for the next scheduled run.
     *
     * @Valid on WatchlistAddedRequest ensures symbol is non-null and non-blank before
     * this method runs. GlobalExceptionHandler converts violations to 400 Bad Request.
     */
    @PostMapping("/watchlist/added")
    public ResponseEntity<Map<String, Object>> onNewSymbolAdded(
            @Valid @RequestBody WatchlistAddedRequest body) {

        final String symbol = body.getSymbol().trim().toUpperCase();
        log.info("New symbol added to watchlist: {} — triggering immediate fetch", symbol);

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
        }

        return ResponseEntity.ok(Map.of(
            "symbol",     symbol,
            "itemsSaved", totalSaved,
            "message",    totalSaved > 0
                ? "News fetched and saved successfully"
                : "No news found at this time — will be fetched at next scheduled run"
        ));
    }
}
