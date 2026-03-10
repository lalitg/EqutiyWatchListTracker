package com.watchlist.global.seeder;

import com.watchlist.global.entity.GlobalWatchlist;
import com.watchlist.global.entity.Watchlist;
import com.watchlist.global.repository.CompanyRepository;
import com.watchlist.global.repository.GlobalWatchlistRepository;
import com.watchlist.global.repository.WatchlistRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Seeds the global_watchlist table on startup with:
 * 1. All Nifty 50 companies from NSE bulk endpoint (default universe)
 * 2. Any companies already present in user watchlists (backfill)
 */
@Component
public class GlobalWatchlistSeeder {

    private static final Logger logger = LoggerFactory.getLogger(GlobalWatchlistSeeder.class);
    private static final String NIFTY50_URL =
            "https://www.nseindia.com/api/equity-stockIndices?index=NIFTY%2050";

    private final GlobalWatchlistRepository globalWatchlistRepository;
    private final CompanyRepository companyRepository;
    private final WatchlistRepository watchlistRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public GlobalWatchlistSeeder(
            GlobalWatchlistRepository globalWatchlistRepository,
            CompanyRepository companyRepository,
            WatchlistRepository watchlistRepository) {

        this.globalWatchlistRepository = globalWatchlistRepository;
        this.companyRepository         = companyRepository;
        this.watchlistRepository       = watchlistRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seedNifty50();
        backfillFromWatchlist();
    }

    private void seedNifty50() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://www.nseindia.com");

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    NIFTY50_URL, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

            JsonNode body = response.getBody();
            if (body == null || !body.has("data")) {
                logger.warn("Nifty 50 seed: no data returned from NSE");
                return;
            }

            int inserted = 0;
            for (JsonNode stock : body.get("data")) {
                String symbol = stock.get("symbol").asText();
                if (insertIfAbsent(symbol)) inserted++;
            }
            logger.info("Nifty 50 seed complete — {} new symbols inserted", inserted);

        } catch (Exception e) {
            logger.error("Nifty 50 seed failed: {}", e.getMessage());
        }
    }

    private void backfillFromWatchlist() {
        List<Watchlist> entries = watchlistRepository.findAll();
        int inserted = 0;
        for (Watchlist entry : entries) {
            if (insertIfAbsent(entry.getCompanyCode())) inserted++;
        }
        logger.info("Watchlist backfill complete — {} new symbols inserted", inserted);
    }

    private boolean insertIfAbsent(String symbol) {
        if (globalWatchlistRepository.existsBySymbol(symbol)) return false;

        return companyRepository.findBySymbol(symbol).map(company -> {
            globalWatchlistRepository.save(new GlobalWatchlist(company.getId(), symbol));
            return true;
        }).orElseGet(() -> {
            logger.warn("Symbol {} not found in company_master — skipping", symbol);
            return false;
        });
    }
}
