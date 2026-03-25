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
 * Persists company events to the database with per-keyword locking.
 *
 * <p>Uses a <b>replace strategy</b>: all events for a company are overwritten
 * on each fetch. NSE provides the full current calendar, not incremental updates.</p>
 *
 * <p>Per-keyword {@link ReentrantLock} prevents concurrent writes for the same
 * company symbol (e.g. two scheduler threads racing on "INFY").</p>
 */
@Service
public class EventWorkerService {

    private static final Logger log = LoggerFactory.getLogger(EventWorkerService.class);

    private final CompanyEventsRepository repository;

    @Value("${events.limit:10}")
    private int eventsLimit;

    /** Per-keyword locks — prevents concurrent writes for the same company. */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public EventWorkerService(CompanyEventsRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the lock for the given keyword, creating one if absent.
     *
     * @param keyword company symbol
     * @return lock instance for that keyword
     */
    private ReentrantLock getLock(String keyword) {
        return locks.computeIfAbsent(keyword, k -> new ReentrantLock());
    }

    /**
     * Saves or replaces events for the given keyword.
     *
     * <p>Uses {@code noRollbackFor = DataIntegrityViolationException.class} so
     * the transaction stays alive when a constraint violation is caught, allowing
     * the fallback {@link #retryUpdate} to run in the same transaction.</p>
     *
     * @param keyword   company symbol (e.g. "INFY")
     * @param newEvents list of events to persist; no-op if null or empty
     */
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void saveEvents(String keyword, List<EventItem> newEvents) {
        if (newEvents == null || newEvents.isEmpty()) return;

        ReentrantLock lock = getLock(keyword);
        lock.lock();

        try {
            Optional<CompanyEvents> existing = repository.findByKeyword(keyword);
            CompanyEvents record = existing.orElseGet(() -> {
                CompanyEvents c = new CompanyEvents();
                c.setKeyword(keyword);
                return c;
            });

            // new ArrayList<> copies the subList view to prevent later modification issues
            List<EventItem> toSave = newEvents.size() > eventsLimit
                ? new ArrayList<>(newEvents.subList(0, eventsLimit))
                : newEvents;

            record.setEvents(toSave);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);

            log.info("Saved {} event(s) for [{}]", toSave.size(), keyword);

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation for [{}] — retrying as update", keyword);
            retryUpdate(keyword, newEvents);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fallback update executed when a constraint violation is caught in
     * {@link #saveEvents}.
     *
     * <p>Runs inside the same open transaction (kept alive by
     * {@code noRollbackFor}). No separate {@code @Transactional} needed —
     * and none is added, since Spring AOP cannot intercept private methods.</p>
     *
     * @param keyword   company symbol
     * @param newEvents events to persist
     */
    private void retryUpdate(String keyword, List<EventItem> newEvents) {
        Optional<CompanyEvents> existing = repository.findByKeyword(keyword);
        if (existing.isEmpty()) return;

        CompanyEvents record = existing.get();
        List<EventItem> toSave = newEvents.size() > eventsLimit
            ? new ArrayList<>(newEvents.subList(0, eventsLimit))
            : newEvents;

        record.setEvents(toSave);
        record.setLastUpdated(LocalDateTime.now());
        repository.save(record);
        log.info("Retry update succeeded for [{}]", keyword);
    }
}
