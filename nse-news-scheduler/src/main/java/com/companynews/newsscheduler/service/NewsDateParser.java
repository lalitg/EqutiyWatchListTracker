package com.companynews.newsscheduler.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared utility for parsing {@link NewsItem} date strings into {@link ZonedDateTime}.
 *
 * <p>Two date formats are in use across the pipeline:
 * <ul>
 *   <li><b>NSE format</b>: {@code "dd-MMM-yyyy HH:mm:ss"} — e.g. {@code "12-Mar-2026 17:11:14"}.
 *       No timezone in the string; IST (Asia/Kolkata) is assumed.</li>
 *   <li><b>RSS format</b>: {@code "EEE, dd MMM yyyy HH:mm:ss z"} — e.g. {@code "Thu, 12 Mar 2026 10:00:00 GMT"}.
 *       Timezone is embedded in the string.</li>
 * </ul>
 *
 * <p>Used by {@link NewsWorker}, {@link NewsAggregatorService}, and {@link NewsCleanupService}
 * to avoid duplicating the same two-format try-catch logic in multiple places.
 */
public final class NewsDateParser {

    private static final DateTimeFormatter NSE_FORMAT =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter RSS_FORMAT =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private NewsDateParser() {}

    /**
     * Parses a date string using NSE format first, then RSS format.
     *
     * @param dateStr the date string to parse; may be {@code null} or blank
     * @return the parsed {@link ZonedDateTime}, or {@code null} if the string is blank or
     *         matches neither format (callers should treat {@code null} as "unknown / keep")
     */
    public static ZonedDateTime parse(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateStr, NSE_FORMAT).atZone(ZoneId.of("Asia/Kolkata"));
        } catch (Exception ignored) {}
        try {
            return ZonedDateTime.parse(dateStr, RSS_FORMAT);
        } catch (Exception ignored) {}
        return null;
    }
}
