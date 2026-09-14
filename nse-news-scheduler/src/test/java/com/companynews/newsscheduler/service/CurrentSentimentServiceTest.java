package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.CompanySentimentDto;
import com.companynews.newsscheduler.dto.LatestSentimentDto;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.SentimentDto;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.repository.SentimentProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the two readings served per company: the latest scored headline, and the 90-day average.
 *
 * <p>No model and no database — this is arithmetic over already-scored items, which is exactly the
 * property that lets the read path stay cheap in production. Ages are expressed relative to now so
 * the suite does not start failing on a particular calendar date.
 */
class CurrentSentimentServiceTest {

    private CompanyNewsRepository repository;

    private CurrentSentimentService service() {
        SentimentScorer scorer = new SentimentScorer(mock(SentimentModelClient.class), 1.5, -1.5);
        repository = mock(CompanyNewsRepository.class);
        return new CurrentSentimentService(repository, scorer);
    }

    /** An item scored {@code score}, published {@code daysAgo} days before now. */
    private static NewsItem item(double score, double daysAgo) {
        NewsItem item = new NewsItem("Mon, 01 Jan 2026 10:00:00 GMT", "headline",
                                     "https://x/" + daysAgo + "/" + score);
        item.setSentimentScore(score);
        item.setSentimentLabel(score >= 1.5 ? "POSITIVE" : score <= -1.5 ? "NEGATIVE" : "NEUTRAL");
        item.setPublishedAt(System.currentTimeMillis()
                            - (long) (daysAgo * Duration.ofDays(1).toMillis()));
        return item;
    }

    private static CompanyNews record(NewsItem... items) {
        CompanyNews record = new CompanyNews();
        record.setKeyword("TESTCO");
        record.setNews(new ArrayList<>(List.of(items)));
        return record;
    }

    // ── latest ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("latest is the single newest scored headline, not an average")
    void latestIsOneArticle() {
        LatestSentimentDto latest = service().computeLatest(record(item(4.0, 0.1), item(-4.0, 1)));

        assertEquals(4.0, latest.score(), 0.001);
        assertEquals("POSITIVE", latest.label());
    }

    @Test
    @DisplayName("latest is chosen by publication date, not by position in the array")
    void latestIgnoresArrayOrder() {
        // The stored array is maintained newest-first, but items whose date parses in neither
        // format sort to the end — so trusting position would occasionally pick the wrong article.
        LatestSentimentDto latest = service().computeLatest(record(item(1.0, 5), item(-3.0, 0.5)));

        assertEquals(-3.0, latest.score(), 0.001);
    }

    @Test
    @DisplayName("latest carries the date of the article it came from")
    void latestCarriesItsDate() {
        // Without the date this reading is indistinguishable from a current one. It is the only
        // protection against a months-old headline reading as fresh.
        NewsItem newest = item(2.0, 3);
        LatestSentimentDto latest = service().computeLatest(record(newest, item(1.0, 9)));

        assertEquals(newest.getPublishedAt(), latest.publishedAt());
    }

    @Test
    @DisplayName("latest is served however old it is")
    void latestIsNotAgeLimited() {
        // Deliberate reversal of the old behaviour, where an aggregate older than 30 days was
        // suppressed. That rule protected an average that falsely claimed to be current; this
        // reading claims only to be the last thing that happened, and its date is disclosed.
        LatestSentimentDto latest = service().computeLatest(record(item(3.0, 200)));

        assertEquals(3.0, latest.score(), 0.001);
        assertEquals("POSITIVE", latest.label());
    }

    @Test
    @DisplayName("latest falls through an unscored newest headline to the next one down")
    void latestSkipsUnscored() {
        // Reporting nothing because one article failed to score would present a scoring outage as
        // an absence of news.
        NewsItem unscored = item(0.0, 0.1);
        unscored.setSentimentScore(null);
        unscored.setSentimentLabel(null);

        assertEquals(2.0, service().computeLatest(record(unscored, item(2.0, 1))).score(), 0.001);
    }

    @Test
    @DisplayName("an undated article is used only when nothing datable is scored")
    void undatedIsLastResort() {
        NewsItem undated = item(5.0, 0);
        undated.setPublishedAt(null);
        undated.setDate("not a date at all");

        // A datable article exists, so it wins even though it is older.
        assertEquals(1.0, service().computeLatest(record(undated, item(1.0, 30))).score(), 0.001);

        // Nothing datable — fall back, and report a null date rather than inventing one.
        LatestSentimentDto only = service().computeLatest(record(undated));
        assertEquals(5.0, only.score(), 0.001);
        assertNull(only.publishedAt());
    }

    @Test
    @DisplayName("latest reports NO_DATA for an empty, absent or wholly unscored record")
    void latestNoData() {
        NewsItem unscored = item(0.0, 1);
        unscored.setSentimentScore(null);

        assertEquals("NO_DATA", service().computeLatest(null).label());
        assertEquals("NO_DATA", service().computeLatest(new CompanyNews()).label());
        assertEquals("NO_DATA", service().computeLatest(record()).label());
        assertEquals("NO_DATA", service().computeLatest(record(unscored)).label());
        assertNull(service().computeLatest(record(unscored)).score());
    }

    // ── quarter ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("quarter averages every scored headline of the last 90 days")
    void quarterAverages() {
        SentimentDto quarter = service().computeQuarter(
                record(item(4.0, 1), item(2.0, 40), item(0.0, 89)));

        assertEquals(2.0, quarter.score(), 0.001);
        assertEquals(3, quarter.articleCount());
    }

    @Test
    @DisplayName("quarter excludes anything older than 90 days")
    void quarterExcludesOlder() {
        SentimentDto quarter = service().computeQuarter(record(item(5.0, 10), item(-5.0, 91)));

        assertEquals(5.0, quarter.score(), 0.001);
        assertEquals(1, quarter.articleCount());
    }

