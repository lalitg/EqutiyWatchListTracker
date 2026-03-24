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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Orchestrates the NSE Events data pipeline.
 * Runs once on startup, then daily at the configured cron time.
 *
 * <p>Flow: fetch all events → group by symbol → persist each company's events.</p>
 */
@Component
public class NseEventsScheduler {

    private static final Logger log = LoggerFactory.getLogger(NseEventsScheduler.class);

    private final NseEventFetchService fetchService;
    private final EventWorkerService workerService;

    /**
     * Guards against duplicate startup execution.
     * AtomicBoolean ensures thread-safe check-and-set without synchronization blocks.
     */
    private final AtomicBoolean startupDone = new AtomicBoolean(false);

    public NseEventsScheduler(NseEventFetchService fetchService,
                               EventWorkerService workerService) {
        this.fetchService = fetchService;
        this.workerService = workerService;
    }

    /**
     * Runs once when the application is fully started.
     *
     * <p>{@link ApplicationReadyEvent} is preferred over {@code ContextRefreshedEvent}
     * because it fires exactly once — after all beans and Flyway migrations are ready.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!startupDone.compareAndSet(false, true)) return;
        log.info("=== NSE Events startup fetch triggered ===");
        syncEvents();
    }

    /**
     * Fetches the full NSE event calendar and persists all companies.
     * Runs every day at the configured cron time (default: 7 AM).
     *
     * <p>Also called directly by {@link #onStartup()} on first boot.</p>
     */
    @Scheduled(cron = "${events.nse.cron}")
    public void syncEvents() {
        log.info("NSE Events sync started");

        List<EventItem> allEvents = fetchService.fetchAllEvents();

        if (allEvents.isEmpty()) {
            log.warn("NSE Events API returned 0 events — skipping");
            return;
        }

        // Group flat event list by company symbol
        // e.g. { "INFY" -> [event1, event2], "TCS" -> [event3] }
        Map<String, List<EventItem>> bySymbol = allEvents.stream()
            .filter(e -> e.getSymbol() != null)
            .collect(Collectors.groupingBy(EventItem::getSymbol));

        bySymbol.forEach(workerService::saveEvents);

        log.info("NSE Events sync complete — {} companies updated", bySymbol.size());
    }
}
