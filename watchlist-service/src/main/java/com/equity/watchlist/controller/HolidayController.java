package com.equity.watchlist.controller;

import com.equity.watchlist.client.NseHolidayClient.NseHoliday;
import com.equity.watchlist.service.NseHolidayService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public endpoint — no JWT required.
 * Returns NSE trading holidays (Capital Markets segment) for a given year.
 */
@RestController
@RequestMapping("/api/v1/nse")
public class HolidayController {

    private static final Logger logger = LogManager.getLogger(HolidayController.class);

    private final NseHolidayService holidayService;

    public HolidayController(NseHolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping("/holidays")
    public List<Map<String, String>> getHolidays(@RequestParam(required = false) Integer year) {
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        logger.info("GET /api/v1/nse/holidays?year={}", targetYear);
        List<NseHoliday> holidays = holidayService.getHolidays(targetYear);
        return holidays.stream()
                .map(h -> Map.of("date", h.date().toString(), "name", h.name()))
                .collect(Collectors.toList());
    }
}
