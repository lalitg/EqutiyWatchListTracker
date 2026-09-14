package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.NewsItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the score and label arithmetic.
 *
 * <p>The model is mocked throughout: these tests pin the mapping rules, not the model's
 * judgement. Keeping them model-free means they run in milliseconds, need no 219 MB artefact,
 * and fail only when the arithmetic actually changes.
 */
class SentimentScorerTest {

    /** Builds a scorer whose model returns a fixed probability distribution. */
    private SentimentScorer scorerReturning(double positive, double neutral, double negative) {
        SentimentModelClient client = mock(SentimentModelClient.class);
        when(client.predict(anyString())).thenReturn(new double[]{ positive, neutral, negative });
        when(client.isAvailable()).thenReturn(true);
        return new SentimentScorer(client, 1.5, -1.5);
    }

    private static NewsItem item(String headline) {
        NewsItem newsItem = new NewsItem();
        newsItem.setSummary(headline);
        return newsItem;
    }

    @Test
    @DisplayName("strongly positive probabilities map near the top of the scale")
    void strongPositive() {
        SentimentScorer scorer = scorerReturning(0.95, 0.03, 0.02);
        NewsItem newsItem = item("Profit beats estimates");
        scorer.score(newsItem);

        assertEquals(4.65, newsItem.getSentimentScore(), 0.001);
        assertEquals("POSITIVE", newsItem.getSentimentLabel());
    }

    @Test
    @DisplayName("strongly negative probabilities map near the bottom of the scale")
    void strongNegative() {
        SentimentScorer scorer = scorerReturning(0.02, 0.03, 0.95);
        NewsItem newsItem = item("Shares plunge on weak guidance");
        scorer.score(newsItem);

        assertEquals(-4.65, newsItem.getSentimentScore(), 0.001);
        assertEquals("NEGATIVE", newsItem.getSentimentLabel());
    }

    @Test
    @DisplayName("equal positive and negative probabilities map to exactly zero")
    void balancedMapsToZero() {
        // The scale is symmetric precisely so this holds. On the earlier -5..+10 range the
        // same input would have landed at +2.5, making genuinely mixed news read as positive.
        SentimentScorer scorer = scorerReturning(0.10, 0.80, 0.10);
        NewsItem newsItem = item("Board meeting scheduled");
        scorer.score(newsItem);

        assertEquals(0.0, newsItem.getSentimentScore(), 0.001);
        assertEquals("NEUTRAL", newsItem.getSentimentLabel());
    }

    @Test
    @DisplayName("score magnitude tracks model confidence, not just the winning class")
    void hedgedHeadlineScoresSmall() {
        // A hedged 45/45/10 split and a decisive 95/3/2 split both have "positive" as the
        // argmax. Using the probability difference keeps them distinguishable, which is what
        // makes the downstream average meaningful rather than three-valued.
        SentimentScorer confident = scorerReturning(0.95, 0.03, 0.02);
        SentimentScorer hedged    = scorerReturning(0.45, 0.10, 0.45);

        NewsItem confidentItem = item("Profit doubles");
        NewsItem hedgedItem    = item("Results broadly in line");
        confident.score(confidentItem);
        hedged.score(hedgedItem);

        assertEquals(4.65, confidentItem.getSentimentScore(), 0.001);
        assertEquals(0.0,  hedgedItem.getSentimentScore(),    0.001);
        assertEquals("NEUTRAL", hedgedItem.getSentimentLabel());
    }

    @Test
    @DisplayName("thresholds are inclusive at the boundary")
    void thresholdBoundaries() {
        SentimentScorer scorer = scorerReturning(0.5, 0.5, 0.5);   // values unused below

        assertEquals("POSITIVE", scorer.toLabel(1.5));
        assertEquals("NEUTRAL",  scorer.toLabel(1.49));
        assertEquals("NEGATIVE", scorer.toLabel(-1.5));
        assertEquals("NEUTRAL",  scorer.toLabel(-1.49));
        assertEquals("NEUTRAL",  scorer.toLabel(0.0));
    }

    @Test
    @DisplayName("an unavailable model leaves the item unscored rather than neutral")
    void unavailableModelLeavesNull() {
        // This distinction matters downstream: CurrentSentimentService skips null scores when
        // averaging. Defaulting to 0.0 here would silently drag every company's sentiment
        // toward neutral whenever the model was missing.
        SentimentModelClient client = mock(SentimentModelClient.class);
        when(client.predict(anyString())).thenReturn(null);
        SentimentScorer scorer = new SentimentScorer(client, 1.5, -1.5);

        NewsItem newsItem = item("Some headline");
        scorer.score(newsItem);

        assertNull(newsItem.getSentimentScore());
        assertNull(newsItem.getSentimentLabel());
    }

    @Test
    @DisplayName("scoring a null item does not throw")
    void nullItemIsSafe() {
        scorerReturning(0.9, 0.05, 0.05).score(null);
    }
}
