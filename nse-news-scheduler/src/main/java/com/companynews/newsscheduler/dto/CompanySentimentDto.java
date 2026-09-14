package com.companynews.newsscheduler.dto;

/**
 * The pair of sentiment readings shown per company in the watchlist and the index / sector tables.
 *
 * <h2>Why two numbers rather than one</h2>
 * A single figure cannot answer both questions a reader has. {@link #latest} is the reaction to the
 * most recent piece of news; {@link #quarter} is the backdrop it landed against. They routinely
 * disagree — one bad headline today against a flat quarter — and that disagreement is the useful
 * signal, not a contradiction to be averaged away.
 *
 * <p>They also fail differently, which is worth knowing when reading a row: a company whose only
 * news is older than ninety days shows a real {@link #latest} and {@code NO_DATA} for
 * {@link #quarter}. Nothing is wrong in that case — the quarter genuinely contains no news.
 *
 * <p>Both are served from denormalised columns rather than computed from the stored article array,
 * because these tables render fifty-plus companies at once. See
 * {@link com.companynews.newsscheduler.repository.SentimentProjection}.
 *
 * @param latest  sentiment of the single most recent scored headline, with its date
 * @param quarter plain average across every scored headline of the last 90 days
 */
public record CompanySentimentDto(LatestSentimentDto latest, SentimentDto quarter) {

    /** Shared instance for a keyword with no row or no scored news. */
    public static CompanySentimentDto noData() {
        return new CompanySentimentDto(LatestSentimentDto.noData(), SentimentDto.noData());
    }
}
