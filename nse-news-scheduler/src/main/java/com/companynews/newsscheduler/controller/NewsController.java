package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.service.CompanyNewsService;
import com.companynews.newsscheduler.service.NewsAggregatorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing the news query API.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code GET /api/news?key=} — single-keyword lookup for the company detail page.
 *       Results cached in the {@code companyNews} LRU cache (max 100 symbols) via
 *       {@link CompanyNewsService}.</li>
 *   <li>{@code GET /api/news/merged?keys=&page=&size=} — multi-keyword paginated merge
 *       for tab-level views (Domestic sectors, Global market tabs). Results cached in the
 *       {@code mergedNews} Caffeine cache with 24-hour age filtering via
 *       {@link NewsAggregatorService}.</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log = LogManager.getLogger(NewsController.class);

    private final CompanyNewsService companyNewsService;
    private final NewsAggregatorService aggregatorService;

    public NewsController(CompanyNewsService companyNewsService,
                          NewsAggregatorService aggregatorService) {
        this.companyNewsService = companyNewsService;
        this.aggregatorService  = aggregatorService;
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
     * Results are cached in the {@code mergedNews} Caffeine cache with 24-hour age filtering.
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
        aggregatorService.warmNextPage(sortedKeys, page, size);
        return ResponseEntity.ok(result);
    }
}
