package com.companynews.newsscheduler.model;

import com.companynews.newsscheduler.dto.NewsItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "company_news")
public class CompanyNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This maps to the keyword column — UNIQUE means one row per company symbol
    @Column(name = "keyword", unique = true, nullable = false)
    private String keyword;

    // sentiments column — empty for now, future phase
    @Column(name = "sentiments")
    private String sentiments;

    // This maps to the news JSONB column
    // @JdbcTypeCode(SqlTypes.JSON) tells Hibernate to store/read this as JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "news", columnDefinition = "jsonb")
    private List<NewsItem> news;

    // Timestamp of last update
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getSentiments() { return sentiments; }
    public void setSentiments(String sentiments) { this.sentiments = sentiments; }

    public List<NewsItem> getNews() { return news; }
    public void setNews(List<NewsItem> news) { this.news = news; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
