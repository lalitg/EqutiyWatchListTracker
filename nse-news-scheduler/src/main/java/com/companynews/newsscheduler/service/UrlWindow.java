package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Service
public class UrlWindow {

    private static final Logger log = LoggerFactory.getLogger(UrlWindow.class);

    private final int windowSize;
    private final CompanyNewsRepository companyNewsRepository;

    /**
     * WHY two structures (Set + Queue):
     * Set<String>   — O(1) contains() check for dedup. HashSet is the fastest for this.
     * Queue<String> — tracks insertion order so we evict the oldest URL when capacity is reached.
     * Both are always kept in sync: every add/evict touches both.
     */
    private final Set<String>   urlSet   = new HashSet<>();
    private final Queue<String> urlQueue = new LinkedList<>();

    public UrlWindow(@Value("${news.url.window-size:500}") int windowSize,
                     CompanyNewsRepository companyNewsRepository) {
        this.windowSize = windowSize;
        this.companyNewsRepository = companyNewsRepository;
    }

    /**
     * Seeds the in-memory window with all URLs already stored in the DB.
     *
     * WHY seeding matters:
     * On app restart the window is empty. Without seeding, the first Google RSS
     * scheduler run re-processes every URL it has already saved, triggering a flood
     * of similarity checks, failed INSERTs (UNIQUE constraint on keyword), and log
     * noise. Seeding restores the window to its pre-restart state so the first run
     * only processes genuinely new articles.
     *
     * The try-catch ensures a DB outage at startup does not prevent the app from
     * starting — the window will just start empty and fill naturally.
     */
    @PostConstruct
    public void seedFromDb() {
        try {
            List<CompanyNews> all = companyNewsRepository.findAll();
            int seeded = 0;
            for (CompanyNews cn : all) {
                if (cn.getNews() == null) continue;
                for (var item : cn.getNews()) {
                    if (item.getLink() != null) {
                        // Call addIfAbsent directly — window may be smaller than total DB URLs,
                        // so the oldest ones will be naturally evicted as we fill beyond capacity
                        addIfAbsent(item.getLink());
                        seeded++;
                    }
                }
            }
            log.info("URL window seeded with {} URLs from DB (capacity: {})", seeded, windowSize);
        } catch (Exception e) {
            log.warn("Could not seed URL window from DB: {} — window starts empty", e.getMessage());
        }
    }

    /**
     * Atomically checks if URL is new AND marks it as seen.
     * Returns true if the URL is NEW (safe to process).
     * Returns false if the URL is a DUPLICATE (already seen — skip it).
     *
     * WHY synchronized + combined check-and-mark:
     * Two separate synchronized methods (isNew + markSeen) still allow a race:
     *   Thread 1: isNew("urlA") → true   ← Thread 2 sneaks in here
     *   Thread 2: isNew("urlA") → true   (Thread 1 hasn't marked yet)
     *   Both save "urlA" → DUPLICATE in DB
     *
     * One synchronized method eliminates this window entirely:
     * Thread 2 must wait for Thread 1 to complete both the check AND the mark.
     */
    public synchronized boolean addIfAbsent(String url) {
        if (url == null) return false;
        if (urlSet.contains(url)) return false;

        urlSet.add(url);
        urlQueue.add(url);

        // Evict oldest URL when window is at capacity
        if (urlQueue.size() > windowSize) {
            String oldest = urlQueue.poll();
            urlSet.remove(oldest);
        }

        return true;
    }

    public synchronized int getWindowSize() {
        return urlSet.size();
    }
}
