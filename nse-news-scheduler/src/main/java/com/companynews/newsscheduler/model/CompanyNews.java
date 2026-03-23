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
}
