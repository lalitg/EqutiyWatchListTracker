package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
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
 * <p>This service implements the L2 read path for tab-level news (Domestic sector tabs,
 * Global market tabs). Results are cached in the {@code mergedNews} Caffeine cache.
 *
 * <p>On a cache miss, the service:
 * <ol>
 *   <li>Reads each keyword's news list from {@link NewsStore} (L1).</li>
 *   <li>Flattens and deduplicates by URL.</li>
 *   <li>Sorts newest-first using {@link NewsDateParser}.</li>
 *   <li>Applies a 24-hour age filter (items older than 24 hrs are excluded).
 *       If filtering would leave the result empty, the unfiltered (most-recent-first)
 *       pool is used as a fallback so the UI never shows an empty news panel.</li>
 *   <li>Slices the requested page and caches the full result object.</li>
 * </ol>
 *
 * <p>The cache is evicted by {@link NewsWorker} (via {@code @CacheEvict}) every time
 * new articles are written for any keyword.
 */
@Service
public class NewsAggregatorService {

    private static final Logger log = LogManager.getLogger(NewsAggregatorService.class);

    private final NewsStore newsStore;

    public NewsAggregatorService(NewsStore newsStore) {
        this.newsStore = newsStore;
    }

    /**
     * Builds a paginated merged-news response for the given comma-separated sorted keyword string.
     *
     * <p>The {@code sortedKeys} parameter (not a {@code List}) is intentional: Spring's
     * {@code @Cacheable} key evaluation is simpler and more reliable with a plain String,
     * and the caller (controller) is responsible for normalising the key before calling here.
     *
     * @param sortedKeys comma-separated, alphabetically sorted keyword string (e.g. {@code "Banking,IT"})
     * @param page       0-based page index
     * @param size       number of items per page
     * @return map with keys: {@code content} (List), {@code page}, {@code size},
     *         {@code totalItems}, {@code totalPages}, {@code filtered} (boolean — true if 24hr filter applied)
     */
    @Cacheable(value = "mergedNews", key = "#sortedKeys + '_p' + #page + '_s' + #size")
    public Map<String, Object> buildPage(String sortedKeys, int page, int size) {
        log.debug("mergedNews cache MISS — building page={} size={} keys={}", page, size, sortedKeys);

        List<String> keywords = Arrays.asList(sortedKeys.split(","));
        List<NewsItem> pool = mergeAndDedup(keywords);

        // Sort newest-first
        pool.sort(Comparator.comparing(
            item -> NewsDateParser.parse(item.getDate()),
            Comparator.nullsLast(Comparator.reverseOrder())
        ));

        // Apply 24-hour filter
        ZonedDateTime cutoff = ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusHours(24);
        List<NewsItem> filtered = pool.stream()
            .filter(item -> {
                ZonedDateTime d = NewsDateParser.parse(item.getDate());
                return d == null || d.isAfter(cutoff);
            })
            .toList();

        boolean usedFilter = !filtered.isEmpty();
        List<NewsItem> effective = usedFilter ? filtered : pool;

        // Paginate
        int total      = effective.size();
        int totalPages = total == 0 ? 1 : (total + size - 1) / size;
        int fromIdx    = page * size;
        List<NewsItem> pageItems = fromIdx >= total
            ? List.of()
            : effective.subList(fromIdx, Math.min(fromIdx + size, total));

        log.debug("mergedNews built: keys={} pool={} filtered={} total={} totalPages={} page={}",
            sortedKeys, pool.size(), filtered.size(), total, totalPages, page);

        return Map.of(
            "content",    pageItems,
            "page",       page,
            "size",       size,
            "totalItems", total,
            "totalPages", totalPages,
            "filtered",   usedFilter
        );
    }

    /**
     * Asynchronously pre-warms the cache for the next page after the current request completes.
     * Called by the controller as a fire-and-forget after returning the response to the client.
     *
     * @param sortedKeys comma-separated sorted keyword string (same as used in {@link #buildPage})
     * @param currentPage the page that was just served (pre-warm page+1)
     * @param size        page size (must match the original request)
     */
    @Async("cacheWarmExecutor")
    public void warmNextPage(String sortedKeys, int currentPage, int size) {
        try {
            buildPage(sortedKeys, currentPage + 1, size);
            log.debug("Pre-warmed mergedNews page={} keys={}", currentPage + 1, sortedKeys);
        } catch (Exception e) {
            log.warn("Pre-warm failed for keys={} page={}: {}", sortedKeys, currentPage + 1, e.getMessage());
        }
    }

    private List<NewsItem> mergeAndDedup(List<String> keywords) {
        Set<String> seenUrls = new LinkedHashSet<>();
        List<NewsItem> result = new ArrayList<>();
        for (String keyword : keywords) {
            for (NewsItem item : newsStore.get(keyword)) {
                String link = item.getLink();
                if (link != null && seenUrls.add(link)) {
                    result.add(item);
                }
            }
        }
        return result;
    }
}
