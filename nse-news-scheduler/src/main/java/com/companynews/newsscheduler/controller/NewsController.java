package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.SentimentDto;
import com.companynews.newsscheduler.service.CompanyNewsService;
import com.companynews.newsscheduler.service.CurrentSentimentService;
import com.companynews.newsscheduler.service.NewsAggregatorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing the news query API.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code GET /api/news?key=} — single-keyword lookup for the company detail page
 *       (company symbols and sector names). Reads straight from the database via
 *       {@link CompanyNewsService} — no caching layer.</li>
 *   <li>{@code GET /api/news/merged?keys=&page=&size=} — multi-keyword paginated merge
 *       for tab-level views (Domestic sectors, Global market tabs). Served entirely from
 *       {@link com.companynews.newsscheduler.service.KeywordNewsBucketCache}, the in-memory
 *       server-side 96-slot rolling-24-hour cache, via {@link NewsAggregatorService}.</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log = LogManager.getLogger(NewsController.class);

    /**
     * Upper bound on keywords accepted by {@code /sentiment} in one request.
     *
     * <p>Guards against an unbounded {@code IN (...)} clause. The largest legitimate caller is
     * a Nifty 50 table, so 200 leaves generous headroom while still capping a malformed or
     * hostile request.
     */
    private static final int MAX_SENTIMENT_KEYS = 200;

    private final CompanyNewsService companyNewsService;
    private final NewsAggregatorService aggregatorService;
    private final CurrentSentimentService currentSentimentService;

    public NewsController(CompanyNewsService companyNewsService,
                          NewsAggregatorService aggregatorService,
                          CurrentSentimentService currentSentimentService) {
        this.companyNewsService      = companyNewsService;
        this.aggregatorService       = aggregatorService;
        this.currentSentimentService = currentSentimentService;
    }

    /**
     * Returns news for a single keyword (company symbol, sector, or macro term).
     * Results are cached in the {@code companyNews} LRU cache (max 100 symbols).
     *
     * <p>Example: {@code GET /api/news?key=INFY}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNews(
            @RequestParam @NotBlank(message = "key must not be blank") String key) {
        log.info("GET /api/news?key={}", key);
        return ResponseEntity.ok(companyNewsService.getNews(key));
    }

    /**
     * Returns a paginated merged-news response for multiple keywords.
     * Used by tab-level views (Domestic sector tabs, Global market tabs).
     * Served entirely from the in-memory 96-slot rolling-24-hour bucket cache — see
     * {@link NewsAggregatorService}.
     *
     * <p>Example: {@code GET /api/news/merged?keys=IT,Banking&page=0&size=20}
     *
     * @param keys  comma-separated keyword list
     * @param page  0-based page index (default 0)
     * @param size  items per page (default 20)
     */
    @GetMapping("/merged")
    public ResponseEntity<Map<String, Object>> getMergedNews(
            @RequestParam @NotEmpty(message = "keys must not be empty") String keys,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        String sortedKeys = Arrays.stream(keys.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .sorted()
            .collect(Collectors.joining(","));

        log.info("GET /api/news/merged keys={} page={} size={}", sortedKeys, page, size);

        Map<String, Object> result = aggregatorService.buildPage(sortedKeys, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns current sentiment for many keywords in one call.
     *
     * <p>Exists because the watchlist and the Nifty index / sector company tables render dozens
     * of companies at once. Calling {@code GET /api/news?key=} per row would mean fifty HTTP
     * requests and fifty full news payloads to populate a single column. This endpoint carries
     * no article text — just a score, a label and a contributing-article count per keyword —
     * and resolves them with a single database query.
     *
     * <p>Every requested keyword appears in the response. Ones with no scored news come back as
     * {@code NO_DATA} rather than being omitted, so the frontend never has to distinguish a
     * missing key from a genuine neutral reading.
     *
     * <p>Example: {@code GET /api/news/sentiment?keys=INFY,TCS,RELIANCE}
     *
     * @param keys comma-separated keyword list
     */
    @GetMapping("/sentiment")
    public ResponseEntity<Map<String, SentimentDto>> getSentiments(
            @RequestParam @NotEmpty(message = "keys must not be empty") String keys) {

        List<String> keywords = Arrays.stream(keys.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .limit(MAX_SENTIMENT_KEYS)
            .toList();

        log.info("GET /api/news/sentiment keys={}", keywords.size());
        return ResponseEntity.ok(currentSentimentService.getForKeywords(keywords));
    }
}
