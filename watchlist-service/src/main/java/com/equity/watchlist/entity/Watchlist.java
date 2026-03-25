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
 * JPA entity representing a single entry in a user's equity watchlist.
 *
 * <p>Maps to the {@code watchlist} table in PostgreSQL. Each row is uniquely
 * identified by the combination of {@code user_id} and {@code company_code}.
 * Price fields are populated from the global-watchlist-service at insert time
 * and may be updated manually or via re-import.
 */
@Entity
@Table(name = "watchlist", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "company_code"})
})
public class Watchlist {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the user who owns this watchlist entry. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** NSE stock symbol (e.g. {@code "INFY"}, {@code "RELIANCE"}). */
    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    /** Lowest price over the past 52 weeks. */
    @Column(name = "week_52_low", precision = 15, scale = 2)
    private BigDecimal week52Low;

    /** Highest price over the past 52 weeks. */
    @Column(name = "week_52_high", precision = 15, scale = 2)
    private BigDecimal week52High;

    /** All-time low price recorded in the global watchlist. */
    @Column(name = "all_time_low", precision = 15, scale = 2)
    private BigDecimal allTimeLow;

    /** All-time high price recorded in the global watchlist. */
    @Column(name = "all_time_high", precision = 15, scale = 2)
    private BigDecimal allTimeHigh;

    /** Latest traded price at the time the entry was last updated. */
    @Column(name = "current_value", precision = 15, scale = 2)
    private BigDecimal currentValue;

    /** Total traded volume for the latest session. */
    @Column(name = "traded_volume", precision = 20, scale = 2)
    private BigDecimal tradedVolume;

    /** Timestamp when this entry was first added to the watchlist. */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Default constructor. Sets {@link #createdAt} to the current timestamp.
     */
    public Watchlist() {
        this.createdAt = LocalDateTime.now();
    }

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

    public BigDecimal getTradedVolume() { return tradedVolume; }
    public void setTradedVolume(BigDecimal tradedVolume) { this.tradedVolume = tradedVolume; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
