package com.watchlist.global.seeder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchlist.global.entity.GlobalWatchlist;
import com.watchlist.global.entity.Watchlist;
import com.watchlist.global.repository.CompanyRepository;
import com.watchlist.global.repository.GlobalWatchlistRepository;
import com.watchlist.global.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Component
public class GlobalWatchlistSeeder {

    private static final Logger logger = LoggerFactory.getLogger(GlobalWatchlistSeeder.class);

    private static final String NSE_HOME        = "https://www.nseindia.com";
    private static final String NIFTY_TOTAL_URL =
            "https://www.nseindia.com/api/equity-stockIndices?index=NIFTY%20TOTAL%20MARKET";

    private final GlobalWatchlistRepository globalWatchlistRepository;
    private final CompanyRepository companyRepository;
    private final WatchlistRepository watchlistRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        seedFromNiftyTotalMarket();
        backfillFromWatchlist();
    }

    private void seedFromNiftyTotalMarket() {
        try {
            // Cookie-aware Java HttpClient — shares cookies across all requests automatically
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .build();

            // Step 1: visit NSE homepage to establish session and collect cookies
            HttpRequest homeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(NSE_HOME))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();

            client.send(homeRequest, HttpResponse.BodyHandlers.ofString());
            logger.info("NSE session initialized — {} cookies collected",
                    cookieManager.getCookieStore().getCookies().size());

            // Step 2: call Nifty Total Market API with session cookies
            HttpRequest apiRequest = HttpRequest.newBuilder()
                    .uri(URI.create(NIFTY_TOTAL_URL))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .header("Referer", "https://www.nseindia.com")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(apiRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());

            if (!body.has("data")) {
                logger.warn("Nifty Total Market seed: no data in response. Status: {}", response.statusCode());
                return;
            }

            int inserted = 0;
            for (JsonNode stock : body.get("data")) {
                if (!stock.has("symbol")) continue;
                String symbol = stock.get("symbol").asText();
                if (symbol.contains(" ")) continue; // skip index rows like "NIFTY TOTAL MARKET"
                if (insertIfAbsent(symbol)) inserted++;
            }
            logger.info("Nifty Total Market seed complete — {} new symbols inserted", inserted);

        } catch (Exception e) {
            logger.error("Nifty Total Market seed failed: {}", e.getMessage());
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
