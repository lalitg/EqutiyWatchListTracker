package com.companynews.newsscheduler.dto;

/**
 * The sentiment of a company's single most recent scored headline.
 *
 * <h2>Why this is a separate type from {@link SentimentDto}</h2>
 * {@link SentimentDto} describes an <em>average over a period</em> and carries an article count to
 * say how much evidence sits behind it. This describes <em>one specific article</em>, so a count
 * would always be 1 and tell the reader nothing. What matters here instead is <b>when</b> that
 * article was published — which is why {@link #publishedAt} exists and why no average carries it.
 *
 * <h2>Deliberately not age-limited</h2>
 * A period average that never expires is a trap: nothing rewrites a quiet company's score, so it
 * would keep serving a confident reading forever. That is why the aggregate readings used to be
 * suppressed past a cut-off.
 *
 * <p>This reading does not need that protection, because it does not claim to be current. It claims
 * to be the last thing that happened, which stays true however long ago it was. The obligation the
 * cut-off was discharging — never letting stale data masquerade as fresh — is met here by
 * <b>disclosing the date</b> rather than by hiding the value. The UI shows it, so a three-month-old
 * reading reads as three months old.
 *
 * @param score       sentiment of that one headline on the -5.0..+5.0 scale, or {@code null} when
 *                    the company has no scored article at all
 * @param label       {@code POSITIVE} / {@code NEGATIVE} / {@code NEUTRAL}, or {@code NO_DATA}
 * @param publishedAt publication instant of that headline in epoch milliseconds, or {@code null}
 *                    when unknown. Never omit it from the UI: without the date this reading is
 *                    indistinguishable from a current one
 */
public record LatestSentimentDto(Double score, String label, Long publishedAt) {

    /** Shared instance for a company with no scored article. */
    public static LatestSentimentDto noData() {
        return new LatestSentimentDto(null, SentimentDto.LABEL_NO_DATA, null);
    }
}
