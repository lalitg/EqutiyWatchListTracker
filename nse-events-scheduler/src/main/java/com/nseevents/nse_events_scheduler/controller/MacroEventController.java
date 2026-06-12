package com.nseevents.nse_events_scheduler.controller;

import com.nseevents.nse_events_scheduler.model.MacroEvent;
import com.nseevents.nse_events_scheduler.repository.MacroEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for macroeconomic event queries.
 *
 * GET /api/macro-events                          — all events, sorted by date asc
 * GET /api/macro-events?category=RBI_MPC         — filter by category
 * GET /api/macro-events?country=US               — filter by country (IN or US)
 * GET /api/macro-events?upcoming=true            — only today and future events
 * GET /api/macro-events?category=FOMC&upcoming=true   — combine filters
 * GET /api/macro-events?country=IN&upcoming=true      — combine filters
 *
 * Valid categories: BUDGET, ELECTION, RBI_MPC, US_CPI, US_NFP, MUHURAT_TRADING, FOMC
 * Valid countries:  IN, US
 */
@RestController
@RequestMapping("/api/macro-events")
public class MacroEventController {

    private static final Logger log = LoggerFactory.getLogger(MacroEventController.class);

    private final MacroEventRepository repository;

    public MacroEventController(MacroEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<MacroEvent>> getEvents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            @RequestParam(required = false, defaultValue = "false") boolean upcoming) {

        String cat     = category != null ? category.trim().toUpperCase() : null;
        String ctry    = country  != null ? country.trim().toUpperCase()  : null;
        LocalDate from = upcoming ? LocalDate.now() : null;

        log.info("GET /api/macro-events category={} country={} upcoming={}", cat, ctry, upcoming);

        List<MacroEvent> result;

        if (cat != null && upcoming) {
            result = repository.findByCategoryAndEventDateGreaterThanEqualOrderByEventDateAsc(cat, from);
        } else if (ctry != null && upcoming) {
            result = repository.findByCountryAndEventDateGreaterThanEqualOrderByEventDateAsc(ctry, from);
        } else if (upcoming) {
            result = repository.findByEventDateGreaterThanEqualOrderByEventDateAsc(from);
        } else if (cat != null) {
            result = repository.findByCategoryOrderByEventDateAsc(cat);
        } else if (ctry != null) {
            result = repository.findByCountryOrderByEventDateAsc(ctry);
        } else {
            result = repository.findAllByOrderByEventDateAsc();
        }

        return ResponseEntity.ok(result);
    }
}
