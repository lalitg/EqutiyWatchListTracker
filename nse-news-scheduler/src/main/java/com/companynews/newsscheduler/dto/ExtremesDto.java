package com.companynews.newsscheduler.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One Extremes board: the most positive and most negative companies for a single range.
 *
 * <p>Shape borrowed from the retired fast-movers service, which paired gainers with losers and
 * stamped the result with a generation time. The names here are {@code topPositives} and
 * {@code topNegatives} rather than gainers and losers because sentiment is a <b>level, not a
 * change</b>: a company sitting at +3 all month would appear among "gainers" every day without
 * having gained anything. Naming them for what they are keeps the page from promising a movement
 * it does not measure.
 *
 * @param range        machine-readable range key, e.g. {@code 2026-09-07} for a day or
 *                     {@code WEEK_1} for a period
 * @param label        human-readable range name, e.g. {@code "Yesterday"} or {@code "Last 1 week"}
 * @param day          the calendar day for a single-day board; {@code null} for cumulative ranges
 * @param topPositives most positive first
 * @param topNegatives most negative first
 * @param generatedAt  when this response was assembled. Worth showing: the Today board moves on the
 *                     fifteen-minute fetch cycle, so a reader needs to know how fresh what they are
 *                     looking at is
 */
public record ExtremesDto(String range,
                          String label,
                          LocalDate day,
                          List<ExtremeEntryDto> topPositives,
                          List<ExtremeEntryDto> topNegatives,
                          LocalDateTime generatedAt) {
}
