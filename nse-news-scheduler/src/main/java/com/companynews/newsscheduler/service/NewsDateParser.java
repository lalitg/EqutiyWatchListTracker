package com.companynews.newsscheduler.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared utility for parsing {@link com.companynews.newsscheduler.dto.NewsItem} date strings
 * into {@link ZonedDateTime}.
 *
 * <p>Two date formats are in use across the pipeline:
 * <ul>
 *   <li><b>NSE format</b>: {@code "dd-MMM-yyyy HH:mm:ss"} — e.g. {@code "12-Mar-2026 17:11:14"}.
 *       No timezone in the string; IST (Asia/Kolkata) is assumed.</li>
 *   <li><b>RSS format</b>: {@code "EEE, dd MMM yyyy HH:mm:ss z"} — e.g. {@code "Thu, 12 Mar 2026 10:00:00 GMT"}.
 *       Timezone is embedded in the string.</li>
 * </ul>
 *
 * <p>Used by {@link NewsWorker}, {@link NewsAggregatorService}, {@link NewsCleanupService} and
 * {@link SentimentWindowService} to avoid duplicating the same two-format try-catch logic.
 *
 * <h2>Why the format is sniffed before parsing</h2>
 * The original implementation tried NSE first and fell through to RSS on exception. RSS is by
 * far the more common format in production, so the common path threw and caught a
 * {@code DateTimeParseException} on every single article. Exception construction fills in a
 * stack trace, which is orders of magnitude more expensive than the parse itself — invisible
 * when parsing one date, but the dominant cost once a window computation walks a quarter of
 * stored history for a company.
 *
 * <p>{@link #sniff(String)} distinguishes the two formats by a single character (RSS begins
 * with a three-letter day name followed by a comma), so the right formatter is tried first and
 * the fallback exists only for genuinely malformed input.
 */
public final class NewsDateParser {

    private static final DateTimeFormatter NSE_FORMAT =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter RSS_FORMAT =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    /** NSE date strings carry no zone; they are published in Indian market time. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private NewsDateParser() {}

    /**
     * Parses a date string, trying whichever format the string appears to be first.
     *
     * @param dateStr the date string to parse; may be {@code null} or blank
     * @return the parsed {@link ZonedDateTime}, or {@code null} if the string is blank or
     *         matches neither format (callers should treat {@code null} as "unknown / keep")
     */
    public static ZonedDateTime parse(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        if (sniff(dateStr)) {
            ZonedDateTime rss = tryRss(dateStr);
            return rss != null ? rss : tryNse(dateStr);
        }
        ZonedDateTime nse = tryNse(dateStr);
        return nse != null ? nse : tryRss(dateStr);
    }

    /**
     * Parses a date string straight to epoch milliseconds.
     *
     * <p>Convenience for {@link com.companynews.newsscheduler.dto.NewsItem#getPublishedAt()},
     * which stores the normalised instant so that window filtering is an integer comparison
     * rather than a string parse.
     *
     * @param dateStr the date string to parse; may be {@code null} or blank
     * @return epoch milliseconds, or {@code null} if the string could not be parsed
     */
    public static Long toEpochMillis(String dateStr) {
        ZonedDateTime parsed = parse(dateStr);
        return parsed == null ? null : parsed.toInstant().toEpochMilli();
    }

    /**
     * Returns {@code true} if the string looks like the RSS format.
     *
     * <p>RSS dates start with an abbreviated day name and a comma ({@code "Thu, 12 Mar ..."});
     * NSE dates start with a two-digit day and a hyphen ({@code "12-Mar-2026 ..."}). Checking
     * index 3 for a comma separates them without any parsing.
     */
    private static boolean sniff(String dateStr) {
        return dateStr.length() > 3 && dateStr.charAt(3) == ',';
    }

    private static ZonedDateTime tryNse(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, NSE_FORMAT).atZone(IST);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ZonedDateTime tryRss(String dateStr) {
        try {
            return ZonedDateTime.parse(dateStr, RSS_FORMAT);
        } catch (Exception ignored) {
            return null;
        }
    }
}
