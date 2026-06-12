package com.equity.watchlist.service;

import com.equity.watchlist.client.NseHolidayClient;
import com.equity.watchlist.client.NseHolidayClient.NseHoliday;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class NseHolidayService {

    private static final Logger logger = LogManager.getLogger(NseHolidayService.class);

    private final NseHolidayClient client;
    private final Map<Integer, List<NseHoliday>> cache = new ConcurrentHashMap<>();

    public NseHolidayService(NseHolidayClient client) {
        this.client = client;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    /** Refresh on the 1st of every month at 6 AM — picks up next-year list when NSE publishes it. */
    @Scheduled(cron = "0 0 6 1 * *")
    public void refresh() {
        logger.info("Refreshing NSE holiday cache");
        List<NseHoliday> holidays = client.fetchHolidays();
        if (holidays.isEmpty()) {
            logger.warn("Refresh returned empty list — keeping stale cache");
            return;
        }
        Map<Integer, List<NseHoliday>> grouped = holidays.stream()
                .collect(Collectors.groupingBy(h -> h.date().getYear()));
        cache.putAll(grouped);
        logger.info("NSE holiday cache updated: {} years, {} total holidays",
                cache.size(), cache.values().stream().mapToInt(List::size).sum());
    }

    public List<NseHoliday> getHolidays(int year) {
        return cache.getOrDefault(year, Collections.emptyList());
    }

    public List<NseHoliday> getHolidays() {
        return getHolidays(LocalDate.now().getYear());
    }
}
