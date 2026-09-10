package com.companynews.newsscheduler.repository;

/**
 * Read-only projection of a keyword's denormalised sentiment columns.
 *
 * <h2>Why a projection rather than the entity</h2>
 * {@link CompanyNewsRepository#findByKeywordIn} returns full {@link
 * com.companynews.newsscheduler.model.CompanyNews} rows, which means the {@code news} JSONB column
 * is fetched and deserialised into {@code NewsItem} objects for every keyword requested. The batch
 * sentiment endpoint serves the watchlist and the Nifty index / sector tables, which ask about
 * fifty-plus companies at a time, and it needs six scalar values per company. With company news
 * retained for a quarter, the entity path would transfer and deserialise several megabytes of
 * article text per page view to produce a few hundred bytes of answer.
 *
 * <p>Spring Data materialises this interface directly from the selected columns, so the generated
 * SQL lists only those columns and the JSONB is never read.
 *
 * @see CompanyNewsRepository#findSentimentsByKeywordIn(java.util.Collection)
 */
public interface SentimentProjection {

    /** @return the company symbol, sector name, or macro term this row belongs to */
    String getKeyword();

    /** @return score of the most recent scored headline, or {@code null} if there is none */
    Double getLatestScore();

    /** @return band for the most recent scored headline, or {@code NO_DATA} */
    String getLatestLabel();

    /**
     * @return publication instant of that headline in epoch milliseconds, or {@code null}. Served
     *         to the UI so the reader can see how old the reading is — it is not used to suppress
     *         anything
     */
    Long getNewestArticleAt();

    /** @return average score across scored headlines of the last 90 days, or {@code null} */
    Double getQuarterScore();

    /** @return band for the 90-day average, or {@code NO_DATA} */
    String getQuarterLabel();

    /** @return how many scored headlines contributed to the 90-day average */
    Integer getQuarterCount();
}
