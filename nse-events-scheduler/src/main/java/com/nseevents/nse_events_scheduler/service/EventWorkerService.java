package com.nseevents.nse_events_scheduler.service;

import com.nseevents.nse_events_scheduler.dto.EventItem;
import com.nseevents.nse_events_scheduler.model.CompanyEvents;
import com.nseevents.nse_events_scheduler.repository.CompanyEventsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Persists company events to the database using a merge + per-source early-exit strategy.
 *
 * Strategy (replaces the old overwrite approach):
 *   - An in-memory hashcode cache tracks what is currently in the DB for each company.
 *   - On each scheduler run, events from the 3 NSE APIs are grouped by source.
 *   - Each source's event list (newest first) is walked independently:
 *       - If the event's hashcode is NOT in the cache → new event, add it.
 *       - If the event's hashcode IS in the cache → early-exit for this source
 *         (everything after is already saved).
 *   - New events from all 3 sources are merged with the existing DB events.
 *   - After deduplication, only the newest `events.limit` events are kept.
 *   - Old events are never deleted unless they fall off the bottom of the limit window.
 *
 * Cache structure:
 *   hashCache: Map<symbol, Set<Integer>>
 *   Each integer is the hashcode of the composite string "date|event|purpose".
 *   One combined Set per company covers all 3 sources — the early-exit per source
 *   still works correctly because each source is walked independently.
 *   Cache is warmed from the DB on first access per company after a service restart.
 */
@Service
public class EventWorkerService {

    private static final Logger log = LoggerFactory.getLogger(EventWorkerService.class);

    private final CompanyEventsRepository repository;

    @Value("${events.limit:10}")
    private int eventsLimit;

    /** Per-keyword write locks — prevents two threads from writing the same company. */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * In-memory hashcode cache.
     * Key: company symbol. Value: hashcodes of all events currently saved in DB.
     * Populated lazily (on first saveEvents call per company after restart).
     */
    private final ConcurrentHashMap<String, Set<Integer>> hashCache = new ConcurrentHashMap<>();

    public EventWorkerService(CompanyEventsRepository repository) {
        this.repository = repository;
    }

    private ReentrantLock getLock(String keyword) {
        return locks.computeIfAbsent(keyword, k -> new ReentrantLock());
    }

