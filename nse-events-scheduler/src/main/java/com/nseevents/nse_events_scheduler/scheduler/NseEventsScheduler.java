package com.nseevents.nse_events_scheduler.scheduler;

import com.nseevents.nse_events_scheduler.dto.EventItem;
import com.nseevents.nse_events_scheduler.service.EventWorkerService;
import com.nseevents.nse_events_scheduler.service.NseEventFetchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the NSE Events pipeline.
 * Runs once on startup and then every day at 7 AM.
 *
 * Flow:
 * 1. Fetch all events from NSE Event Calendar API
 * 2. Group by company symbol
 * 3. Save each company's events to DB (replace strategy)
 */
@Component
public class NseEventsScheduler {

    private static final Logger log = LoggerFactory.getLogger(NseEventsScheduler.class);

    private final NseEventFetchService fetchService;
    private final EventWorkerService workerService;

    // Prevents duplicate startup execution
    private boolean startupFetchDone = false;

    public NseEventsScheduler(NseEventFetchService fetchService,
                               EventWorkerService workerService) {
        this.fetchService = fetchService;
        this.workerService = workerService;
    }

    /**
     * Runs ONCE when application is fully started.
     * ApplicationReadyEvent fires exactly once — more reliable than
     * ContextRefreshedEvent which can fire multiple times.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (startupFetchDone) return;
        startupFetchDone = true;
        log.info("=== NSE Events startup fetch triggered ===");
        fetchAndSaveEvents();
    }

    /**
     * Runs every day at 7 AM via cron.
     * Fetches the full event calendar and saves/updates all companies.
     */
    @Scheduled(cron = "${events.nse.cron}")
    public void fetchAndSaveEvents() {
        log.info("NSE Events fetch started...");

        // Step 1: Fetch all events from NSE
        List<EventItem> allEvents = fetchService.fetchAllEvents();

        if (allEvents.isEmpty()) {
            log.warn("NSE Events API returned 0 events — skipping");
            return;
        }

        // Step 2: Group by company symbol
        // NSE returns a flat list mixing all companies
        // We need one group per company to save separately
        // Result: { "INFY" -> [event1, event2], "RELIANCE" -> [event3] }
        Map<String, List<EventItem>> bySymbol = allEvents.stream()
            .filter(e -> e.getSymbol() != null)
            .collect(Collectors.groupingBy(EventItem::getSymbol));

        // Step 3: Save each company's events
        for (Map.Entry<String, List<EventItem>> entry : bySymbol.entrySet()) {
            workerService.saveEvents(entry.getKey(), entry.getValue());
        }

        log.info("NSE Events fetch complete — {} companies saved/updated",
                 bySymbol.size());
    }
}

