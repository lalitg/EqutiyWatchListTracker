package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory L1 store for tab-level news aggregation.
 *
 * <p>Holds up to {@code news.limit} {@link NewsItem}s per keyword in a
 * {@link ConcurrentHashMap}. Seeded from the database at startup; updated in-place
 * by {@link NewsWorker} on every scheduler write and by {@link NewsCleanupService}
 * after each hourly retention cleanup.
 *
 * <p>The database ({@code company_news} table) is the source of truth for restarts.
 * This store is the source of truth for all live read paths — the aggregator reads
 * from here, never from the DB directly.
 */
@Component
public class NewsStore {

    private static final Logger log = LogManager.getLogger(NewsStore.class);

    private final ConcurrentHashMap<String, List<NewsItem>> store = new ConcurrentHashMap<>();
    private final CompanyNewsRepository repository;

    public NewsStore(CompanyNewsRepository repository) {
        this.repository = repository;
    }

    /**
     * Seeds the store from the database on application startup.
     * Runs once via Spring's {@code @PostConstruct} lifecycle hook.
     */
    @PostConstruct
    public void init() {
        repository.findAll().forEach(record -> {
            if (record.getNews() != null && !record.getNews().isEmpty()) {
                store.put(record.getKeyword(), List.copyOf(record.getNews()));
            }
        });
        log.info("NewsStore seeded with {} keywords from DB", store.size());
    }

    /**
     * Replaces the stored news list for the given keyword.
     * Called by {@link NewsWorker} after each successful DB write and by
     * {@link NewsCleanupService} after each retention cleanup.
     *
     * @param keyword the keyword to update
     * @param items   the new list (stored as an unmodifiable copy)
     */
    public void put(String keyword, List<NewsItem> items) {
        store.put(keyword, List.copyOf(items));
    }

    /**
     * Returns the stored news list for the given keyword, or an empty list if absent.
     *
     * @param keyword the keyword to look up
     * @return unmodifiable list of news items; never {@code null}
     */
    public List<NewsItem> get(String keyword) {
        return store.getOrDefault(keyword, Collections.emptyList());
    }

    /**
     * Returns a snapshot of all keywords currently in the store.
     * Used by {@link NewsAggregatorService} for pre-warming adjacent pages.
     *
     * @return unmodifiable list of all keywords
     */
    public List<String> allKeywords() {
        return List.copyOf(store.keySet());
    }
}