    @Test
    @DisplayName("quarter excludes undated articles rather than assuming they belong")
    void quarterExcludesUndated() {
        NewsItem undated = item(-5.0, 0);
        undated.setPublishedAt(null);
        undated.setDate("not a date at all");

        SentimentDto quarter = service().computeQuarter(record(item(3.0, 5), undated));

        assertEquals(3.0, quarter.score(), 0.001);
        assertEquals(1, quarter.articleCount());
    }

    @Test
    @DisplayName("a company with only old news has a latest reading but no quarter")
    void latestWithoutQuarter() {
        // The two readings fail differently, and a row where they disagree like this is correct
        // rather than broken: there genuinely is no news in the quarter, but there was some news.
        CompanyNews record = record(item(3.0, 120));
        CompanySentimentDto both = service().computeFrom(record);

        assertEquals(3.0, both.latest().score(), 0.001);
        assertNull(both.quarter().score());
        assertEquals("NO_DATA", both.quarter().label());
        assertEquals(0, both.quarter().articleCount());
    }

    // ── denormalisation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh writes both readings onto the record")
    void refreshWritesColumns() {
        CompanyNews record = record(item(4.0, 1), item(-1.0, 10));
        service().refresh(record);

        assertEquals(4.0, record.getLatestScore(), 0.001);
        assertEquals("POSITIVE", record.getLatestLabel());
        assertTrue(record.getNewestArticleAt() != null);

        assertEquals(1.5, record.getQuarterScore(), 0.001);
        assertEquals("POSITIVE", record.getQuarterLabel());
        assertEquals(2, record.getQuarterCount());
    }

    @Test
    @DisplayName("refreshIfChanged reports false when nothing moved")
    void refreshIfChangedDetectsNoChange() {
        // This is what stops the hourly cleanup rewriting every JSONB blob in the table for
        // nothing. A false negative here would be a correctness bug; a false positive, a cost one.
        CompanyNews record = record(item(4.0, 1));
        service().refresh(record);

        assertFalse(service().refreshIfChanged(record));
    }

    @Test
    @DisplayName("refreshIfChanged reports true when the quarter ages out from under a quiet row")
    void refreshIfChangedDetectsQuarterDrift() {
        // The case the hourly recompute exists for: no new articles, no deletions, but the only
        // article has crossed the 90-day boundary and the stored average must now empty out.
        CompanyNews record = record(item(4.0, 91));
        record.setLatestScore(4.0);
        record.setLatestLabel("POSITIVE");
        record.setNewestArticleAt(record.getNews().get(0).getPublishedAt());
        record.setQuarterScore(4.0);            // what it said before the boundary was crossed
        record.setQuarterLabel("POSITIVE");
        record.setQuarterCount(1);

        assertTrue(service().refreshIfChanged(record));
        assertNull(record.getQuarterScore());
        assertEquals("NO_DATA", record.getQuarterLabel());
        assertEquals(4.0, record.getLatestScore(), 0.001);   // latest is unaffected by age
    }

    // ── batch read path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("batch lookup returns every requested keyword, including ones with no row")
    void batchSeedsMissingKeywords() {
        CurrentSentimentService svc = service();
        long now = System.currentTimeMillis();
        when(repository.findSentimentsByKeywordIn(anyCollection()))
                .thenReturn(List.of(projection("INFY", -3.0, "NEGATIVE", now, 0.4, "NEUTRAL", 22)));

        Map<String, CompanySentimentDto> result = svc.getForKeywords(List.of("INFY", "TCS"));

        assertEquals(2, result.size());
        assertEquals(-3.0, result.get("INFY").latest().score(), 0.001);
        assertEquals(now,  result.get("INFY").latest().publishedAt());
        assertEquals(0.4,  result.get("INFY").quarter().score(), 0.001);
        assertEquals(22,   result.get("INFY").quarter().articleCount());

        // TCS has no row at all. It must still be present, or the frontend cannot distinguish a
        // company with no news from a key it failed to request.
        assertEquals("NO_DATA", result.get("TCS").latest().label());
        assertEquals("NO_DATA", result.get("TCS").quarter().label());
    }

    @Test
    @DisplayName("a null column maps to NO_DATA independently for each reading")
    void batchHandlesPartialData() {
        CurrentSentimentService svc = service();
        when(repository.findSentimentsByKeywordIn(anyCollection()))
                .thenReturn(List.of(projection("INFY", 2.0, "POSITIVE",
                                               System.currentTimeMillis(), null, null, null)));

        CompanySentimentDto row = svc.getForKeywords(List.of("INFY")).get("INFY");

        assertEquals(2.0, row.latest().score(), 0.001);
        assertNull(row.quarter().score());
        assertEquals("NO_DATA", row.quarter().label());
    }

    /**
     * A plain implementation rather than a Mockito mock.
     *
     * <p>Stubbing a mock here would have to happen inside the {@code when(...).thenReturn(...)}
     * that stubs the repository, and Mockito treats nested stubbing as an error.
     */
    private record FakeProjection(String getKeyword,
                                  Double getLatestScore,
                                  String getLatestLabel,
                                  Long getNewestArticleAt,
                                  Double getQuarterScore,
                                  String getQuarterLabel,
                                  Integer getQuarterCount) implements SentimentProjection {}

    private static SentimentProjection projection(String keyword,
                                                  Double latestScore, String latestLabel,
                                                  Long newestAt,
                                                  Double quarterScore, String quarterLabel,
                                                  Integer quarterCount) {
        return new FakeProjection(keyword, latestScore, latestLabel, newestAt,
                                  quarterScore, quarterLabel, quarterCount);
    }
}
