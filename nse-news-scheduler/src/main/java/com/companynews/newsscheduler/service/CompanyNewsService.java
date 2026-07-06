package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer for single-keyword company news lookups ({@code GET /api/news?key=}).
 *
 * <p>Wraps the on-demand fetch logic from the controller and adds an LRU cache
 * ({@code companyNews}) via {@code @Cacheable}. The cache holds up to 100 symbols;
 * Caffeine evicts the least-recently-used entry when the 101st distinct symbol is requested.
 *
 * <p>The cache entry for a keyword is evicted by {@link NewsWorker} via
 * {@code @CacheEvict(value="companyNews", key="#keyword")} whenever new articles are
 * written for that keyword — ensuring the next request reads the fresh DB row.
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

    public CompanyNewsService(CompanyNewsRepository repository,
                              NseFetcher nseFetcher,
                              RssFetcher rssFetcher,
                              NewsWorker newsWorker,
                              SeqIdWindow seqIdWindow,
                              UrlWindow urlWindow) {
        this.repository  = repository;
        this.nseFetcher  = nseFetcher;
        this.rssFetcher  = rssFetcher;
        this.newsWorker  = newsWorker;
        this.seqIdWindow = seqIdWindow;
        this.urlWindow   = urlWindow;
    }

    /**
     * Returns the news response for the given keyword, caching the result in the
     * {@code companyNews} LRU cache (max 100 entries).
     *
     * <p>On a cache miss:
     * <ol>
     *   <li>Reads from DB — returns immediately if a row exists.</li>
     *   <li>On DB miss: triggers on-demand NSE + RSS fetch, saves, then reads back.</li>
     *   <li>If still no data: returns an empty response (never throws).</li>
     * </ol>
     *
     * @param key the keyword to look up (company symbol, sector name, or macro term)
     * @return map with {@code keyword}, {@code sentiments}, {@code news}, {@code lastUpdated}
     */
    @Cacheable(value = "companyNews", key = "#key")
    public Map<String, Object> getNews(String key) {
        log.debug("companyNews cache MISS — fetching for key={}", key);

        Optional<CompanyNews> existing = repository.findByKeyword(key);
        if (existing.isPresent()) {
            log.info("DB hit for key={}", key);
            return buildResponse(existing.get());
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
            newsWorker.saveNews(key, nseMatching);
        }

        List<NewsItem> googleItems = rssFetcher.fetch(key);
        List<NewsItem> googleNew = googleItems.stream()
            .filter(item -> urlWindow.addIfAbsent(item.getLink()))
            .toList();

        if (!googleNew.isEmpty()) {
            newsWorker.saveNews(key, googleNew);
        }

        Optional<CompanyNews> saved = repository.findByKeyword(key);
        if (saved.isPresent()) {
            return buildResponse(saved.get());
        }

        log.warn("No news found for key={} from any source", key);
        return buildEmptyResponse(key);
    }

    private Map<String, Object> buildResponse(CompanyNews companyNews) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     companyNews.getKeyword());
        response.put("sentiments",  companyNews.getSentiments() != null ? companyNews.getSentiments() : "");
        response.put("news",        companyNews.getNews());
        response.put("lastUpdated", companyNews.getLastUpdated());
        return response;
    }

    private Map<String, Object> buildEmptyResponse(String key) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     key);
        response.put("sentiments",  "");
        response.put("news",        List.of());
        response.put("lastUpdated", null);
        return response;
    }
}
