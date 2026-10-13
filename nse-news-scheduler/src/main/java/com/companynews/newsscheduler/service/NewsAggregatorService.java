package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates news from multiple keywords into a single merged, paginated response.
 *
 * <p>This service implements the read path for tab-level news (Domestic sector tabs,
 * Global market tabs), sourced entirely from {@link KeywordNewsBucketCache} — the in-memory,
 * server-side, 96-slot rolling-24-hour cache for keywords.txt-sourced keywords.
 *
 * <p>Every call recomputes the merge, dedup, sort, and pagination fresh — there is no result
 * caching in this class. That's intentional: the bucket cache underneath is already in
 * memory and small, so recomputing on every call is cheap, and a second caching layer on
 * top of it would add complexity for no measurable benefit at this scale.
 */
@Service
public class NewsAggregatorService {

    private static final Logger log = LogManager.getLogger(NewsAggregatorService.class);

    private final KeywordNewsBucketCache bucketCache;

    public NewsAggregatorService(KeywordNewsBucketCache bucketCache) {
        this.bucketCache = bucketCache;
    }

    /**
     * Builds a paginated merged-news response for the given comma-separated sorted keyword string.
     *
     * @param sortedKeys comma-separated, alphabetically sorted keyword string (e.g. {@code "Banking,IT"})
     * @param page       0-based page index
     * @param size       number of items per page
     * @return map with keys: {@code content} (List), {@code page}, {@code size},
     *         {@code totalItems}, {@code totalPages}, {@code filtered} (always {@code true} —
     *         the bucket cache only ever holds the last 24 hours, by construction, with no
     *         fallback to older items)
     */
    public Map<String, Object> buildPage(String sortedKeys, int page, int size) {
        List<String> keywords = Arrays.asList(sortedKeys.split(","));
        List<NewsItem> pool = mergeAndDedup(keywords);

        // Sort newest-first
        pool.sort(Comparator.comparing(
            (NewsItem item) -> NewsDateParser.parse(item.getDate()),
            Comparator.nullsLast(Comparator.reverseOrder())
        ));

        int total      = pool.size();
        int totalPages = total == 0 ? 1 : (total + size - 1) / size;
        int fromIdx    = page * size;
        List<NewsItem> pageItems = fromIdx >= total
            ? List.of()
            : pool.subList(fromIdx, Math.min(fromIdx + size, total));

        log.debug("buildPage: keys={} pool={} total={} totalPages={} page={}",
            sortedKeys, pool.size(), total, totalPages, page);

        return Map.of(
            "content",    pageItems,
            "page",       page,
            "size",       size,
            "totalItems", total,
            "totalPages", totalPages,
            "filtered",   true
        );
    }

    private List<NewsItem> mergeAndDedup(List<String> keywords) {
        Set<String> seenUrls = new LinkedHashSet<>();
        List<NewsItem> result = new ArrayList<>();
        for (String keyword : keywords) {
            for (NewsItem item : bucketCache.getAll(keyword)) {
                String link = item.getLink();
                if (link != null && seenUrls.add(link)) {
                    result.add(item);
                }
            }
        }
        return result;
    }
}
