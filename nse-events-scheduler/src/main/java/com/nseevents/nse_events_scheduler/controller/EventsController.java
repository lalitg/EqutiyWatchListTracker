package com.nseevents.nse_events_scheduler.controller;

import com.nseevents.nse_events_scheduler.dto.EventItem;
import com.nseevents.nse_events_scheduler.model.CompanyEvents;
import com.nseevents.nse_events_scheduler.repository.CompanyEventsRepository;
import com.nseevents.nse_events_scheduler.service.EventWorkerService;
import com.nseevents.nse_events_scheduler.service.NseEventFetchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for company event queries.
 *
 * <p>Exposes a single endpoint: {@code GET /api/events?key=SYMBOL}</p>
 *
 * <p>Flow:
 * <ol>
 *   <li>DB hit → return immediately (fast path)</li>
 *   <li>DB miss → on-demand fetch from NSE → save → return</li>
 *   <li>Not found anywhere → empty response</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/api/events")
public class EventsController {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final CompanyEventsRepository repository;
    private final NseEventFetchService fetchService;
    private final EventWorkerService workerService;

    public EventsController(CompanyEventsRepository repository,
                             NseEventFetchService fetchService,
                             EventWorkerService workerService) {
        this.repository = repository;
        this.fetchService = fetchService;
        this.workerService = workerService;
    }

    /**
     * Returns events for the given company symbol.
     * Triggers an on-demand NSE fetch if the symbol is not cached in the database.
     *
     * @param key company symbol, e.g. "INFY", "TCS", "RELIANCE"
     * @return events payload with keyword, events list, and lastUpdated;
     *         HTTP 400 if key is blank; HTTP 200 with empty events if not found
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getEvents(@RequestParam String key) {
        if (key == null || key.isBlank()) {
            log.warn("GET /api/events called with blank or missing key");
            return ResponseEntity.badRequest().build();
        }

        final String symbol = key.trim().toUpperCase();
        log.info("GET /api/events?key={}", symbol);

        // Fast path: return from DB if already present
        Optional<CompanyEvents> existing = repository.findByKeyword(symbol);
        if (existing.isPresent()) {
            log.info("DB hit for [{}]", symbol);
            return ResponseEntity.ok(buildResponse(existing.get()));
        }

        // Slow path: on-demand fetch from NSE, filter by symbol, persist
        log.info("DB miss — on-demand fetch from NSE for [{}]", symbol);
        List<EventItem> allEvents = fetchService.fetchAllEvents();

        List<EventItem> matching = allEvents.stream()
            .filter(e -> symbol.equalsIgnoreCase(e.getSymbol()))
            .toList();

        if (!matching.isEmpty()) {
            workerService.saveEvents(symbol, matching);
        }

        Optional<CompanyEvents> saved = repository.findByKeyword(symbol);
        if (saved.isPresent()) {
            return ResponseEntity.ok(buildResponse(saved.get()));
        }

        log.warn("No events found for [{}]", symbol);
        return ResponseEntity.ok(emptyResponse(symbol));
    }

    /**
     * Builds the standard response payload for a company.
     *
     * @param ce the company events entity
     * @return map with keyword, events list, and lastUpdated timestamp
     */
    private Map<String, Object> buildResponse(CompanyEvents ce) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("keyword", ce.getKeyword());
        resp.put("events", ce.getEvents());
        resp.put("lastUpdated", ce.getLastUpdated());
        return resp;
    }

    /**
     * Builds an empty response when no events are found for the given symbol.
     *
     * @param symbol company symbol
     * @return map with empty events list and null lastUpdated
     */
    private Map<String, Object> emptyResponse(String symbol) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("keyword", symbol);
        resp.put("events", List.of());
        resp.put("lastUpdated", null);
        return resp;
    }
}
