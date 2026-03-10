package com.companynews.newsscheduler.service;
 
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.companynews.newsscheduler.config.NewsLimitConfig;
import com.companynews.newsscheduler.dto.NewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.companynews.newsscheduler.service.CompanySymbolService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
 
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
 
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleRssService {
 
    // These two services handle saving to the database
    private final NseFetchService nseFetchService;
    private final NewsLimitConfig limitConfig;
    private final CompanySymbolService companySymbolService;
 
    private static final String GOOGLE_RSS_BASE =
        "https://news.google.com/rss/search?q=";
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
 
 
    @Value("${news.sectors:}")
    private String sectorsRaw;
 
    @Value("${news.global.regions:}")
    private String globalRegionsRaw;
 
    // ── Entry point called by the scheduler ───────────────────
    public void fetchAllRssNews() {
        fetchCompanyNews();
        fetchSectorNews();
        fetchGlobalNews();
    }
 
    // ── 1. Company-level news (e.g. query: 'Infosys') ─────────
    private void fetchCompanyNews() {
        List<String> symbols = companySymbolService.getAllSymbols();
        if (symbols.isEmpty()) {
            log.warn("No company symbols loaded. Skipping company RSS fetch.");
            return;
        }
        log.info("Fetching RSS news for {} companies...", symbols.size());
        for (String sym : symbols) {
            fetchAndSaveNewsForSymbol(sym, sym, sym);
            // Small pause between requests to avoid Google rate-limiting.
            // 500ms per company × 2700 companies = ~22 minutes total.
            // This is fine for a daily job that runs at 8 AM.
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        log.info("Company RSS fetch complete.");
    }

 
    // ── 2. Sector-level news ───────────────────────────────────
    // Reads queries from application.properties:
    // news.sector.query.IT=Indian IT sector stocks
    // ✅ Replace with these 5 simple lines:
    @Value("${news.sector.query.IT:Indian IT sector stocks}")
    private String sectorQueryIT;

    @Value("${news.sector.query.PHARMA:Indian pharma sector stocks}")
    private String sectorQueryPHARMA;

    @Value("${news.sector.query.BANKING:Indian banking sector stocks}")
    private String sectorQueryBANKING;

    @Value("${news.sector.query.AUTO:Indian auto sector stocks NSE}")
    private String sectorQueryAUTO;

    @Value("${news.sector.query.FMCG:Indian FMCG sector stocks NSE}")
    private String sectorQueryFMCG;
 
    private void fetchSectorNews() {
        fetchAndSaveNewsForSymbol("SECTOR_IT",     sectorQueryIT,     null);
        fetchAndSaveNewsForSymbol("SECTOR_PHARMA", sectorQueryPHARMA, null);
        fetchAndSaveNewsForSymbol("SECTOR_BANKING", sectorQueryBANKING, null);
        fetchAndSaveNewsForSymbol("SECTOR_AUTO",   sectorQueryAUTO,   null);
        fetchAndSaveNewsForSymbol("SECTOR_FMCG",   sectorQueryFMCG,   null);
    }
 
    // ── 3. Global region news ──────────────────────────────────
    @Value("${news.global.query.US:US stock market news}")
    private String globalQueryUS;
    @Value("${news.global.query.EUROPE:Europe stock market news}")
    private String globalQueryEUROPE;
    @Value("${news.global.query.ASIA:Asia stock market news}")
    private String globalQueryASIA;
 
    private void fetchGlobalNews() {
        fetchAndSaveNewsForSymbol("GLOBAL_US",     globalQueryUS,     null);
        fetchAndSaveNewsForSymbol("GLOBAL_EUROPE", globalQueryEUROPE, null);
        fetchAndSaveNewsForSymbol("GLOBAL_ASIA",   globalQueryASIA,   null);
    }
 
    // ── Core method: fetch RSS feed and save each item ─────────
    private void fetchAndSaveNewsForSymbol(
            String companySymbol, String query, String symbolForLimit) {
        if (query == null || query.isBlank()) return;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String feedUrl = GOOGLE_RSS_BASE + encodedQuery;
 
            // Rome library fetches and parses the RSS XML for us
            SyndFeed feed = new SyndFeedInput().build(
                new XmlReader(new URL(feedUrl)));
 
            // Each entry in the feed = one news article
            for (SyndEntry entry : feed.getEntries()) {
                String title = entry.getTitle();
                String link  = entry.getLink();
                String date  = entry.getPublishedDate() != null
                    ? entry.getPublishedDate()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(DATE_FMT)
                    : LocalDate.now().format(DATE_FMT);
 
                if (title == null || title.isBlank()) continue;
 
                NewsItem newsItem = new NewsItem(date, title, link);
 
                String sym = symbolForLimit != null ? symbolForLimit : companySymbol;
                int limit = limitConfig.getLimitFor(sym);
 
                // Save to company_news table using NseFetchService's upsert logic
                nseFetchService.upsertNews(companySymbol, newsItem, limit);
            }
 
            log.info("RSS fetched for [{}] — {} entries",
                companySymbol, feed.getEntries().size());
 
        } catch (Exception e) {
            log.warn("RSS fetch failed for [{}]: {}", companySymbol, e.getMessage());
        }
    }
}
