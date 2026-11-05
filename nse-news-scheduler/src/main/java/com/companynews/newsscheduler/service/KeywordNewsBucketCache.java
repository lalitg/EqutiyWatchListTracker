package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, server-side cache holding a rolling 24-hour window of news for each
 * {@code keywords.txt}-sourced keyword (Domestic and Global tabs only). Never used for
 * company symbols or sector names — those continue to read from {@link NewsStore}/the
 * database directly, unchanged.
 *
 * <p>Each keyword maps to 96 "slots" — one per 15-minute period of the day (24h × 4 = 96),
 * matching the scheduler's own fetch cadence. Each slot holds the articles whose own
 * published date falls in that 15-minute window.
 *
 * <p><b>No incremental cell updates, no separate staleness tracking.</b> Unlike a design that
 * mutates one array cell per write, {@link #rebuild} reconstructs a keyword's entire 96-slot
 * structure from scratch every time it is called — reading that keyword's currently known
 * articles and re-bucketing only the ones dated within the last 24 hours. Anything older is
 * simply never included. This makes the cache self-cleaning: there is no possibility of a
 * stale slot lingering from a previous day, because nothing is ever reused across rebuilds.
 *
 * <p><b>{@link #rebuild} must be called every scheduler cycle for every keywords.txt keyword,
 * even when no new articles were found that cycle.</b> Otherwise a quiet keyword's slots would
 * never be re-filtered, and articles that have since aged past 24 hours would keep being served
 * indefinitely instead of aging out on schedule. See
 * {@link com.companynews.newsscheduler.scheduler.GoogleRssScheduler#processKeyword}.
 *
 * <p><b>No floor/fallback.</b> If a keyword's cache is empty or sparse, callers get exactly
 * what's in the cache — no supplementing from the database with older items. This is a
 * deliberate, strict 24-hour cutoff.
 *
 * <p>If the process restarts (deploy, crash), this map starts empty and is naturally rebuilt
 * as the scheduler's startup fetch runs {@link #rebuild} for every keywords.txt keyword —
 * no special "restore" step is needed, since rebuild always derives fresh from
 * {@link NewsStore} (itself seeded from the database on startup).
 */
@Component
public class KeywordNewsBucketCache {

    private static final Logger log = LogManager.getLogger(KeywordNewsBucketCache.class);

    /** Number of 15-minute slots in a 24-hour rolling window: 24 * 60 / 15. */
    static final int SLOT_COUNT = 96;

    /** Length of one slot in seconds: 15 minutes. */
    private static final long SLOT_SECONDS = 900L;

    private final ConcurrentHashMap<String, List<List<NewsItem>>> buckets = new ConcurrentHashMap<>();

    /**
     * Rebuilds the given keyword's entire 96-slot structure from scratch.
     *
     * <p>Filters {@code currentItems} down to only those whose own published date is within
     * the last 24 hours (unparseable dates are excluded — they cannot be placed in a slot),
     * buckets each survivor into its correct 15-minute slot by absolute time (not "minutes
     * since midnight" — that would make the same slot mean a different moment every 24 hours),
     * and atomically replaces the keyword's previous entry with the freshly built one so no
     * reader ever observes a half-updated structure.
     *
     * @param keyword      the keywords.txt-sourced keyword being rebuilt
     * @param currentItems that keyword's currently known articles (from {@link NewsStore})
     */
    public void rebuild(String keyword, List<NewsItem> currentItems) {
        List<List<NewsItem>> slots = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.add(new ArrayList<>());
        }

        long nowSlot           = Instant.now().getEpochSecond() / SLOT_SECONDS;
        long oldestAllowedSlot = nowSlot - SLOT_COUNT;

        int placed = 0;
        for (NewsItem item : currentItems) {
            ZonedDateTime published = NewsDateParser.parse(item.getDate());
            if (published == null) continue; // can't bucket an unparseable date

            long itemSlot = published.toEpochSecond() / SLOT_SECONDS;
            // Must be within the last 24h and not in the future (defensive against clock skew)
            if (itemSlot <= oldestAllowedSlot || itemSlot > nowSlot) continue;

            int pos = (int) Math.floorMod(itemSlot, SLOT_COUNT);
            slots.get(pos).add(item);
            placed++;
        }

        buckets.put(keyword, slots);
        log.debug("Rebuilt bucket cache for keyword={}: {} of {} items placed (last 24h)",
                keyword, placed, currentItems.size());
    }

    /**
     * Returns all articles currently cached for the given keyword, across all 96 slots,
     * in no particular order — {@link NewsAggregatorService} sorts and paginates.
     *
     * @param keyword the keyword to look up
     * @return a flat list of all cached articles for this keyword; empty if the keyword has
     *         no cache entry yet (e.g. before its first scheduler cycle)
     */
    public List<NewsItem> getAll(String keyword) {
        List<List<NewsItem>> slots = buckets.get(keyword);
        if (slots == null) return Collections.emptyList();

        List<NewsItem> result = new ArrayList<>();
        for (List<NewsItem> slot : slots) {
            result.addAll(slot);
        }
        return result;
    }

    /**
     * Removes cache entries for any keyword no longer present in {@code currentFileKeywords}.
     *
     * <p>Called once per scheduler cycle after {@link KeywordLoader#loadFileKeywords()}, so a
     * keyword deleted from {@code keywords.txt} doesn't linger in memory forever. Purely a
     * tidiness measure — the map is small and bounded either way, so this has no functional
     * impact on correctness.
     *
     * @param currentFileKeywords the keywords.txt-sourced keywords as of this cycle
     */
    public void pruneRemovedKeywords(Set<String> currentFileKeywords) {
        int before = buckets.size();
        buckets.keySet().retainAll(currentFileKeywords);
        int removed = before - buckets.size();
        if (removed > 0) {
            log.info("Pruned {} keyword(s) no longer in keywords.txt from bucket cache", removed);
        }
    }
}