    /**
     * Merges new events from the 3 NSE APIs into the existing DB record for keyword.
     *
     * Per-source early-exit: events from each API are walked independently.
     * The walk stops for a source as soon as a known event (already in cache) is found.
     * This is correct because NSE returns events newest-first within each API.
     *
     * If no new events are found across all 3 sources, the DB is not touched.
     *
     * @param keyword   company symbol e.g. "INFY"
     * @param newEvents merged flat list from all 3 APIs, each item has source set
     */
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void saveEvents(String keyword, List<EventItem> newEvents) {
        if (newEvents == null || newEvents.isEmpty()) return;

        ReentrantLock lock = getLock(keyword);
        lock.lock();

        try {
            // Load existing DB record — needed for cache warm and merge
            Optional<CompanyEvents> existing = repository.findByKeyword(keyword);
            List<EventItem> existingEvents = existing
                    .map(CompanyEvents::getEvents)
                    .orElse(List.of());

            // Warm cache from DB on first access (after service restart cache is empty)
            Set<Integer> cachedHashes = hashCache.computeIfAbsent(keyword, k -> {
                Set<Integer> hashes = new HashSet<>();
                for (EventItem e : existingEvents) {
                    hashes.add(toHashCode(e));
                }
                log.debug("[{}] cache warmed from DB — {} hashes loaded", keyword, hashes.size());
                return hashes;
            });

            // Group new events by source for independent per-source walks
            Map<String, List<EventItem>> bySource = newEvents.stream()
                    .filter(e -> e != null)
                    .collect(Collectors.groupingBy(
                            e -> e.getSource() != null ? e.getSource() : "unknown"));

            // Walk each source independently — early-exit on first known event
            List<EventItem> toAdd = new ArrayList<>();
            for (Map.Entry<String, List<EventItem>> entry : bySource.entrySet()) {
                String source = entry.getKey();
                List<EventItem> sourceEvents = entry.getValue();
                int addedFromSource = 0;

                for (EventItem e : sourceEvents) {
                    int h = toHashCode(e);
                    if (cachedHashes.contains(h)) {
                        log.debug("[{}][{}] early-exit — known event reached, stopping this source",
                                keyword, source);
                        break;
                    }
                    toAdd.add(e);
                    addedFromSource++;
                }

                if (addedFromSource > 0) {
                    log.debug("[{}][{}] {} new event(s) found", keyword, source, addedFromSource);
                }
            }

            // Nothing new from any source — skip DB write entirely
            if (toAdd.isEmpty()) {
                log.debug("[{}] no new events — DB write skipped", keyword);
                return;
            }

            // Merge: new events first (newest), then existing DB events (older)
            List<EventItem> combined = new ArrayList<>(toAdd);
            combined.addAll(existingEvents);

            // Deduplicate by (date, event, purpose) — covers cross-source overlaps
            List<EventItem> deduped = combined.stream()
                    .filter(e -> e != null)
                    .distinct()
                    .collect(Collectors.toList());

            // Trim to limit — oldest events fall off the bottom
            List<EventItem> toSave = deduped.size() > eventsLimit
                    ? new ArrayList<>(deduped.subList(0, eventsLimit))
                    : deduped;

            // Persist
            CompanyEvents record = existing.orElseGet(() -> {
                CompanyEvents c = new CompanyEvents();
                c.setKeyword(keyword);
                return c;
            });
            record.setEvents(toSave);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);

            // Rebuild cache to match the new DB state
            Set<Integer> updatedHashes = new HashSet<>();
            for (EventItem e : toSave) {
                updatedHashes.add(toHashCode(e));
            }
            hashCache.put(keyword, updatedHashes);

            log.info("[{}] +{} new, {} total saved (limit {})",
                    keyword, toAdd.size(), toSave.size(), eventsLimit);

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation for [{}] — retrying as update", keyword);
            retryUpdate(keyword, newEvents);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fallback for DataIntegrityViolationException (concurrent INSERT race on the same keyword).
     * Merges all new events with the now-existing DB record and updates it.
     * Runs inside the same open transaction (noRollbackFor keeps it alive).
     */
    private void retryUpdate(String keyword, List<EventItem> newEvents) {
        Optional<CompanyEvents> existing = repository.findByKeyword(keyword);
        if (existing.isEmpty()) return;

        CompanyEvents record = existing.get();
        List<EventItem> existingEvents = record.getEvents() != null
                ? record.getEvents() : List.of();

        List<EventItem> combined = new ArrayList<>(newEvents);
        combined.addAll(existingEvents);

        List<EventItem> deduped = combined.stream()
                .filter(e -> e != null)
                .distinct()
                .collect(Collectors.toList());

        List<EventItem> toSave = deduped.size() > eventsLimit
                ? new ArrayList<>(deduped.subList(0, eventsLimit))
                : deduped;

        record.setEvents(toSave);
        record.setLastUpdated(LocalDateTime.now());
        repository.save(record);

        // Sync cache after retry
        Set<Integer> updatedHashes = new HashSet<>();
        for (EventItem e : toSave) {
            updatedHashes.add(toHashCode(e));
        }
        hashCache.put(keyword, updatedHashes);

        log.info("Retry update succeeded for [{}] — {} event(s) saved", keyword, toSave.size());
    }

    /**
     * Computes the hashcode used as the cache key for a single event.
     * Composite string: "date|event|purpose" — all three fields must match for a hit.
     * Null fields become empty string to avoid NullPointerException.
     */
    private int toHashCode(EventItem e) {
        String key = nullSafe(e.getDate()) + "|" + nullSafe(e.getEvent()) + "|" + nullSafe(e.getPurpose());
        return key.hashCode();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
