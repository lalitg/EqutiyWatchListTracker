package com.companynews.newsscheduler.model;

import com.companynews.newsscheduler.dto.NewsItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA entity representing one row in the {@code company_news} table.
 *
 * <p>Each row stores all news items for a single keyword (company symbol, sector name,
 * or macro keyword). The {@code keyword} column has a UNIQUE constraint, so there is
 * exactly one row per keyword.
 *
 * <p>The {@code news} column is stored as PostgreSQL {@code JSONB}, allowing efficient
 * querying and indexing of the embedded JSON array. Hibernate serializes and deserializes
 * the {@code List<NewsItem>} automatically using the {@link JdbcTypeCode} mapping.
 *
 * <p>The {@code sentiments} field is reserved for a future sentiment analysis phase
 * and is currently always stored as an empty string.
 */
@Entity
@Table(name = "company_news")
public class CompanyNews {

    /**
     * Auto-generated surrogate primary key. Not exposed in API responses.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The keyword identifying this record (company symbol, sector, or macro term).
     * Maps to the {@code keyword} VARCHAR column with a UNIQUE constraint.
     * One row exists per unique keyword.
     */
    @Column(name = "keyword", unique = true, nullable = false)
    private String keyword;

    /**
     * Reserved for a future sentiment analysis feature.
     * Currently stored as an empty string for all records.
     */
    @Column(name = "sentiments")
    private String sentiments;

    /**
     * The list of news articles stored as a JSONB array in the database.
     * Each element is a {@link NewsItem} containing date, summary, and link.
     * Trimmed to a configurable limit (default 5) with newest articles first.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "news", columnDefinition = "jsonb")
    private List<NewsItem> news;

    /**
     * Denormalised score of the single most recent scored headline.
     *
     * <h2>Why these readings are duplicated out of the JSONB</h2>
     * The watchlist and the Nifty index / sector company tables ask for sentiment on fifty-plus
     * companies in one request. Deriving it from {@code news} means Hibernate loads and Jackson
     * deserialises every stored article of every one of those companies — thousands of objects —
     * so that the service can produce a handful of numbers. With company news retained for a
     * quarter that read is roughly an order of magnitude larger than it was under seven-day
     * retention, while the answer stays the same few values.
     *
     * <p>Holding the answers in plain columns lets
     * {@link com.companynews.newsscheduler.repository.CompanyNewsRepository#findSentimentsByKeywordIn}
     * serve those tables with a projection that never touches the JSONB at all.
     *
     * <p>Written by {@link com.companynews.newsscheduler.service.CurrentSentimentService#refresh}
     * wherever the article list changes — save, cleanup and backfill — so the columns can never
     * disagree with the array they summarise. {@code null} means no scored article exists, which
     * is distinct from a score of {@code 0.0} meaning genuinely neutral news.
     */
    @Column(name = "latest_score")
    private Double latestScore;

    /**
     * Band matching {@link #latestScore} — {@code POSITIVE} / {@code NEGATIVE} / {@code NEUTRAL},
     * or {@code NO_DATA} when there is no scored article.
     */
    @Column(name = "latest_label", length = 16)
    private String latestLabel;

    /**
     * Publication instant of the article behind {@link #latestScore}, in epoch milliseconds.
     *
     * <p>Served to the UI rather than used to suppress anything. The latest reading is deliberately
     * not age-limited — it describes the last thing that happened, which stays true however long ago
     * that was — so the protection against stale data reading as fresh comes from showing this date,
     * not from hiding the score.
     *
     * <p>{@code null} when no article is scored, or when the newest scored article carries a date
     * that parses in neither supported format.
     */
    @Column(name = "newest_article_at")
    private Long newestArticleAt;

    /**
     * Denormalised average score across every scored headline of the last 90 days.
     *
     * <h2>Why a time-dependent value can live in a column at all</h2>
     * Unlike {@link #latestScore}, this changes with the passage of time rather than with new data:
     * articles keep falling out of the ninety-day window even while a company sits silent, so a
     * naively stored average would drift.
     *
     * <p>It works here because the retention window is also ninety days, so an article leaves this
     * average at the moment cleanup deletes it — and cleanup refreshes the row it just changed. The
     * one gap is the min-count floor, which force-keeps the newest fifteen articles regardless of
     * age: such a row never changes and would never refresh. {@code NewsCleanupService} closes that
     * by recomputing every row hourly and writing only when a value actually moved, which bounds the
     * error at one hour without adding writes.
     */
    @Column(name = "quarter_score")
    private Double quarterScore;

    /** Band matching {@link #quarterScore}, or {@code NO_DATA} when the quarter holds no news. */
    @Column(name = "quarter_label", length = 16)
    private String quarterLabel;

