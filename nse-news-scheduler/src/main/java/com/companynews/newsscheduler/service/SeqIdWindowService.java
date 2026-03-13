package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
public class SeqIdWindowService {

    private static final Logger log = LoggerFactory.getLogger(SeqIdWindowService.class);

    // Maximum number of seqIds to keep in memory at any time
    // This is the sliding window size
    private static final int WINDOW_SIZE = 20;

    // The in-memory sliding window
    // TreeSet keeps seqIds sorted numerically (smallest = oldest, largest = newest)
    // WHY TreeSet: sorted + fast contains() + easy to get first() for removal
    private final TreeSet<Long> seqIdWindow = new TreeSet<>();

    private final CompanyNewsRepository repository;

    public SeqIdWindowService(CompanyNewsRepository repository) {
        this.repository = repository;
    }

    /**
     * Called automatically by Spring ONCE after the application starts.
     *
     * WHY @PostConstruct:
     * We cannot load DB data in the constructor because at constructor time,
     * Spring has not finished setting up the database connection yet.
     * @PostConstruct runs after everything is ready — DB, JPA, everything.
     *
     * What it does:
     * Loads all existing seqIds from the DB into our in-memory TreeSet
     * so we don't miss duplicates from before the app restarted.
     *
     * NOTE: In this phase seqId is NOT stored in DB anymore (V2 migration removed it).
     * So on restart, the window starts empty — which is fine because:
     * NSE only keeps the latest 20 announcements anyway.
     * After the first scheduler run, the window fills up correctly.
     */
    @PostConstruct
    public void initializeWindow() {
        log.info("Initializing seqId sliding window...");
        // Window starts empty — seqIds are no longer persisted in DB
        // The window will fill naturally on the first scheduler run
        log.info("SeqId window initialized (empty — will fill on first NSE fetch)");
    }

    /**
     * Check if a seqId has already been processed.
     * This is a pure in-memory check — NO database query.
     *
     * @param seqId the sequence ID from NSE announcement
     * @return true if already seen (duplicate), false if new
     */
    public boolean isAlreadySeen(Long seqId) {
        return seqIdWindow.contains(seqId);
    }

    /**
     * Mark a seqId as processed by adding it to the sliding window.
     * If the window exceeds WINDOW_SIZE, remove the oldest (smallest) seqId.
     *
     * This is the SLIDING part of sliding window:
     * Window before: [100, 101, 102, ... 119]  (20 items)
     * Add 120:       [100, 101, 102, ... 119, 120]  (21 items)
     * Remove oldest: [101, 102, 103, ... 119, 120]  (20 items, slid forward)
     *
     * @param seqId the sequence ID to add to the window
     */
    public void markAsSeen(Long seqId) {
        seqIdWindow.add(seqId);

        // If window exceeded max size, remove the smallest (oldest) seqId
        if (seqIdWindow.size() > WINDOW_SIZE) {
            Long removed = seqIdWindow.first(); // first() = smallest = oldest
            seqIdWindow.remove(removed);
            log.debug("Window full — removed oldest seqId: {}", removed);
        }

        log.debug("SeqId {} added to window. Window size: {}", seqId, seqIdWindow.size());
    }

    /**
     * Returns current window size — useful for logging and debugging.
     */
    public int getWindowSize() {
        return seqIdWindow.size();
    }

    /**
     * Returns a copy of the current window contents — useful for debugging.
     * Returns a copy so caller cannot accidentally modify the real window.
     */
    public TreeSet<Long> getCurrentWindow() {
        return new TreeSet<>(seqIdWindow);
    }
}
