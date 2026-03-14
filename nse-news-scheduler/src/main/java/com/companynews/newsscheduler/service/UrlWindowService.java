package com.companynews.newsscheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

@Service
public class UrlWindowService {

    private static final Logger log = LoggerFactory.getLogger(UrlWindowService.class);

    // Window size — how many URLs we remember
    private static final int WINDOW_SIZE = 100;

    // HashSet for fast O(1) duplicate lookup
    private final Set<String> urlSet = new HashSet<>();

    // Queue to track insertion order so we know which URL to remove
    // when the window is full (FIFO — first in, first out)
    // WHY Queue: HashSet has no order. We need to know which URL was added
    // first so we can remove it when we need to slide the window.
    private final Queue<String> urlQueue = new LinkedList<>();

    /**
     * Check if a URL has already been seen (duplicate check).
     * Pure in-memory — zero DB queries.
     */
    public boolean isAlreadySeen(String url) {
        if (url == null) return false;
        return urlSet.contains(url);
    }

    /**
     * Mark a URL as seen — add it to the window.
     * If window is full, remove the oldest URL first (sliding window).
     */
    public void markAsSeen(String url) {
        if (url == null) return;

        // Add to both the set (for fast lookup) and the queue (for order tracking)
        urlSet.add(url);
        urlQueue.add(url);

        // If exceeded window size, remove the oldest entry
        if (urlQueue.size() > WINDOW_SIZE) {
            String oldest = urlQueue.poll(); // poll() removes and returns head of queue
            urlSet.remove(oldest);
            log.debug("URL window full — removed oldest URL");
        }
    }

    public int getWindowSize() {
        return urlSet.size();
    }
}
