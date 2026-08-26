package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.SentimentDto;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Tests the top-N averaging that turns stored per-headline scores into a company's
 * current sentiment.
 *
 * <p>No model and no database: the aggregation is pure arithmetic over already-scored
 * items, which is exactly the property that lets the read path stay cheap in production.
 */
class CurrentSentimentServiceTest {

    private static final int TOP_N = 5;

    private CurrentSentimentService service() {
        // The scorer is only used here for toLabel(); its model client is never invoked.
        SentimentScorer scorer = new SentimentScorer(mock(SentimentModelClient.class), 1.5, -1.5);
        return new CurrentSentimentService(mock(CompanyNewsRepository.class), scorer, TOP_N);
    }

    /** Builds a record whose items carry the given scores, newest first. */
    private static CompanyNews recordWithScores(Double... scores) {
        List<NewsItem> items = new ArrayList<>();
        for (Double score : scores) {
            NewsItem item = new NewsItem("Mon, 01 Jan 2026 10:00:00 GMT", "headline", "https://x/" + items.size());
            item.setSentimentScore(score);
            items.add(item);
        }
        CompanyNews record = new CompanyNews();
        record.setKeyword("TESTCO");
        record.setNews(items);
        return record;
    }

    @Test
    @DisplayName("averages the newest N scores")
    void averagesTopN() {
        SentimentDto result = service().computeFrom(recordWithScores(4.0, 2.0, 3.0));

        assertEquals(3.0, result.score(), 0.001);
        assertEquals("POSITIVE", result.label());
        assertEquals(3, result.articleCount());
    }

    @Test
    @DisplayName("ignores anything older than the newest N")
    void ignoresBeyondTopN() {
        // Six items, all +5 except the last which is -5. With N=5 the stale item must not
        // contribute — this is what keeps the reading "current" rather than historical.
        SentimentDto result = service().computeFrom(
                recordWithScores(5.0, 5.0, 5.0, 5.0, 5.0, -5.0));

        assertEquals(5.0, result.score(), 0.001);
        assertEquals(5, result.articleCount());
    }

    @Test
    @DisplayName("skips unscored items instead of counting them as neutral")
    void skipsNullScores() {
        // Treating null as 0.0 would pull this company from +4.0 down to +1.33 purely
        // because two headlines had not been scored yet — a data-availability artefact
        // masquerading as a sentiment signal.
        SentimentDto result = service().computeFrom(recordWithScores(4.0, null, null));

        assertEquals(4.0, result.score(), 0.001);
        assertEquals(1, result.articleCount());
    }

    @Test
    @DisplayName("reports NO_DATA when nothing is scored")
    void allUnscoredIsNoData() {
        SentimentDto result = service().computeFrom(recordWithScores(null, null));

        assertNull(result.score());
        assertEquals("NO_DATA", result.label());
        assertEquals(0, result.articleCount());
    }

    @Test
    @DisplayName("reports NO_DATA for an empty or absent record")
    void emptyRecordIsNoData() {
        assertEquals("NO_DATA", service().computeFrom(null).label());
        assertEquals("NO_DATA", service().computeFrom(new CompanyNews()).label());
        assertEquals("NO_DATA", service().computeFrom(recordWithScores()).label());
    }

    @Test
    @DisplayName("opposing scores average toward neutral")
    void opposingScoresCancel() {
        // The symmetric -5..+5 scale makes this exact. On an asymmetric range the same
        // balanced input would have settled at a positive value.
        SentimentDto result = service().computeFrom(recordWithScores(5.0, -5.0));

        assertEquals(0.0, result.score(), 0.001);
        assertEquals("NEUTRAL", result.label());
        assertEquals(2, result.articleCount());
    }
}
