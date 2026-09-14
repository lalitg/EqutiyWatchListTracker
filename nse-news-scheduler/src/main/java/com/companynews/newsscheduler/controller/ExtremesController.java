package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.ExtremesDto;
import com.companynews.newsscheduler.dto.SentimentWindow;
import com.companynews.newsscheduler.service.ExtremesService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the Extremes page: the most positive and most negative companies by news sentiment.
 *
 * <p>Deliberately mounted under {@code /api/news} rather than at a path of its own. The frontend
 * dev proxy already routes that prefix to this service, so the page needs no proxy change, and the
 * data being ranked is this service's own.
 */
@RestController
@RequestMapping("/api/news/extremes")
public class ExtremesController {

    private static final Logger log = LogManager.getLogger(ExtremesController.class);

    private final ExtremesService extremesService;

    public ExtremesController(ExtremesService extremesService) {
        this.extremesService = extremesService;
    }

    /**
     * Lists the days the Single Day tab can show, newest first.
     *
     * <p>Served rather than computed in the browser so both sides agree on which day is "today":
     * the boards are bucketed in Indian market time, and a viewer in another timezone — or one whose
     * clock has drifted — would otherwise ask for a day the server does not consider current.
     *
     * <p>{@code GET /api/news/extremes/days}
     */
    @GetMapping("/days")
    public ResponseEntity<List<Map<String, Object>>> getDays() {
        List<Map<String, Object>> days = extremesService.selectableDays().stream()
            .map(day -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("day", day.toString());
                entry.put("label", extremesService.forDayLabel(day));
                entry.put("weekday", day.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH));
                return entry;
            })
            .toList();
        return ResponseEntity.ok(days);
    }

    /**
     * Top positives and top negatives for one calendar day.
     *
     * <p>Omitting {@code day} means today. A day with no news anywhere returns empty boards rather
     * than an error: silence is a real answer, and the page says so.
     *
     * <p>{@code GET /api/news/extremes/daily?day=2026-09-07}
     *
     * @param day ISO date, or omitted for today
     */
    @GetMapping("/daily")
    public ResponseEntity<?> getDaily(@RequestParam(required = false) String day) {
        LocalDate target;
        try {
            target = (day == null || day.isBlank())
                ? com.companynews.newsscheduler.service.DailySentimentService.today()
                : LocalDate.parse(day.trim());
        } catch (DateTimeParseException e) {
            log.warn("Invalid day '{}' for extremes", day);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "day must be an ISO date, e.g. 2026-09-07"));
        }

        log.info("GET /api/news/extremes/daily day={}", target);
        return ResponseEntity.ok(extremesService.forDay(target));
    }

    /**
     * Top positives and top negatives for one cumulative range.
     *
     * <p>{@code GET /api/news/extremes/cumulative?range=WEEK_1}
     *
     * @param range one of {@code WEEK_1}, {@code WEEK_2}, {@code MONTH_1}, {@code QUARTER_1}
     */
    @GetMapping("/cumulative")
    public ResponseEntity<?> getCumulative(
            @RequestParam(defaultValue = "WEEK_1") String range) {

        SentimentWindow window;
        try {
            window = ExtremesService.CUMULATIVE_RANGES.apply(range.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid cumulative range '{}'", range);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "range must be one of WEEK_1, WEEK_2, MONTH_1, QUARTER_1"));
        }

        log.info("GET /api/news/extremes/cumulative range={}", window);
        ExtremesDto body = extremesService.forWindow(window);
        return ResponseEntity.ok(body);
    }
}
