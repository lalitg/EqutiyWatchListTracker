package com.watchlist.global.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity mapping to the {@code global_watchlist} table.
 *
 * <p>Stores the persisted price snapshot for each tracked NSE company.
 * The in-memory cache ({@link com.watchlist.global.model.GlobalWatchlistEntry})
 * is populated from this entity on startup and written back at market close.
 */
@Entity
@Table(name = "global_watchlist")
public class GlobalWatchlist {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE stock symbol (e.g. {@code "INFY"}). Unique and non-null. */
    @Column(name = "company_code", unique = true, nullable = false, length = 50)
    private String companyCode;

    /** Latest traded price persisted at market close. */
    @Column(name = "current_value", precision = 15, scale = 2)
    private BigDecimal currentValue;

    /** 52-week low price. */
    @Column(name = "week_52_low", precision = 15, scale = 2)
    private BigDecimal week52Low;

    /** 52-week high price. */
    @Column(name = "week_52_high", precision = 15, scale = 2)
    private BigDecimal week52High;

    /** All-time low price. */
    @Column(name = "all_time_low", precision = 15, scale = 2)
    private BigDecimal allTimeLow;

    /** All-time high price. */
    @Column(name = "all_time_high", precision = 15, scale = 2)
    private BigDecimal allTimeHigh;

    /** Total traded volume for the latest persisted session. */
    @Column(name = "traded_volume", precision = 20, scale = 2)
    private BigDecimal tradedVolume;

    /** Whether this company is part of the NIFTY 50 index. */
    @Column(name = "is_nifty50", nullable = false)
    private boolean nifty50 = false;

    /** Timestamp when this row was first inserted. */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Timestamp of the last price update. */
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /**
     * Default constructor — sets {@code createdAt} and {@code lastUpdated} to now.
     */
    public GlobalWatchlist() {
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }

    /** @return the auto-generated primary key */
    public Long getId() { return id; }

    /** @return the NSE stock symbol */
    public String getCompanyCode() { return companyCode; }
    /** @param companyCode the NSE stock symbol */
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    /** @return the latest persisted traded price */
    public BigDecimal getCurrentValue() { return currentValue; }
    /** @param currentValue the latest traded price */
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    /** @return the 52-week low price */
    public BigDecimal getWeek52Low() { return week52Low; }
    /** @param week52Low the 52-week low price */
    public void setWeek52Low(BigDecimal week52Low) { this.week52Low = week52Low; }

    /** @return the 52-week high price */
    public BigDecimal getWeek52High() { return week52High; }
    /** @param week52High the 52-week high price */
    public void setWeek52High(BigDecimal week52High) { this.week52High = week52High; }

    /** @return the all-time low price */
    public BigDecimal getAllTimeLow() { return allTimeLow; }
    /** @param allTimeLow the all-time low price */
    public void setAllTimeLow(BigDecimal allTimeLow) { this.allTimeLow = allTimeLow; }

    /** @return the all-time high price */
    public BigDecimal getAllTimeHigh() { return allTimeHigh; }
    /** @param allTimeHigh the all-time high price */
    public void setAllTimeHigh(BigDecimal allTimeHigh) { this.allTimeHigh = allTimeHigh; }

    /** @return the total traded volume */
    public BigDecimal getTradedVolume() { return tradedVolume; }
    /** @param tradedVolume the total traded volume */
    public void setTradedVolume(BigDecimal tradedVolume) { this.tradedVolume = tradedVolume; }

    /** @return true if this company is part of the NIFTY 50 index */
    public boolean isNifty50() { return nifty50; }
    /** @param nifty50 true if this company is part of the NIFTY 50 index */
    public void setNifty50(boolean nifty50) { this.nifty50 = nifty50; }

    /** @return the row creation timestamp */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** @return the last price update timestamp */
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    /** @param lastUpdated the last price update timestamp */
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
