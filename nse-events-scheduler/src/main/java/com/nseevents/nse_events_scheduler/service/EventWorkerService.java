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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Saves events to DB with per-keyword locking to prevent race conditions.
 * Handles INSERT vs UPDATE automatically.
 * Enforces the events.limit per company.
 *
 * WHY replaceAll strategy:
 * Unlike news (where we accumulate items over time),
 * events are a calendar — NSE gives us the full current list each time.
 * We REPLACE all events for a company on every fetch.
 * This ensures past events that NSE removes are also removed from our DB.
 */
@Service
public class EventWorkerService {

    private static final Logger log = LoggerFactory.getLogger(EventWorkerService.class);

    private final CompanyEventsRepository repository;

    @Value("${events.limit:10}")
    private int eventsLimit;

    // Per-keyword locking — prevents race condition when multiple
    // threads save events for the same company simultaneously
    private final ConcurrentHashMap<String, ReentrantLock> keywordLocks
        = new ConcurrentHashMap<>();

    public EventWorkerService(CompanyEventsRepository repository) {
        this.repository = repository;
    }

    private ReentrantLock getLockForKeyword(String keyword) {
        return keywordLocks.computeIfAbsent(keyword, k -> new ReentrantLock());
    }

    @Transactional
    public void saveEvents(String keyword, List<EventItem> newEvents) {
        if (newEvents == null || newEvents.isEmpty()) return;

        ReentrantLock lock = getLockForKeyword(keyword);
        lock.lock();

        try {
            // Load existing row or create new one
            Optional<CompanyEvents> existing = repository.findByKeyword(keyword);
            CompanyEvents record;

            if (existing.isPresent()) {
                record = existing.get();
            } else {
                record = new CompanyEvents();
                record.setKeyword(keyword);
            }

            // REPLACE strategy — NSE gives us the full current event list
            // Trim to limit before saving
            List<EventItem> toSave = newEvents.size() > eventsLimit
                ? newEvents.subList(0, eventsLimit)
                : newEvents;

            record.setEvents(toSave);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);

            log.info("Saved {} event(s) for keyword: {}", toSave.size(), keyword);

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint hit for keyword: {} — retrying", keyword);
            retryAsUpdate(keyword, newEvents);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    private void retryAsUpdate(String keyword, List<EventItem> newEvents) {
        Optional<CompanyEvents> existing = repository.findByKeyword(keyword);
        if (existing.isEmpty()) return;

        CompanyEvents record = existing.get();
        List<EventItem> toSave = newEvents.size() > eventsLimit
            ? newEvents.subList(0, eventsLimit)
            : newEvents;

        record.setEvents(toSave);
        record.setLastUpdated(LocalDateTime.now());
        repository.save(record);
        log.info("Retry update succeeded for keyword: {}", keyword);
    }
}

