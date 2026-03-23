package com.companynews.newsscheduler.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.TreeSet;

@Service
public class SeqIdWindow {

    private static final Logger log = LoggerFactory.getLogger(SeqIdWindow.class);

    /**
     * WHY @Value instead of a static constant:
     * A static constant requires a code change + redeploy to tune.
     * An @Value property can be changed in application.properties and
     * the new value takes effect on next app start — no code change.
     * Default of 20 matches NSE batch size (NSE returns ~20 items per call).
     */
    private final int windowSize;

    /**
     * TreeSet keeps seqIds sorted numerically (ascending).
     * WHY TreeSet:
     *  - Sorted: first() always returns the oldest (smallest) seqId for O(1) eviction
     *  - contains(): O(log n) — fast dedup check
     *  - Deduplicates automatically (Set property)
     */
    private final TreeSet<Long> window = new TreeSet<>();

    public SeqIdWindow(@Value("${news.seqid.window-size:20}") int windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * SeqIds are not persisted to the DB (intentional design decision — they are
     * NSE-internal identifiers, not meaningful for storage). So the window starts
     * empty on restart and fills naturally on the first NSE fetch cycle.
     * Duplicate announcements that arrive before the window fills are caught by the
     * URL and similarity dedup layers in NewsWorker.
     */
    @PostConstruct
    public void init() {
        log.info("SeqId window initialized (capacity={}) — fills on first NSE fetch", windowSize);
    }

    /**
     * Returns true if this seqId has already been processed (duplicate).
     * Pure in-memory check — no DB query.
     */
    public boolean contains(Long seqId) {
        return window.contains(seqId);
    }

    /**
     * Marks a seqId as processed. If the window is full, evicts the oldest (smallest) entry.
     *
     * Sliding window example (windowSize=3):
     *   Before: [100, 101, 102]
     *   add(103): window=[100,101,102,103] → evict first(100) → [101,102,103]
     */
    public void add(Long seqId) {
        window.add(seqId);
        if (window.size() > windowSize) {
            Long evicted = window.first();
            window.remove(evicted);
            log.debug("Window full — evicted oldest seqId: {}", evicted);
        }
        log.debug("SeqId {} added. Current window size: {}/{}", seqId, window.size(), windowSize);
    }

    public int getWindowSize() {
        return window.size();
    }

    /** Returns a defensive copy — callers cannot modify the internal window. */
    public TreeSet<Long> getWindow() {
        return new TreeSet<>(window);
    }
}
