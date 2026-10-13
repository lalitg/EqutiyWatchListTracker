package com.companynews.newsscheduler.repository;

/**
 * One row of a sentiment leaderboard, straight from the database.
 *
 * <p>A projection rather than an entity because a board keeps ten rows out of potentially thousands
 * of candidates. Materialising every candidate as a managed entity, only to discard all but ten,
 * would be wasted work on a path any visitor can trigger — and none of the entity's other fields
 * are ever read.
 *
 * <p>Shared by both tabs: the Single Day board projects it out of the daily rollup, the Cumulative
 * board out of the denormalised columns on {@code company_news}. Same three values either way, so
 * the service that assembles the page needs no knowledge of which source it came from.
 */
public interface RankedSentimentProjection {

    /** @return the company symbol */
    String getKeyword();

    /** @return mean sentiment on the -5.0..+5.0 scale for the day or period being ranked */
    Double getScore();

    /**
     * @return how many scored articles the score rests on.
     *         <p>Load-bearing rather than informational: there is no minimum article count, so this
     *         is the reader's only way to tell a twenty-article consensus from a single loud
     *         headline that happens to sit at the same place on the scale.
     */
    Integer getArticleCount();
}
