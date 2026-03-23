package com.companynews.newsscheduler.service;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.TreeSet;

/**
 * In-memory sliding window for NSE sequence ID ({@code seq_id}) deduplication.
 *
 * <p>NSE returns a batch of announcements on every API call, each identified by a numeric
 * {@code seq_id}. The window tracks the most recently seen sequence IDs so that
 * announcements already processed in a previous scheduler cycle are not saved again.
 *
 * <p>WHY a sliding window instead of a DB query:
 * Querying the database on every announcement would add significant latency, especially
 * when processing 20+ announcements per cycle. The in-memory window provides O(log n)
 * deduplication with zero DB load.
 *
 * <p>WHY {@link TreeSet}:
 * <ul>
 *   <li>Sorted: {@code first()} always returns the oldest (smallest) seqId in O(1) for eviction.</li>
 *   <li>{@code contains()}: O(log n) — fast dedup check.</li>
 *   <li>Deduplicates automatically (Set property).</li>
 * </ul>
 *
 * <p>This class is accessed only from the single-threaded NSE scheduler, so no external
 * synchronization is needed. The window starts empty on app restart and fills naturally
 * on the first NSE fetch cycle — cross-cycle duplicates that appear before the window
 * fills are caught by the URL and similarity dedup layers in {@link NewsWorker}.
 */
@Service
public class SeqIdWindow {

    private static final Logger log = LogManager.getLogger(SeqIdWindow.class);

    /**
     * Maximum number of seqIds retained in the window.
     *
     * <p>WHY {@code @Value} instead of a static constant: a static constant requires a code
     * change and redeploy to tune. An {@code @Value} property can be changed in
     * {@code application.properties} and takes effect on next app start — no code change.
     * Default of 20 matches the typical NSE batch size.
     */
    private final int windowSize;

    /** Internal sorted set storing the most recently seen seqIds. */
    private final TreeSet<Long> window = new TreeSet<>();

    /**
     * Constructs a {@code SeqIdWindow} with the configured window capacity.
     *
     * @param windowSize maximum number of seqIds to retain (from {@code news.seqid.window-size})
     */
    public SeqIdWindow(@Value("${news.seqid.window-size:20}") int windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * Logs the initial window state after Spring has finished constructing this bean.
     */
    @PostConstruct
    public void init() {
        log.info("SeqId window initialized — capacity: {} — fills naturally on first NSE fetch cycle",
                windowSize);
    }

    /**
     * Returns {@code true} if the given seqId has already been processed (is a duplicate).
     *
     * <p>Pure in-memory check — no database query.
     *
     * @param seqId the NSE sequence ID to check
     * @return {@code true} if the seqId is already in the window (duplicate); {@code false} if new
     */
    public boolean contains(Long seqId) {
        return window.contains(seqId);
    }

    /**
     * Marks a seqId as processed by adding it to the window.
     *
     * <p>If the window is at capacity, the oldest (smallest) seqId is evicted first
     * to make room. This maintains the sliding-window behaviour.
     *
     * <p>Example (windowSize=3):
     * <pre>
     *   Before: [100, 101, 102]
     *   add(103): window becomes [100,101,102,103] → evict first(100) → [101,102,103]
     * </pre>
     *
     * @param seqId the NSE sequence ID to mark as processed
     */
    public void add(Long seqId) {
        window.add(seqId);
        if (window.size() > windowSize) {
            Long evicted = window.first();
            window.remove(evicted);
            log.debug("Window at capacity — evicted oldest seqId: {}", evicted);
        }
        log.debug("SeqId {} added to window — current size: {}/{}", seqId, window.size(), windowSize);
    }

    /**
     * Returns the current number of seqIds tracked in the window.
     *
     * @return current window occupancy (0 to windowSize)
     */
    public int getWindowSize() {
        return window.size();
    }

    /**
     * Returns a defensive copy of the internal window set.
     *
     * <p>The returned copy is safe to iterate without affecting the internal state.
     * Callers cannot modify the window through the returned set.
     *
     * @return a new {@link TreeSet} containing all currently tracked seqIds
     */
    public TreeSet<Long> getWindow() {
        return new TreeSet<>(window);
    }
}
