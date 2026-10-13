package com.companynews.newsscheduler.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * One company's news sentiment for one calendar day — the rollup behind the Extremes page.
 *
 * <h2>Why a table rather than computing on read</h2>
 * The sentiment for any past day <em>is</em> derivable from stored articles: every one carries a
 * publication instant and a score, and company news is retained for ninety days. But the Extremes
 * page ranks <b>every company against every other</b>, so deriving it per request means expanding
 * the JSONB of all ~2,200 companies on a page anyone can open — tens of megabytes and well over a
 * hundred thousand array elements, to produce twenty rows.
 *
 * <h2>Why the sum and the count, not the average</h2>
 * Storing only a daily average would make the Cumulative tab wrong. Averaging seven daily averages
 * weights a day carrying one article exactly as heavily as a day carrying twenty. Keeping the sum
 * and the count preserves the arithmetic: a day is {@code sum / count}, and any range is
 * {@code Σsum / Σcount} — article-weighted, and therefore equal to what the company's own Sentiments
 * tab reports for the same span.
 *
 * <h2>Nothing shifts at midnight</h2>
 * Rows are keyed by date, so the row for 7 September holds 7 September's sentiment permanently.
 * "Today" and "Yesterday" are labels the UI applies at render time, not values that move. There is
 * no nightly shift job that could run late, run twice, or fail — an entire class of bug that a
 * rolling design would have introduced.
 *
 * <h2>Derived, and rebuildable</h2>
 * Everything here can be recomputed from {@code company_news}. Retention matches the ninety-day
 * article window precisely so that stays true; keeping daily rows longer than the articles behind
 * them would turn this into a primary record that nothing could verify.
 */
@Entity
@Table(
    name = "company_daily_sentiment",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_sentiment_keyword_day",
        columnNames = { "keyword", "day" }),
    indexes = {
        // The Single Day board filters by day and ranks within it; ~1,000 rows per day at full
        // scale, so an index on the date is the whole access path.
        @Index(name = "idx_daily_sentiment_day", columnList = "day"),
        @Index(name = "idx_daily_sentiment_keyword", columnList = "keyword")
    })
public class CompanyDailySentiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Company symbol. Only company keywords appear here — sectors and macro terms are never scored. */
    @Column(name = "keyword", nullable = false, length = 64)
    private String keyword;

    /** The calendar day in {@code Asia/Kolkata}, the timezone the market and schedulers run on. */
    @Column(name = "day", nullable = false)
    private LocalDate day;

    /** Sum of the scores of every scored article published that day. */
    @Column(name = "score_sum", nullable = false)
    private Double scoreSum;

    /**
     * How many scored articles that sum covers.
     *
     * <p>Never zero: a company with no news on a day has no row at all, which is what keeps it out
     * of that day's ranking rather than appearing as a neutral zero.
     */
    @Column(name = "article_count", nullable = false)
    private Integer articleCount;

    public CompanyDailySentiment() {}

    public CompanyDailySentiment(String keyword, LocalDate day, Double scoreSum, Integer articleCount) {
        this.keyword      = keyword;
        this.day          = day;
        this.scoreSum     = scoreSum;
        this.articleCount = articleCount;
    }

    /** @return surrogate primary key */
    public Long getId() { return id; }

    /** @param id surrogate primary key */
    public void setId(Long id) { this.id = id; }

    /** @return the company symbol */
    public String getKeyword() { return keyword; }

    /** @param keyword the company symbol */
    public void setKeyword(String keyword) { this.keyword = keyword; }

    /** @return the calendar day this row summarises, in IST */
    public LocalDate getDay() { return day; }

    /** @param day the calendar day, in IST */
    public void setDay(LocalDate day) { this.day = day; }

    /** @return sum of that day's article scores */
    public Double getScoreSum() { return scoreSum; }

    /** @param scoreSum sum of that day's article scores */
    public void setScoreSum(Double scoreSum) { this.scoreSum = scoreSum; }

    /** @return how many scored articles contributed */
    public Integer getArticleCount() { return articleCount; }

    /** @param articleCount how many scored articles contributed */
    public void setArticleCount(Integer articleCount) { this.articleCount = articleCount; }

    /**
     * The day's mean sentiment.
     *
     * @return {@code scoreSum / articleCount}, or {@code null} if the row somehow holds no articles
     */
    @Transient
    public Double average() {
        if (articleCount == null || articleCount == 0 || scoreSum == null) return null;
        return scoreSum / articleCount;
    }
}
