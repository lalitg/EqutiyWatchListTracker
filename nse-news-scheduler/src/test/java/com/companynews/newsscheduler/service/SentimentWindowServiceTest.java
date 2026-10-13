package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.SentimentWindow;
import com.companynews.newsscheduler.dto.SentimentWindowDto;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Tests the breakdown behind the Sentiments tab: one latest-article row plus a series of periods.
 *
 * <p>Pure arithmetic over already-scored items — no model, no database. Rows are built from relative
 * ages rather than fixed dates so the suite does not start failing on a particular calendar day.
 */
class SentimentWindowServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private SentimentWindowService service() {
        SentimentScorer scorer = new SentimentScorer(mock(SentimentModelClient.class), 1.5, -1.5);
        CompanyNewsRepository repository = mock(CompanyNewsRepository.class);
        return new SentimentWindowService(
                repository, new CurrentSentimentService(repository, scorer), scorer);
    }

    /** Indexes the returned list by window so assertions read by name rather than position. */
    private Map<SentimentWindow, SentimentWindowDto> windowsOf(CompanyNews record) {
        return service().computeFrom(record).stream()
                .collect(Collectors.toMap(w -> SentimentWindow.valueOf(w.window()),
                                          Function.identity()));
    }

    private static CompanyNews record(NewsItem... items) {
        CompanyNews record = new CompanyNews();
        record.setKeyword("TESTCO");
        record.setNews(new ArrayList<>(List.of(items)));
        return record;
    }

    /** An item scored {@code score}, published {@code hoursAgo} hours before now. */
    private static NewsItem item(double score, long hoursAgo) {
        NewsItem item = new NewsItem("Mon, 01 Jan 2026 10:00:00 GMT", "headline",
                                     "https://x/" + hoursAgo + "/" + score);
        item.setSentimentScore(score);
        item.setSentimentLabel(score >= 1.5 ? "POSITIVE" : score <= -1.5 ? "NEGATIVE" : "NEUTRAL");
        item.setPublishedAt(System.currentTimeMillis() - Duration.ofHours(hoursAgo).toMillis());
        return item;
    }

    /** Hours between now and midday today, IST — safely inside the current calendar day. */
    private static long hoursToMiddayToday() {
        ZonedDateTime now = ZonedDateTime.now(IST);
        long hours = Duration.between(now.toLocalDate().atStartOfDay(IST).plusHours(12), now)
                             .toHours();
        // Before midday that difference is negative, which would place the item in the future.
        // Fall back to one hour ago, which is still today.
        return hours > 0 ? hours : 1;
    }

    /** Hours between now and midday yesterday, IST — safely inside the previous calendar day. */
    private static long hoursToMiddayYesterday() {
        ZonedDateTime now = ZonedDateTime.now(IST);
        return Duration.between(now.toLocalDate().minusDays(1).atStartOfDay(IST).plusHours(12), now)
                       .toHours();
    }

    // ── shape ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every row is always present, even for a company with no news")
    void allRowsAlwaysPresent() {
        // The tab renders a fixed set of rows. Omitting empty ones would read as a rendering fault
        // rather than as an absence of news, and most companies have nothing published today.
        for (CompanyNews input : List.of(new CompanyNews(), record())) {
            List<SentimentWindowDto> rows = service().computeFrom(input);
            assertEquals(SentimentWindow.values().length, rows.size());
            rows.forEach(row -> {
                assertNull(row.score());
                assertEquals("NO_DATA", row.sentiment());
                assertEquals(0, row.articleCount());
            });
        }
        assertEquals(SentimentWindow.values().length, service().computeFrom(null).size());
    }

    @Test
    @DisplayName("rows come back in declaration order, latest first")
    void rowsAreOrdered() {
        List<String> order = service().computeFrom(record(item(1.0, 1))).stream()
                .map(SentimentWindowDto::window).toList();

        assertEquals(List.of("LATEST", "TODAY", "YESTERDAY",
                             "WEEK_1", "WEEK_2", "MONTH_1", "QUARTER_1"), order);
    }

    @Test
    @DisplayName("each row carries its display label")
    void rowsCarryLabels() {
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(item(1.0, 1)));

        assertEquals("Latest",         windows.get(SentimentWindow.LATEST).label());
        assertEquals("Today",          windows.get(SentimentWindow.TODAY).label());
        assertEquals("Last 2 weeks",   windows.get(SentimentWindow.WEEK_2).label());
        assertEquals("Last 1 quarter", windows.get(SentimentWindow.QUARTER_1).label());
    }

    // ── the latest row ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("latest is one article and reports a count of exactly 1")
    void latestIsOneArticle() {
        Map<SentimentWindow, SentimentWindowDto> windows =
                windowsOf(record(item(4.0, 1), item(-4.0, 2)));

        SentimentWindowDto latest = windows.get(SentimentWindow.LATEST);
        assertEquals(4.0, latest.score(), 0.001);   // not the average of +4 and -4
        assertEquals(1, latest.articleCount());
    }

    @Test
    @DisplayName("only the latest row carries a date")
    void onlyLatestCarriesDate() {
        // An average spans many dates; a single timestamp on it would misrepresent the reading.
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(item(2.0, 1)));

        assertNotNull(windows.get(SentimentWindow.LATEST).publishedAt());
        assertNull(windows.get(SentimentWindow.TODAY).publishedAt());
        assertNull(windows.get(SentimentWindow.WEEK_1).publishedAt());
        assertNull(windows.get(SentimentWindow.QUARTER_1).publishedAt());
    }

    @Test
    @DisplayName("latest survives when every period is empty")
    void latestSurvivesEmptyPeriods() {
        // The whole reason the periods are free to be honestly empty: something always shows.
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(item(3.0, 24 * 200)));

        assertEquals(3.0, windows.get(SentimentWindow.LATEST).score(), 0.001);
        assertEquals("NO_DATA", windows.get(SentimentWindow.TODAY).sentiment());
        assertEquals("NO_DATA", windows.get(SentimentWindow.QUARTER_1).sentiment());
    }

    @Test
    @DisplayName("the latest row matches what the badge beside the company name shows")
    void latestMatchesBadge() {
        // Delegating to CurrentSentimentService rather than recomputing is what guarantees this.
        // Two independent implementations of "latest" would eventually disagree, and the tab sits
        // one click from the badge that would contradict it.
        CompanyNews record = record(item(4.0, 1), item(2.0, 2));

        SentimentScorer scorer = new SentimentScorer(mock(SentimentModelClient.class), 1.5, -1.5);
        CompanyNewsRepository repository = mock(CompanyNewsRepository.class);
        CurrentSentimentService current = new CurrentSentimentService(repository, scorer);

        SentimentWindowDto row = new SentimentWindowService(repository, current, scorer)
                .computeFrom(record).get(0);

        assertEquals(current.computeLatest(record).score(),       row.score());
        assertEquals(current.computeLatest(record).label(),       row.sentiment());
        assertEquals(current.computeLatest(record).publishedAt(), row.publishedAt());
    }

    // ── calendar days ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("today is the current calendar day, not the newest few articles")
    void todayIsACalendarDay() {
        // This is the whole point of the rename. The old CURRENT row averaged the newest five
        // headlines at any age, so a company whose last news was three weeks ago would have shown
        // those articles under a heading that said Today.
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(
                item(4.0, hoursToMiddayToday()),
                item(-4.0, 24 * 21)));                 // three weeks old

        SentimentWindowDto today = windows.get(SentimentWindow.TODAY);
        assertEquals(4.0, today.score(), 0.001);
        assertEquals(1, today.articleCount());
    }

    @Test
    @DisplayName("today is empty when nothing was published today, and says so")
    void todayIsHonestlyEmpty() {
        // The common case, and correct: 0.0 Neutral and "no news" must not render the same way.
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(item(3.0, 24 * 5)));

        SentimentWindowDto today = windows.get(SentimentWindow.TODAY);
        assertNull(today.score());
        assertEquals("NO_DATA", today.sentiment());
        assertEquals(0, today.articleCount());
    }

    @Test
    @DisplayName("today and yesterday do not overlap")
    void todayAndYesterdayAreDisjoint() {
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(
                item(4.0, hoursToMiddayToday()),
                item(-2.0, hoursToMiddayYesterday())));

        assertEquals(4.0,  windows.get(SentimentWindow.TODAY).score(), 0.001);
        assertEquals(1,    windows.get(SentimentWindow.TODAY).articleCount());
        assertEquals(-2.0, windows.get(SentimentWindow.YESTERDAY).score(), 0.001);
        assertEquals(1,    windows.get(SentimentWindow.YESTERDAY).articleCount());
    }

    @Test
    @DisplayName("a closed period is not truncated by the newest-first ordering")
    void closedPeriodScansWholeList() {
        // Yesterday starts partway down a newest-first list. Short-circuiting at the first
        // out-of-range item would silently drop everything below it.
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(
                item(1.0, 1),
                item(2.0, hoursToMiddayYesterday()),
                item(4.0, hoursToMiddayYesterday() + 2)));

        assertEquals(3.0, windows.get(SentimentWindow.YESTERDAY).score(), 0.001);
        assertEquals(2,   windows.get(SentimentWindow.YESTERDAY).articleCount());
    }

    // ── rolling periods ────────────────────────────────────────────────────────

    @Test
    @DisplayName("longer periods contain shorter ones")
    void periodsAreNested() {
        // +4 two hours ago, -2 ten days ago. The week sees only the first; the month sees both.
        Map<SentimentWindow, SentimentWindowDto> windows =
                windowsOf(record(item(4.0, 2), item(-2.0, 24 * 10)));

        assertEquals(4.0, windows.get(SentimentWindow.WEEK_1).score(), 0.001);
        assertEquals(1,   windows.get(SentimentWindow.WEEK_1).articleCount());
        assertEquals(1.0, windows.get(SentimentWindow.MONTH_1).score(), 0.001);
        assertEquals(2,   windows.get(SentimentWindow.MONTH_1).articleCount());
    }

    @Test
    @DisplayName("an article outside a period does not leak into it")
    void articlesOutsidePeriodExcluded() {
        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(item(5.0, 24 * 100)));

        assertEquals("NO_DATA", windows.get(SentimentWindow.QUARTER_1).sentiment());
        assertEquals(0,         windows.get(SentimentWindow.QUARTER_1).articleCount());
    }

    @Test
    @DisplayName("the quarter row matches the quarter column served to the tables")
    void quarterRowMatchesTableColumn() {
        // Both read SentimentWindow.QUARTER_1.days(), so they are the same number by construction.
        // A user comparing the watchlist column against this tab must not see two answers.
        CompanyNews record = record(item(4.0, 1), item(-1.0, 24 * 50), item(2.0, 24 * 95));

        SentimentScorer scorer = new SentimentScorer(mock(SentimentModelClient.class), 1.5, -1.5);
        CompanyNewsRepository repository = mock(CompanyNewsRepository.class);
        CurrentSentimentService current = new CurrentSentimentService(repository, scorer);

        SentimentWindowDto row = windowsOf(record).get(SentimentWindow.QUARTER_1);

        assertEquals(current.computeQuarter(record).score(),        row.score());
        assertEquals(current.computeQuarter(record).articleCount(), row.articleCount());
    }

    // ── exclusions ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unscored articles are skipped, not counted as neutral")
    void unscoredArticlesSkipped() {
        NewsItem unscored = item(0.0, 2);
        unscored.setSentimentScore(null);

        Map<SentimentWindow, SentimentWindowDto> windows =
                windowsOf(record(item(4.0, 1), unscored));

        assertEquals(4.0, windows.get(SentimentWindow.WEEK_1).score(), 0.001);
        assertEquals(1,   windows.get(SentimentWindow.WEEK_1).articleCount());
    }

    @Test
    @DisplayName("an undated article is excluded from every period but can still be the latest")
    void undatedArticleExcludedFromPeriods() {
        // It cannot be placed in time, and guessing would put it in every period at once — the one
        // outcome guaranteed to be wrong.
        NewsItem undated = item(4.0, 1);
        undated.setPublishedAt(null);
        undated.setDate("not a date at all");

        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(undated));

        assertEquals("NO_DATA", windows.get(SentimentWindow.TODAY).sentiment());
        assertEquals("NO_DATA", windows.get(SentimentWindow.QUARTER_1).sentiment());
        assertEquals(4.0,       windows.get(SentimentWindow.LATEST).score(), 0.001);
    }

    @Test
    @DisplayName("falls back to parsing the date string when publishedAt is absent")
    void fallsBackToParsingDateString() {
        // Keeps the tab correct between deployment and the backfill completing.
        NewsItem legacy = item(4.0, 1);
        legacy.setPublishedAt(null);
        legacy.setDate(ZonedDateTime.now(IST).minusDays(3)
                .format(java.time.format.DateTimeFormatter
                        .ofPattern("dd-MMM-yyyy HH:mm:ss", java.util.Locale.ENGLISH)));

        Map<SentimentWindow, SentimentWindowDto> windows = windowsOf(record(legacy));

        assertEquals(4.0, windows.get(SentimentWindow.WEEK_1).score(), 0.001);
        assertEquals(1,   windows.get(SentimentWindow.WEEK_1).articleCount());
    }
}
