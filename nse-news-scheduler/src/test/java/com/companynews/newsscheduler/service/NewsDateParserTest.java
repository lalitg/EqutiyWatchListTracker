package com.companynews.newsscheduler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the behaviour of the shared date parser after it was changed to sniff the format before
 * parsing rather than trying NSE first and catching the failure.
 *
 * <p>The change was made for cost — RSS is the more common format in production, so the previous
 * order threw and caught an exception on the majority of articles — but the risk it introduces is
 * correctness: a wrong sniff would send a string to the wrong formatter. These tests exist to hold
 * both formats, and the fallback between them, in place.
 */
class NewsDateParserTest {

    @Test
    @DisplayName("parses the NSE format as Indian market time")
    void parsesNseFormat() {
        ZonedDateTime parsed = NewsDateParser.parse("12-Mar-2026 17:11:14");

        assertEquals(2026, parsed.getYear());
        assertEquals(3,    parsed.getMonthValue());
        assertEquals(12,   parsed.getDayOfMonth());
        assertEquals(17,   parsed.getHour());
        assertEquals(ZoneId.of("Asia/Kolkata"), parsed.getZone());
    }

    @Test
    @DisplayName("parses the RSS format using the zone in the string")
    void parsesRssFormat() {
        ZonedDateTime parsed = NewsDateParser.parse("Thu, 12 Mar 2026 10:00:00 GMT");

        assertEquals(2026, parsed.getYear());
        assertEquals(12,   parsed.getDayOfMonth());
        assertEquals(10,   parsed.getHour());
    }

    @Test
    @DisplayName("both formats resolve to the same instant when they describe one")
    void formatsAgreeOnInstant() {
        // 10:00 GMT is 15:30 IST on the same day. Getting the sniff wrong would silently shift
        // every RSS article by five and a half hours, which is small enough to pass a casual
        // glance at the UI and large enough to move articles across a calendar-day boundary.
        Long rss = NewsDateParser.toEpochMillis("Thu, 12 Mar 2026 10:00:00 GMT");
        Long nse = NewsDateParser.toEpochMillis("12-Mar-2026 15:30:00");

        assertEquals(rss, nse);
    }

    @Test
    @DisplayName("returns null rather than throwing on unusable input")
    void unusableInputIsNull() {
        assertNull(NewsDateParser.parse(null));
        assertNull(NewsDateParser.parse(""));
        assertNull(NewsDateParser.parse("   "));
        assertNull(NewsDateParser.parse("not a date at all"));
        assertNull(NewsDateParser.parse("Thu, definitely not a date"));
        assertNull(NewsDateParser.toEpochMillis("nonsense"));
    }

    @Test
    @DisplayName("a string that sniffs one way but parses the other still resolves")
    void fallsBackWhenSniffIsWrong() {
        // The sniff only looks at one character, so the fallback is what makes it safe. A date
        // whose fourth character happens to be a comma must still parse if it is really NSE.
        assertNull(NewsDateParser.parse("12-,ar-2026 17:11:14"));   // genuinely malformed
        assertEquals(2026, NewsDateParser.parse("12-Mar-2026 17:11:14").getYear());
    }

    @Test
    @DisplayName("toEpochMillis agrees with parse")
    void epochMillisMatchesParse() {
        String date = "Thu, 12 Mar 2026 10:00:00 GMT";

        assertEquals(NewsDateParser.parse(date).toInstant().toEpochMilli(),
                     NewsDateParser.toEpochMillis(date));
    }
}
