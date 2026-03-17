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

    private static final int WINDOW_SIZE = 100;

    // These are the same data structures as before
    // BUT all methods that access them are now synchronized
    private final Set<String> urlSet = new HashSet<>();
    private final Queue<String> urlQueue = new LinkedList<>();

    /**
     * WHY synchronized:
     * When Thread 1 calls isAlreadySeen("urlA") and Thread 2 calls
     * isAlreadySeen("urlA") at the exact same moment:
     * - Without synchronized: both get false → both think it is new
     *   → both save it → DUPLICATE saved to DB
     * - With synchronized: Thread 2 waits until Thread 1 finishes
     *   → correct result every time
     *
     * synchronized means only ONE thread can execute this method
     * at a time. Other threads wait in line.
     */
    public synchronized boolean isAlreadySeen(String url) {
        if (url == null) return false;
        return urlSet.contains(url);
    }

    /**
     * WHY synchronized here too:
     * isAlreadySeen and markAsSeen must be atomic together.
     * Thread 1: isAlreadySeen("urlA") → false
     * Thread 2: isAlreadySeen("urlA") → false (Thread 1 hasn't marked yet)
     * Thread 1: markAsSeen("urlA")
     * Thread 2: markAsSeen("urlA") → DUPLICATE
     *
     * By making both methods synchronized on the same object (this),
     * only one thread can be in either method at any time.
     * This makes the check-then-mark operation atomic.
     */
    public synchronized void markAsSeen(String url) {
        if (url == null) return;

        urlSet.add(url);
        urlQueue.add(url);

        if (urlQueue.size() > WINDOW_SIZE) {
            String oldest = urlQueue.poll();
            urlSet.remove(oldest);
            log.debug("URL window full — removed oldest URL");
        }

        log.debug("URL added to window. Window size: {}", urlSet.size());
    }

    /**
     * Atomically checks if URL is new AND marks it as seen in one operation.
     * Returns true if the URL is NEW (not seen before).
     * Returns false if the URL is a DUPLICATE (already seen).
     *
     * WHY this method exists:
     * Without this, the check and mark are two separate method calls.
     * Even with each being synchronized individually, another thread
     * can sneak in between the two calls.
     *
     * Thread 1: isAlreadySeen("urlA") → false ← Thread 2 sneaks in here
     * Thread 2: isAlreadySeen("urlA") → false
     * Thread 1: markAsSeen("urlA")
     * Thread 2: markAsSeen("urlA") → DUPLICATE
     *
     * With checkAndMark(), the check AND mark happen inside ONE
     * synchronized block — no thread can sneak in between.
     */
    public synchronized boolean checkAndMark(String url) {
        if (url == null) return false;
        if (urlSet.contains(url)) {
            return false;  // duplicate
        }
        // New URL — mark it and return true
        urlSet.add(url);
        urlQueue.add(url);
        if (urlQueue.size() > WINDOW_SIZE) {
            String oldest = urlQueue.poll();
            urlSet.remove(oldest);
        }
        return true;  // new
    }

    public synchronized int getWindowSize() {
        return urlSet.size();
    }
}
