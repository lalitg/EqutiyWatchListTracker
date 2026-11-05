package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyDailySentiment;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyDailySentimentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the per-company, per-day rollup that the Extremes page ranks over.
 *
 * <p>Days are relative to now so the suite does not start failing on a particular calendar date.
 */
class DailySentimentServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private CompanyDailySentimentRepository repository;
    private DailySentimentService service;

    @BeforeEach
    void setUp() {
        repository = mock(CompanyDailySentimentRepository.class);
        when(repository.findByKeyword(anyString())).thenReturn(List.of());
        service = new DailySentimentService(repository);
    }

    /** An item scored {@code score}, published {@code hoursAgo} hours before now. */
    private static NewsItem item(double score, long hoursAgo) {
        NewsItem item = new NewsItem("Mon, 01 Jan 2026 10:00:00 GMT", "headline",
                                     "https://x/" + hoursAgo + "/" + score);
        item.setSentimentScore(score);
        item.setPublishedAt(System.currentTimeMillis() - Duration.ofHours(hoursAgo).toMillis());
        return item;
    }

    private static CompanyNews record(NewsItem... items) {
        CompanyNews record = new CompanyNews();
        record.setKeyword("TESTCO");
        record.setNews(new ArrayList<>(List.of(items)));
        return record;
    }

    /** Hours back to midday of the day {@code daysAgo} days before today, IST. */
    private static long hoursToMiddayOf(int daysAgo) {
        ZonedDateTime now = ZonedDateTime.now(IST);
        ZonedDateTime target = now.toLocalDate().minusDays(daysAgo).atStartOfDay(IST).plusHours(12);
        long hours = Duration.between(target, now).toHours();
        return hours > 0 ? hours : 1;
    }

    @SuppressWarnings("unchecked")
    private Map<LocalDate, CompanyDailySentiment> captureSaved() {
        ArgumentCaptor<List<CompanyDailySentiment>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue().stream()
            .collect(Collectors.toMap(CompanyDailySentiment::getDay, Function.identity()));
    }

    @Test
    @DisplayName("stores the sum and the count, not the average")
    void storesSumAndCount() {
        // The distinction that makes the Cumulative tab correct. Averaging daily averages would
        // weight a one-article day exactly as heavily as a twenty-article day; keeping sum and
        // count lets any range be summed article-weighted instead.
        service.rebuildFor(record(item(4.0, hoursToMiddayOf(0)), item(2.0, hoursToMiddayOf(0))));

        CompanyDailySentiment today = captureSaved().get(LocalDate.now(IST));
        assertEquals(6.0, today.getScoreSum(), 0.001);
        assertEquals(2, today.getArticleCount());
        assertEquals(3.0, today.average(), 0.001);
    }

    @Test
    @DisplayName("buckets articles into separate calendar days")
    void bucketsByDay() {
        service.rebuildFor(record(
            item(4.0, hoursToMiddayOf(0)),
            item(-2.0, hoursToMiddayOf(1)),
            item(1.0, hoursToMiddayOf(3))));

        Map<LocalDate, CompanyDailySentiment> saved = captureSaved();
        LocalDate today = LocalDate.now(IST);

        assertEquals(3, saved.size());
        assertEquals(4.0,  saved.get(today).average(), 0.001);
        assertEquals(-2.0, saved.get(today.minusDays(1)).average(), 0.001);
        assertEquals(1.0,  saved.get(today.minusDays(3)).average(), 0.001);
    }

    @Test
    @DisplayName("a day with no news gets no row at all")
    void silentDaysAreAbsentNotZero() {
        // Absent rather than zero is what keeps a silent company out of the ranking. A zero row
        // would sit at the neutral midpoint, outranking genuinely negative companies and being
        // outranked by positive ones, purely for having no news.
        service.rebuildFor(record(item(3.0, hoursToMiddayOf(0)), item(3.0, hoursToMiddayOf(2))));

        Map<LocalDate, CompanyDailySentiment> saved = captureSaved();
        assertEquals(2, saved.size());
        assertTrue(!saved.containsKey(LocalDate.now(IST).minusDays(1)));
    }

    @Test
    @DisplayName("unscored and undated articles are excluded")
    void excludesUnscoredAndUndated() {
        NewsItem unscored = item(0.0, hoursToMiddayOf(0));
        unscored.setSentimentScore(null);

        NewsItem undated = item(5.0, hoursToMiddayOf(0));
        undated.setPublishedAt(null);
        undated.setDate("not a date at all");

        service.rebuildFor(record(item(2.0, hoursToMiddayOf(0)), unscored, undated));

        CompanyDailySentiment today = captureSaved().get(LocalDate.now(IST));
        assertEquals(1, today.getArticleCount());
        assertEquals(2.0, today.average(), 0.001);
    }

    @Test
    @DisplayName("rebuilding is idempotent — an unchanged day is not rewritten")
    void unchangedDayIsNotRewritten() {
        // This runs on every save for every active company, so rewriting rows whose numbers have
        // not moved would multiply writes across the whole table for nothing.
        LocalDate today = LocalDate.now(IST);
        CompanyDailySentiment existing =
            new CompanyDailySentiment("TESTCO", today, 4.0, 1);
        when(repository.findByKeyword("TESTCO")).thenReturn(List.of(existing));

        service.rebuildFor(record(item(4.0, hoursToMiddayOf(0))));

        verify(repository, never()).saveAll(any());
        verify(repository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("a day whose last article was deleted has its row removed")
    void staleDayIsDeleted() {
        // Cleanup can remove the final article of a day. Without this the bucket would keep
        // ranking that company on articles that no longer exist.
        LocalDate today = LocalDate.now(IST);
        CompanyDailySentiment stale =
            new CompanyDailySentiment("TESTCO", today.minusDays(2), -3.0, 1);
        when(repository.findByKeyword("TESTCO")).thenReturn(List.of(stale));

        service.rebuildFor(record(item(1.0, hoursToMiddayOf(0))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CompanyDailySentiment>> deleted = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAll(deleted.capture());
        assertEquals(1, deleted.getValue().size());
        assertEquals(today.minusDays(2), deleted.getValue().get(0).getDay());
    }

    @Test
    @DisplayName("a null or empty record is handled without touching the repository")
    void nullRecordIsSafe() {
        assertEquals(0, service.rebuildFor(null));
        verify(repository, never()).saveAll(any());
    }
}
