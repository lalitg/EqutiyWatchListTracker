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
import java.util.stream.Collectors;

/**
 * Single REST controller — one GET endpoint.
 * Serves events for any company symbol.
 *
 * Logic:
 * 1. Check DB first — return immediately if found (cache hit)
 * 2. If not found — fetch from NSE on-demand, save, return
 * 3. If nothing found anywhere — return empty response
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
     * GET /api/events?key=INFY
     *
     * Returns all events (both upcoming and past) for the given symbol.
     * If not in DB — triggers on-demand fetch from NSE and returns result.
     *
     * @param key company symbol e.g. "INFY", "RELIANCE", "TCS"
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getEvents(@RequestParam String key) {
        log.info("GET /api/events?key={}", key);

        final String keyword = key.trim().toUpperCase();

        // Step 1: Check DB first — fastest path, no external API call
        Optional<CompanyEvents> existing = repository.findByKeyword(keyword);
        if (existing.isPresent()) {
            log.info("Cache hit — returning DB data for: {}", keyword);
            return ResponseEntity.ok(buildResponse(existing.get()));
        }

        // Step 2: Not in DB — fetch from NSE on-demand
        log.info("Not in DB — fetching on-demand from NSE for: {}", keyword);
        List<EventItem> allEvents = fetchService.fetchAllEvents();

        // Filter only events matching the requested symbol
        List<EventItem> matching = allEvents.stream()
            .filter(e -> keyword.equalsIgnoreCase(e.getSymbol()))
            .collect(Collectors.toList());

        if (!matching.isEmpty()) {
            workerService.saveEvents(keyword, matching);
        }

        // Step 3: Read back what was saved
        Optional<CompanyEvents> saved = repository.findByKeyword(keyword);
        if (saved.isPresent()) {
            return ResponseEntity.ok(buildResponse(saved.get()));
        }

        // Step 4: Nothing found — return empty response
        log.warn("No events found for: {}", keyword);
        return ResponseEntity.ok(buildEmptyResponse(keyword));
    }

    private Map<String, Object> buildResponse(CompanyEvents companyEvents) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword", companyEvents.getKeyword());
        response.put("events", companyEvents.getEvents());
        response.put("lastUpdated", companyEvents.getLastUpdated());
        return response;
    }

    private Map<String, Object> buildEmptyResponse(String key) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword", key);
        response.put("events", List.of());
        response.put("lastUpdated", null);
        return response;
    }
}