    /**
     * How many scored headlines contributed to {@link #quarterScore}.
     *
     * <p>Served to the UI so a reading resting on one article is distinguishable from one resting on
     * forty. Zero whenever {@link #quarterScore} is {@code null}.
     */
    @Column(name = "quarter_count")
    private Integer quarterCount;

    /**
     * Timestamp of the last update to this record.
     * Set by the application on every save — not managed by the database trigger.
     * Formatted as {@code yyyy-MM-dd'T'HH:mm:ss} in API responses.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /**
     * Returns the surrogate primary key.
     *
     * @return the record ID
     */
    public Long getId() { return id; }

    /**
     * Sets the surrogate primary key.
     *
     * @param id the record ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * Returns the keyword identifying this news record.
     *
     * @return keyword string
     */
    public String getKeyword() { return keyword; }

    /**
     * Sets the keyword identifying this news record.
     *
     * @param keyword keyword string (company symbol, sector, or macro term)
     */
    public void setKeyword(String keyword) { this.keyword = keyword; }

    /**
     * Returns the sentiment label for this keyword (currently always empty).
     *
     * @return sentiments string
     */
    public String getSentiments() { return sentiments; }

    /**
     * Sets the sentiment label for this keyword.
     *
     * @param sentiments the sentiment string
     */
    public void setSentiments(String sentiments) { this.sentiments = sentiments; }

    /**
     * Returns the list of news items stored in the JSONB column.
     *
     * @return list of {@link NewsItem} objects, or {@code null} if not yet populated
     */
    public List<NewsItem> getNews() { return news; }

    /**
     * Sets the list of news items to be stored in the JSONB column.
     *
     * @param news list of {@link NewsItem} objects
     */
    public void setNews(List<NewsItem> news) { this.news = news; }

    /**
     * Returns the timestamp of the last update to this record.
     *
     * @return last updated timestamp
     */
    public LocalDateTime getLastUpdated() { return lastUpdated; }

    /**
     * Sets the timestamp of the last update to this record.
     *
     * @param lastUpdated the last updated timestamp
     */
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    /**
     * Returns the score of the most recent scored headline, on the -5.0..+5.0 scale.
     *
     * @return the score, or {@code null} if the company has no scored article
     */
    public Double getLatestScore() { return latestScore; }

    /**
     * Sets the score of the most recent scored headline.
     *
     * @param latestScore score on the -5.0..+5.0 scale, or {@code null}
     */
    public void setLatestScore(Double latestScore) { this.latestScore = latestScore; }

    /**
     * Returns the band for the most recent scored headline.
     *
     * @return {@code POSITIVE} / {@code NEGATIVE} / {@code NEUTRAL} / {@code NO_DATA}
     */
    public String getLatestLabel() { return latestLabel; }

    /**
     * Sets the band for the most recent scored headline.
     *
     * @param latestLabel the band string
     */
    public void setLatestLabel(String latestLabel) { this.latestLabel = latestLabel; }

    /**
     * Returns the publication instant of the newest scored article, in epoch milliseconds.
     *
     * @return epoch millis, or {@code null} if unknown
     */
    public Long getNewestArticleAt() { return newestArticleAt; }

    /**
     * Sets the publication instant of the newest scored article.
     *
     * @param newestArticleAt epoch millis, or {@code null}
     */
    public void setNewestArticleAt(Long newestArticleAt) { this.newestArticleAt = newestArticleAt; }

    /**
     * Returns the average score across scored headlines of the last 90 days.
     *
     * @return the average, or {@code null} if the quarter holds no scored article
     */
    public Double getQuarterScore() { return quarterScore; }

    /**
     * Sets the 90-day average score.
     *
     * @param quarterScore average on the -5.0..+5.0 scale, or {@code null}
     */
    public void setQuarterScore(Double quarterScore) { this.quarterScore = quarterScore; }

    /**
     * Returns the band for the 90-day average.
     *
     * @return {@code POSITIVE} / {@code NEGATIVE} / {@code NEUTRAL} / {@code NO_DATA}
     */
    public String getQuarterLabel() { return quarterLabel; }

    /**
     * Sets the band for the 90-day average.
     *
     * @param quarterLabel the band string
     */
    public void setQuarterLabel(String quarterLabel) { this.quarterLabel = quarterLabel; }

    /**
     * Returns how many scored headlines contributed to the 90-day average.
     *
     * @return contributing article count, {@code 0} when there is no reading
     */
    public Integer getQuarterCount() { return quarterCount; }

    /**
     * Sets how many scored headlines contributed to the 90-day average.
     *
     * @param quarterCount contributing article count
     */
    public void setQuarterCount(Integer quarterCount) { this.quarterCount = quarterCount; }
}
