package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Thread-safe in-memory sliding window for URL-based deduplication of Google RSS articles.
 *
 * <p>Tracks the most recently seen article URLs so that articles already fetched and stored
 * in a previous scheduler run are not re-saved when they reappear in a later RSS feed response.
 *
 * <p>WHY two data structures ({@link Set} + {@link Queue}):
 * <ul>
 *   <li>{@link HashSet} — O(1) {@code contains()} check for fast dedup.</li>
 *   <li>{@link LinkedList} (as a {@link Queue}) — tracks insertion order so the oldest URL
 *       can be evicted in O(1) when the window reaches capacity.</li>
 * </ul>
 * Both structures are always kept in sync: every add and evict touches both.
 *
 * <p>All public methods are {@code synchronized} to guarantee thread safety when multiple
 * RSS worker threads process different keywords concurrently.
 *
 * <p>On startup, the window is seeded from the database (via {@link #seedFromDb()}) to
 * restore its pre-restart state and prevent the first scheduled run from re-saving all
 * previously stored articles.
 */
@Service
public class UrlWindow {

    private static final Logger log = LogManager.getLogger(UrlWindow.class);

    /** Maximum number of URLs tracked in the window. Configurable via {@code news.url.window-size}. */
    private final int windowSize;

    private final CompanyNewsRepository companyNewsRepository;

    /** Fast O(1) membership check set. Always in sync with {@link #urlQueue}. */
    private final Set<String>   urlSet   = new HashSet<>();

    /** Insertion-order queue for O(1) eviction of the oldest URL. Always in sync with {@link #urlSet}. */
    private final Queue<String> urlQueue = new LinkedList<>();

    /**
     * Constructs a {@code UrlWindow} with the configured capacity.
     *
     * @param windowSize              maximum number of URLs to retain (from {@code news.url.window-size})
     * @param companyNewsRepository   repository used to seed the window from DB on startup
     */
    public UrlWindow(@Value("${news.url.window-size:500}") int windowSize,
                     CompanyNewsRepository companyNewsRepository) {
        this.windowSize              = windowSize;
        this.companyNewsRepository   = companyNewsRepository;
    }

    /**
     * Seeds the in-memory window with all article URLs currently stored in the database.
     *
     * <p>WHY seeding matters: on app restart the window starts empty. Without seeding, the
     * first Google RSS scheduler run would re-process every URL already stored, triggering
     * unnecessary similarity checks, constraint-violation retries, and log noise. Seeding
     * restores the window to its pre-restart state so the first run only processes genuinely
     * new articles.
     *
     * <p>If the total number of stored URLs exceeds {@link #windowSize}, the oldest ones
     * are naturally evicted via {@link #addIfAbsent(String)} as the window fills beyond capacity.
     *
     * <p>A {@code try-catch} wraps the entire operation so a DB outage at startup does not
     * prevent the application from starting — the window simply starts empty and fills naturally.
     */
    @PostConstruct
    @Transactional(readOnly = true)
    public void seedFromDb() {
        log.info("Seeding URL window from DB — capacity: {}", windowSize);
        try {
            List<CompanyNews> all = companyNewsRepository.findAll();
            int seeded = 0;
            for (CompanyNews cn : all) {
                if (cn.getNews() == null) continue;
                for (var item : cn.getNews()) {
                    if (item.getLink() != null) {
                        addIfAbsent(item.getLink());
                        seeded++;
                    }
                }
            }
            log.info("URL window seeded with {} URLs from DB (current size: {})", seeded, urlSet.size());
        } catch (Exception e) {
            log.warn("Could not seed URL window from DB — window starts empty. Cause: {}", e.getMessage());
        }
    }

    /**
     * Atomically checks if a URL is new and, if so, marks it as seen.
     *
     * <p>Returns {@code true} if the URL is NEW (safe to process and save).
     * Returns {@code false} if the URL is a DUPLICATE (already seen — skip it).
     *
     * <p>WHY {@code synchronized} combined check-and-mark: two separate synchronized methods
     * (e.g., {@code isNew} + {@code markSeen}) still allow a race:
     * <pre>
     *   Thread 1: isNew("urlA") → true   ← Thread 2 sneaks in here
     *   Thread 2: isNew("urlA") → true   (Thread 1 hasn't marked yet)
     *   Both threads save "urlA" → duplicate in DB
     * </pre>
     * One synchronized method eliminates this window: Thread 2 must wait for Thread 1 to
     * complete both the check AND the mark before it can proceed.
     *
     * <p>When the window is at capacity, the oldest URL (front of {@link #urlQueue}) is
     * evicted from both {@link #urlSet} and {@link #urlQueue} before the new URL is added.
     *
     * @param url the article URL to check and potentially mark as seen; returns {@code false} if {@code null}
     * @return {@code true} if the URL was new and has now been marked; {@code false} if already seen or null
     */
    public synchronized boolean addIfAbsent(String url) {
        if (url == null) return false;
        if (urlSet.contains(url)) {
            log.debug("URL already in window — skipping: {}", url);
            return false;
        }

        urlSet.add(url);
        urlQueue.add(url);

        // Evict the oldest URL when the window exceeds capacity
        if (urlQueue.size() > windowSize) {
            String oldest = urlQueue.poll();
            urlSet.remove(oldest);
            log.debug("Window at capacity — evicted oldest URL: {}", oldest);
        }

        return true;
    }

    /**
     * Returns the current number of URLs tracked in the window.
     *
     * @return current window occupancy (0 to windowSize)
     */
    public synchronized int getWindowSize() {
        return urlSet.size();
    }
}
