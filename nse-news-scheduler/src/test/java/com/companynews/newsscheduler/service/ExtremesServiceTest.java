package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.ExtremeEntryDto;
import com.companynews.newsscheduler.dto.ExtremesDto;
import com.companynews.newsscheduler.dto.SentimentWindow;
import com.companynews.newsscheduler.repository.CompanyDailySentimentRepository;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.repository.RankedSentimentProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the assembly of the Top Positives / Top Negatives boards.
 *
 * <p>Ordering itself is the database's job — these cover what the service adds on top: ranking,
 * name resolution, banding, and the failure modes that would otherwise cost a reader the board.
 */
class ExtremesServiceTest {

    private CompanyDailySentimentRepository dailyRepository;
    private CompanyNewsRepository newsRepository;
    private CompanyNameLookup nameLookup;
    private ExtremesService service;

    @BeforeEach
    void setUp() {
        dailyRepository = mock(CompanyDailySentimentRepository.class);
        newsRepository  = mock(CompanyNewsRepository.class);
        nameLookup      = mock(CompanyNameLookup.class);
        when(nameLookup.namesFor(anyCollection())).thenReturn(java.util.Map.of());

        service = new ExtremesService(dailyRepository, newsRepository, nameLookup,
                new SentimentScorer(mock(SentimentModelClient.class), 1.5, -1.5), 10, 7);
    }

    private record Row(String getKeyword, Double getScore, Integer getArticleCount)
            implements RankedSentimentProjection {}

    private void stubDay(LocalDate day, List<RankedSentimentProjection> top,
                         List<RankedSentimentProjection> bottom) {
        when(dailyRepository.rankTopForDay(any(), any())).thenReturn(top);
        when(dailyRepository.rankBottomForDay(any(), any())).thenReturn(bottom);
    }

    @Test
    @DisplayName("ranks are 1-based and follow the order the database returned")
    void ranksFollowQueryOrder() {
        stubDay(LocalDate.now(),
                List.of(new Row("AAA", 4.0, 1), new Row("BBB", 3.0, 9)),
                List.of(new Row("ZZZ", -4.0, 2)));

        ExtremesDto board = service.forDay(LocalDate.now());

        assertEquals(1, board.topPositives().get(0).rank());
        assertEquals("AAA", board.topPositives().get(0).symbol());
        assertEquals(2, board.topPositives().get(1).rank());
        assertEquals(1, board.topNegatives().get(0).rank());
    }

    @Test
    @DisplayName("every row carries its article count")
    void everyRowCarriesItsCount() {
        // Not decoration. With no minimum article count, a one-article board entry ranks directly
        // against a twenty-article one, and this is the reader's only way to tell them apart.
        stubDay(LocalDate.now(), List.of(new Row("AAA", 4.6, 1), new Row("BBB", 2.0, 20)), List.of());

        ExtremesDto board = service.forDay(LocalDate.now());

        assertEquals(1,  board.topPositives().get(0).articleCount());
        assertEquals(20, board.topPositives().get(1).articleCount());
    }

    @Test
    @DisplayName("resolves display names, and falls back to the symbol when there is none")
    void resolvesNames() {
        stubDay(LocalDate.now(), List.of(new Row("TCS", 4.0, 3), new Row("UNKNOWN", 3.0, 1)), List.of());
        when(nameLookup.namesFor(anyCollection()))
                .thenReturn(java.util.Map.of("TCS", "Tata Consultancy Services Limited"));

        ExtremesDto board = service.forDay(LocalDate.now());

        assertEquals("Tata Consultancy Services Limited", board.topPositives().get(0).companyName());
        // A gap in company_master costs a nicer label, never the row itself.
        assertNull(board.topPositives().get(1).companyName());
        assertEquals("UNKNOWN", board.topPositives().get(1).symbol());
    }

    @Test
    @DisplayName("a failing name lookup still returns the board")
    void nameLookupFailureIsNotFatal() {
        // CompanyNameLookup swallows its own failures and hands back an empty map, so the board is
        // built from tickers alone rather than the page dying for a decorative column.
        stubDay(LocalDate.now(), List.of(new Row("TCS", 4.0, 3)), List.of());
        when(nameLookup.namesFor(anyCollection())).thenReturn(java.util.Map.of());

        ExtremesDto board = service.forDay(LocalDate.now());

        assertEquals(1, board.topPositives().size());
        assertEquals("TCS", board.topPositives().get(0).symbol());
        assertNull(board.topPositives().get(0).companyName());
    }

