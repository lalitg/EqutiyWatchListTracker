package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.NewsItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Turns model probabilities into the {@code -5.0 .. +5.0} score and label stored on each
 * {@link NewsItem}.
 *
 * <p>This class deliberately contains no inference and no I/O. The arithmetic is pure and
 * deterministic so it can be unit-tested without loading a 110 MB model, and so that a change
 * to the scale or the thresholds is reviewable in isolation from the model itself.
 *
 * <h2>Why the probability difference rather than the winning label</h2>
 * Taking the argmax would collapse every headline into one of three values (-5, 0, +5),
 * throwing away how confident the model was and making downstream averages coarse and jumpy.
 * Using {@code p_positive - p_negative} keeps the score continuous: a hedged headline yields a
 * small magnitude, a decisive one a large magnitude. That matters because Step 1 averages the
 * newest few scores, and Step 2 will apply a time-weighted formula over them.
 *
 * <h2>Why the scale is symmetric</h2>
 * An earlier design used an asymmetric {@code -5 .. +10} range. That was dropped because a
 * keyword receiving equal amounts of strongly-positive and strongly-negative news converges to
 * the midpoint of the range, so genuinely mixed news would have read as mildly positive. On a
 * symmetric scale, balanced news correctly converges to 0 and a single linear factor suffices.
 */
@Service
public class SentimentScorer {

    private static final Logger log = LogManager.getLogger(SentimentScorer.class);

    public static final String LABEL_POSITIVE = "POSITIVE";
    public static final String LABEL_NEGATIVE = "NEGATIVE";
    public static final String LABEL_NEUTRAL  = "NEUTRAL";

    /** Half-width of the score range; {@code (p_pos - p_neg)} spans -1..+1 before scaling. */
    private static final double SCALE = 5.0;

    private final SentimentModelClient modelClient;

    /** Score at or above which a headline is labelled POSITIVE. */
    private final double positiveThreshold;

    /** Score at or below which a headline is labelled NEGATIVE. */
    private final double negativeThreshold;

    public SentimentScorer(SentimentModelClient modelClient,
                           @Value("${sentiment.threshold.positive:1.5}") double positiveThreshold,
                           @Value("${sentiment.threshold.negative:-1.5}") double negativeThreshold) {
        this.modelClient       = modelClient;
        this.positiveThreshold = positiveThreshold;
        this.negativeThreshold = negativeThreshold;
    }

    /**
     * Scores one headline and writes the result onto the item in place.
     *
     * <p>On any failure the item is left with {@code null} sentiment fields rather than a
     * fabricated neutral value. This distinction matters downstream: "we could not score this"
     * and "this headline is neutral" are different facts, and conflating them would drag
     * aggregate scores toward zero for reasons that have nothing to do with the news.
     *
     * @param item the news item to score; its {@code summary} supplies the text
     */
    public void score(NewsItem item) {
        if (item == null) return;

        double[] probabilities = modelClient.predict(item.getSummary());
        if (probabilities == null) {
            item.setSentimentScore(null);
            item.setSentimentLabel(null);
            return;
        }

        double score = toScore(probabilities);
        item.setSentimentScore(round2(score));
        item.setSentimentLabel(toLabel(score));

        if (log.isDebugEnabled()) {
            log.debug("Scored [{} {}] {}", item.getSentimentLabel(), item.getSentimentScore(),
                      item.getSummary());
        }
    }

    /**
     * Maps canonical probabilities onto the {@code -5.0 .. +5.0} scale.
     *
     * @param probabilities canonical order — {@code [positive, neutral, negative]}
     * @return the score; exactly {@code 0.0} when positive and negative probabilities are equal
     */
    public double toScore(double[] probabilities) {
        double positive = probabilities[SentimentModelClient.POSITIVE];
        double negative = probabilities[SentimentModelClient.NEGATIVE];
        return (positive - negative) * SCALE;
    }

    /**
     * Converts a score into its display band.
     *
     * <p>Thresholds are configurable rather than hardcoded because the right cut-off depends on
     * how the model behaves on our own headlines, which is still being tuned.
     *
     * @param score a value on the {@code -5.0 .. +5.0} scale
     * @return {@link #LABEL_POSITIVE}, {@link #LABEL_NEGATIVE} or {@link #LABEL_NEUTRAL}
     */
    public String toLabel(double score) {
        if (score >= positiveThreshold) return LABEL_POSITIVE;
        if (score <= negativeThreshold) return LABEL_NEGATIVE;
        return LABEL_NEUTRAL;
    }

    /** Whether the underlying model is loaded and able to score. */
    public boolean isAvailable() {
        return modelClient.isAvailable();
    }

    /**
     * Rounds to two decimals.
     *
     * <p>Storing full double precision would imply a level of accuracy the model does not have —
     * roughly one headline in four is misclassified — and would make stored JSON noisier to read.
     */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
