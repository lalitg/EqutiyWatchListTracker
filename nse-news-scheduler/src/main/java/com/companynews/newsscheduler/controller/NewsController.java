package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.service.NseFetcher;
import com.companynews.newsscheduler.service.RssFetcher;
import com.companynews.newsscheduler.service.SeqIdWindow;
import com.companynews.newsscheduler.service.UrlWindow;
import com.companynews.newsscheduler.service.NewsWorker;
import jakarta.validation.constraints.NotBlank;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller exposing the news query API.
 *
 * <p>Provides a single endpoint {@code GET /api/news?key=} that:
 * <ol>
 *   <li>Returns cached data from the database if available (fastest path).</li>
 *   <li>Falls back to on-demand NSE and Google RSS fetches if no cache entry exists.</li>
 *   <li>Returns an empty response if no data is found from any source.</li>
 * </ol>
 *
 * <p>{@code @Validated} at the class level activates Spring's method-level constraint validation
 * so that {@code @NotBlank} on {@code @RequestParam} is enforced before the method body runs.
 * Violations are handled by {@link GlobalExceptionHandler}.
 */
@Validated
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log = LogManager.getLogger(NewsController.class);

    private final CompanyNewsRepository repository;
    private final NseFetcher nseFetcher;
    private final RssFetcher rssFetcher;
    private final NewsWorker newsWorker;
    private final SeqIdWindow seqIdWindow;
    private final UrlWindow urlWindow;

    /**
     * Constructs a {@code NewsController} with all required dependencies injected by Spring.
     *
     * @param repository   repository for reading/writing {@link CompanyNews} records
     * @param nseFetcher   fetches corporate announcements from the NSE API
     * @param rssFetcher   fetches news articles from Google RSS feeds
     * @param newsWorker   handles deduplication and persistence of news items
     * @param seqIdWindow  in-memory sliding window for NSE sequence ID deduplication
     * @param urlWindow    in-memory sliding window for URL-based deduplication
     */
    public NewsController(CompanyNewsRepository repository,
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
     * Returns news for the given keyword, fetching on-demand if not already cached in the DB.
     *
     * <p>Accepted keyword types:
     * <ul>
     *   <li>Company symbol: {@code GET /api/news?key=INFY}</li>
     *   <li>Sector name: {@code GET /api/news?key=Banking}</li>
     *   <li>Macro keyword: {@code GET /api/news?key=Nifty 50}</li>
     * </ul>
     *
     * <p>Fetch logic (applied only on cache miss):
     * <ol>
     *   <li>NSE on-demand fetch → filter by symbol → dedup by seqId → save.</li>
     *   <li>Google RSS on-demand fetch → dedup by URL window → save.</li>
     *   <li>Read back from DB and return. If still empty, return an empty response.</li>
     * </ol>
     *
     * <p>{@code @NotBlank} rejects null, empty, or whitespace-only keys before any service
     * logic runs. {@link GlobalExceptionHandler} converts the resulting
     * {@link jakarta.validation.ConstraintViolationException} into a 400 Bad Request.
     *
     * @param key the keyword to look up (must not be blank)
     * @return 200 OK with a JSON body containing {@code keyword}, {@code sentiments},
     *         {@code news} (list), and {@code lastUpdated}; {@code news} is empty if not found
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNews(
            @RequestParam @NotBlank(message = "key must not be blank") String key) {

        log.info("GET /api/news requested for key={}", key);

        Optional<CompanyNews> existing = repository.findByKeyword(key);
        if (existing.isPresent()) {
            log.info("Cache hit — returning DB data for key={}", key);
            return ResponseEntity.ok(buildResponse(existing.get()));
        }

        log.info("Cache miss — fetching on-demand for key={}", key);

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
            log.info("NSE on-demand: {} matching items found for key={}", nseMatching.size(), key);
            newsWorker.saveNews(key, nseMatching);
        }

        List<NewsItem> googleItems = rssFetcher.fetch(key);
        List<NewsItem> googleNew = googleItems.stream()
            .filter(item -> urlWindow.addIfAbsent(item.getLink()))
            .toList();

        if (!googleNew.isEmpty()) {
            log.info("Google RSS on-demand: {} new items found for key={}", googleNew.size(), key);
            newsWorker.saveNews(key, googleNew);
        }

        Optional<CompanyNews> saved = repository.findByKeyword(key);
        if (saved.isPresent()) {
            return ResponseEntity.ok(buildResponse(saved.get()));
        }

        log.warn("No news found for key={} from any source", key);
        return ResponseEntity.ok(buildEmptyResponse(key));
    }

    /**
     * Builds a populated response map from a {@link CompanyNews} record.
     *
     * @param companyNews the persisted news record to serialize
     * @return a map with {@code keyword}, {@code sentiments}, {@code news}, and {@code lastUpdated}
     */
    private Map<String, Object> buildResponse(CompanyNews companyNews) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     companyNews.getKeyword());
        response.put("sentiments",  companyNews.getSentiments() != null ? companyNews.getSentiments() : "");
        response.put("news",        companyNews.getNews());
        response.put("lastUpdated", companyNews.getLastUpdated());
        return response;
    }

    /**
     * Builds an empty response map for keywords that have no news from any source.
     *
     * @param key the keyword that returned no results
     * @return a map with {@code keyword}, empty {@code sentiments}, empty {@code news} list,
     *         and {@code null} for {@code lastUpdated}
     */
    private Map<String, Object> buildEmptyResponse(String key) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     key);
        response.put("sentiments",  "");
        response.put("news",        List.of());
        response.put("lastUpdated", null);
        return response;
    }
}
