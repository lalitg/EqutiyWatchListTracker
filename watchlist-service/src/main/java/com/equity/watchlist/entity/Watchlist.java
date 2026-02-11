package com.equity.watchlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a watchlist entry.
 * Maps to the 'watchlist' table in PostgreSQL.
 */
@Entity
@Table(name = "watchlist", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "company_code"})
})
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    @Column(name = "week_52_low", precision = 15, scale = 2)
    private BigDecimal week52Low;

    @Column(name = "week_52_high", precision = 15, scale = 2)
    private BigDecimal week52High;

    @Column(name = "all_time_low", precision = 15, scale = 2)
    private BigDecimal allTimeLow;

    @Column(name = "all_time_high", precision = 15, scale = 2)
    private BigDecimal allTimeHigh;

    @Column(name = "current_value", precision = 15, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "trend_sentiment", length = 50)
    private String trendSentiment;

    @Column(name = "pe_ratio", precision = 10, scale = 2)
    private BigDecimal peRatio;

    @Column(name = "eps", precision = 10, scale = 2)
    private BigDecimal eps;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Watchlist() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public BigDecimal getWeek52Low() { return week52Low; }
    public void setWeek52Low(BigDecimal week52Low) { this.week52Low = week52Low; }

    public BigDecimal getWeek52High() { return week52High; }
    public void setWeek52High(BigDecimal week52High) { this.week52High = week52High; }

    public BigDecimal getAllTimeLow() { return allTimeLow; }
    public void setAllTimeLow(BigDecimal allTimeLow) { this.allTimeLow = allTimeLow; }

    public BigDecimal getAllTimeHigh() { return allTimeHigh; }
    public void setAllTimeHigh(BigDecimal allTimeHigh) { this.allTimeHigh = allTimeHigh; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public String getTrendSentiment() { return trendSentiment; }
    public void setTrendSentiment(String trendSentiment) { this.trendSentiment = trendSentiment; }

    public BigDecimal getPeRatio() { return peRatio; }
    public void setPeRatio(BigDecimal peRatio) { this.peRatio = peRatio; }

    public BigDecimal getEps() { return eps; }
    public void setEps(BigDecimal eps) { this.eps = eps; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