    @Test
    @DisplayName("bands come from the shared thresholds, not from a second set of rules")
    void bandsMatchThresholds() {
        stubDay(LocalDate.now(),
                List.of(new Row("AAA", 4.0, 1), new Row("BBB", 1.0, 1)),
                List.of(new Row("ZZZ", -4.0, 1)));

        ExtremesDto board = service.forDay(LocalDate.now());

        assertEquals("POSITIVE", board.topPositives().get(0).label());
        assertEquals("NEUTRAL",  board.topPositives().get(1).label());
        assertEquals("NEGATIVE", board.topNegatives().get(0).label());
    }

    @Test
    @DisplayName("a day with no news returns empty boards rather than failing")
    void emptyDayIsNotAnError() {
        // Silence is a real answer — weekends carry roughly a quarter of a weekday's volume.
        stubDay(LocalDate.now(), List.of(), List.of());

        ExtremesDto board = service.forDay(LocalDate.now());

        assertTrue(board.topPositives().isEmpty());
        assertTrue(board.topNegatives().isEmpty());
        assertEquals("Today", board.label());
    }

    @Test
    @DisplayName("day labels name today and yesterday, and date everything older")
    void dayLabels() {
        LocalDate today = DailySentimentService.today();
        stubDay(today, List.of(), List.of());

        assertEquals("Today",     service.forDayLabel(today));
        assertEquals("Yesterday", service.forDayLabel(today.minusDays(1)));
        // Older days get a date rather than a weekday name: weekends are visibly thin, and a bare
        // "Sat" reads as a fault where a date reads as a quiet weekend.
        assertTrue(service.forDayLabel(today.minusDays(3)).matches("\\d+ \\w+"));
    }

    @Test
    @DisplayName("the day selector always offers a full run of consecutive days")
    void selectableDaysAreConsecutive() {
        // Generated from the calendar, not from which days hold rows. Skipping an empty day would
        // silently renumber the tabs, so "3 days ago" would mean different dates on different days.
        List<LocalDate> days = service.selectableDays();

        assertEquals(7, days.size());
        assertEquals(DailySentimentService.today(), days.get(0));
        for (int i = 1; i < days.size(); i++) {
            assertEquals(days.get(i - 1).minusDays(1), days.get(i));
        }
    }

    @Test
    @DisplayName("cumulative ranges map to the matching query")
    void cumulativeRangesDispatch() {
        when(newsRepository.rankTopByWeek(any())).thenReturn(List.of(new Row("W", 2.0, 5)));
        when(newsRepository.rankBottomByWeek(any())).thenReturn(List.of());
        when(newsRepository.rankTopByQuarter(any())).thenReturn(List.of(new Row("Q", 1.0, 50)));
        when(newsRepository.rankBottomByQuarter(any())).thenReturn(List.of());

        assertEquals("W", service.forWindow(SentimentWindow.WEEK_1).topPositives().get(0).symbol());
        assertEquals("Q", service.forWindow(SentimentWindow.QUARTER_1).topPositives().get(0).symbol());
        assertEquals("Last 1 week", service.forWindow(SentimentWindow.WEEK_1).label());
    }

    @Test
    @DisplayName("a non-cumulative window is rejected rather than silently ranked")
    void nonCumulativeWindowRejected() {
        // LATEST and TODAY are not rolling periods and have no ranking column behind them.
        assertThrows(IllegalArgumentException.class,
                     () -> service.forWindow(SentimentWindow.LATEST));
        assertThrows(IllegalArgumentException.class,
                     () -> ExtremesService.CUMULATIVE_RANGES.apply("TODAY"));
        assertThrows(IllegalArgumentException.class,
                     () -> ExtremesService.CUMULATIVE_RANGES.apply("NOT_A_RANGE"));
    }

    @Test
    @DisplayName("a single-day board records which day it describes")
    void dayBoardCarriesItsDate() {
        LocalDate day = LocalDate.now().minusDays(2);
        stubDay(day, List.of(), List.of());

        ExtremesDto board = service.forDay(day);
        assertEquals(day, board.day());
        assertEquals(day.toString(), board.range());

        // A cumulative board has no single date, and must not invent one.
        when(newsRepository.rankTopByMonth(any())).thenReturn(List.of());
        when(newsRepository.rankBottomByMonth(any())).thenReturn(List.of());
        assertNull(service.forWindow(SentimentWindow.MONTH_1).day());
    }

    @Test
    @DisplayName("scores are rounded to two decimals for display")
    void scoresAreRounded() {
        stubDay(LocalDate.now(), List.of(new Row("AAA", 3.333333, 3)), List.of());

        ExtremeEntryDto entry = service.forDay(LocalDate.now()).topPositives().get(0);
        assertEquals(3.33, entry.score(), 0.0001);
    }
}
