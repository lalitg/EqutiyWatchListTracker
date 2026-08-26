package com.companynews.newsscheduler.dto;

/**
 * A keyword's current sentiment, as served to the frontend.
 *
 * <p>Returned both as the {@code currentSentiment} field of {@code GET /api/news?key=} and as
 * each value in the batch map from {@code GET /api/news/sentiment?keys=}. Deliberately small —
 * the batch endpoint may carry fifty of these for a single index table, so it holds no news
 * arrays or article text.
 *
 * <h2>Step 1 semantics</h2>
 * {@code score} is the mean of the newest {@code articleCount} scored headlines for the keyword
 * (capped by {@code sentiment.current.top-n}). It intentionally ignores article age: a headline
 * from an hour ago and one from six days ago count equally. The time-weighted cumulative score
 * replaces this in Step 2, at which point only the computation behind this DTO changes — the
 * wire format and every frontend consumer stay as they are.
 *
 * @param score        mean of the contributing headline scores on the -5.0..+5.0 scale, or
 *                     {@code null} when nothing could be scored
 * @param label        {@code POSITIVE} / {@code NEGATIVE} / {@code NEUTRAL}, or {@code NO_DATA}
 *                     when {@code score} is {@code null}
 * @param articleCount how many scored headlines contributed. Exposed so the UI can distinguish
 *                     a confident reading from one resting on a single article, and so a
 *                     keyword with no coverage is never shown as a confident neutral
 */
public record SentimentDto(Double score, String label, int articleCount) {

    /** Label used when a keyword has no scored articles at all. */
    public static final String LABEL_NO_DATA = "NO_DATA";

    /** Shared instance for keywords with nothing to report. */
    public static SentimentDto noData() {
        return new SentimentDto(null, LABEL_NO_DATA, 0);
    }
}
