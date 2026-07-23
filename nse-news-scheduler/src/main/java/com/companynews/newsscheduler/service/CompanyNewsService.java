package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer for single-keyword company news lookups ({@code GET /api/news?key=}).
 *
 * <p>Wraps the on-demand fetch logic from the controller. No result caching — every call
 * reads straight from the database via {@link CompanyNewsRepository}, or, on a miss,
 * triggers an on-demand fetch. This endpoint is for company symbols and sector names, which
 * never go through {@link KeywordNewsBucketCache} — that cache is scoped to keywords.txt-
 * sourced keywords only (Domestic/Global tabs).
 */
@Service
public class CompanyNewsService {

    private static final Logger log = LogManager.getLogger(CompanyNewsService.class);

    private final CompanyNewsRepository repository;
    private final NseFetcher nseFetcher;
    private final RssFetcher rssFetcher;
    private final NewsWorker newsWorker;
    private final SeqIdWindow seqIdWindow;
    private final UrlWindow urlWindow;
    private final KeywordLoader keywordLoader;

    /** Latest News window in days — items within this age appear on the Latest tab. */
    @Value("${news.retention.company.latest-window-days:7}")
    private int latestWindowDays;

    /** Important News window in days — flagged items within this age appear on the Important tab. */
    @Value("${news.retention.company.important-window-days:90}")
    private int importantWindowDays;

    /** Floor count so a quiet company's Latest tab is never empty (mirrors cleanup's floor). */
    @Value("${news.retention.min-count:15}")
    private int minCount;

    public CompanyNewsService(CompanyNewsRepository repository,
                              NseFetcher nseFetcher,
                              RssFetcher rssFetcher,
                              NewsWorker newsWorker,
                              SeqIdWindow seqIdWindow,
                              UrlWindow urlWindow,
                              KeywordLoader keywordLoader) {
        this.repository    = repository;
        this.nseFetcher    = nseFetcher;
        this.rssFetcher    = rssFetcher;
        this.newsWorker    = newsWorker;
        this.seqIdWindow   = seqIdWindow;
        this.urlWindow     = urlWindow;
        this.keywordLoader = keywordLoader;
    }

    /**
     * Returns the news response for the given keyword.
     *
     * <ol>
     *   <li>Reads from DB — returns immediately if a row exists.</li>
     *   <li>On DB miss: triggers on-demand NSE + RSS fetch, saves, then reads back.</li>
     *   <li>If still no data: returns an empty response (never throws).</li>
     * </ol>
     *
     * @param key the keyword to look up (company symbol, sector name, or macro term)
     * @return map with {@code keyword}, {@code sentiments}, {@code news}, {@code lastUpdated}
     */
    public Map<String, Object> getNews(String key) {
        log.debug("Fetching news for key={}", key);

        boolean isCompany = keywordLoader.loadCompanySymbols().contains(key);

        Optional<CompanyNews> existing = repository.findByKeyword(key);
        if (existing.isPresent()) {
            log.info("DB hit for key={}", key);
            return buildResponse(existing.get(), isCompany);
        }

        log.info("DB miss — on-demand fetch for key={}", key);

        List<NseAnnouncement> nseAnnouncements = nseFetcher.fetch();
        List<NewsItem> nseMatching = nseAnnouncements.stream()
            .filter(a -> key.equalsIgnoreCase(a.getNewsItem().getSymbol()))
            .filter(a -> {
                if (seqIdWindow.contains(a.getSeqId())) return false;
                seqIdWindow.add(a.getSeqId());
                return true;
            })
            .map(NseAnnouncement::getNewsItem)
            .toList();

        if (!nseMatching.isEmpty()) {
            newsWorker.saveNews(key, nseMatching, isCompany);
        }

        List<NewsItem> googleItems = rssFetcher.fetch(key);
        List<NewsItem> googleNew = googleItems.stream()
            .filter(item -> urlWindow.addIfAbsent(item.getLink()))
            .toList();

        if (!googleNew.isEmpty()) {
            newsWorker.saveNews(key, googleNew, isCompany);
        }

        Optional<CompanyNews> saved = repository.findByKeyword(key);
        if (saved.isPresent()) {
            return buildResponse(saved.get(), isCompany);
        }

        log.warn("No news found for key={} from any source", key);
        return buildEmptyResponse(key, isCompany);
    }

    /**
     * Builds the API response for a keyword.
     *
     * <p>For sector/macro keywords ({@code isCompany == false}) the response is unchanged from
     * the original single-list shape — {@code news} carries the full stored list. For company
     * keywords the stored list (which cleanup retains as a superset: 7-day latest + 90-day
     * important) is split into two views:
     * <ul>
     *   <li>{@code news} — the Latest tab: items within the {@code latest-window-days} window;
     *       if fewer than {@code min-count}, floored with the newest items overall so the tab
     *       is never empty for quiet companies (same floor semantics as cleanup).</li>
     *   <li>{@code importantNews} — the Important tab: items flagged {@code category="important"}
     *       within {@code important-window-days}, newest-first.</li>
     * </ul>
     * The two lists intentionally overlap: a recent important article appears in both tabs.
     */
    private Map<String, Object> buildResponse(CompanyNews companyNews, boolean isCompany) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     companyNews.getKeyword());
        response.put("sentiments",  companyNews.getSentiments() != null ? companyNews.getSentiments() : "");
        response.put("lastUpdated", companyNews.getLastUpdated());

        List<NewsItem> all = companyNews.getNews() != null ? companyNews.getNews() : List.of();

        if (!isCompany) {
            // Sectors/macro keywords: unchanged single-list response.
            response.put("news", companyNews.getNews());
            return response;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        ZonedDateTime latestCutoff    = now.minusDays(latestWindowDays);
        ZonedDateTime importantCutoff = now.minusDays(importantWindowDays);

        List<NewsItem> latest    = new ArrayList<>();
        List<NewsItem> important = new ArrayList<>();

        for (NewsItem item : all) {
            ZonedDateTime date = NewsDateParser.parse(item.getDate());
            boolean withinLatest    = date == null || date.isAfter(latestCutoff);
            boolean withinImportant = date == null || date.isAfter(importantCutoff);
            boolean isImportant = NewsImportanceClassifier.CATEGORY_IMPORTANT.equals(item.getCategory());

            if (withinLatest) {
                latest.add(item);
            }
            if (isImportant && withinImportant) {
                important.add(item);
            }
        }

        // Floor: if too few items in the Latest window, pad with the newest items overall
        // (list is stored newest-first) so a quiet company's Latest tab is never empty.
        if (latest.size() < minCount) {
            latest = new ArrayList<>(all.subList(0, Math.min(minCount, all.size())));
        }

        response.put("news",          latest);
        response.put("importantNews", important);
        return response;
    }

    private Map<String, Object> buildEmptyResponse(String key, boolean isCompany) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     key);
        response.put("sentiments",  "");
        response.put("news",        List.of());
        if (isCompany) {
            response.put("importantNews", List.of());
        }
        response.put("lastUpdated", null);
        return response;
    }
}
