package com.equity.watchlist.dto;

import java.math.BigDecimal;

/**
 * Response DTO representing a watchlist entry returned to the client.
 *
 * <p>Price fields are populated live from global-watchlist-service at read time —
 * they are not stored in the {@code watchlist} table.
 */
public class WatchlistView {

    /** NSE stock symbol (e.g. {@code "INFY"}). */
    private String companyCode;

    /** Full company name from {@code company_master}. May be {@code null} if not found. */
    private String companyName;

    /** ID of the named watchlist this entry belongs to. */
    private Long userWatchlistId;

    /** Name of the watchlist this entry belongs to (e.g. "My Watchlist"). */
    private String watchlistName;

    /** 52-week low price (fetched live from global_watchlist). */
    private BigDecimal week52Low;

    /** 52-week high price (fetched live from global_watchlist). */
    private BigDecimal week52High;

    /** All-time low price (fetched live from global_watchlist). */
    private BigDecimal allTimeLow;

    /** All-time high price (fetched live from global_watchlist). */
    private BigDecimal allTimeHigh;

    /** Latest traded price (fetched live from global_watchlist). */
    private BigDecimal currentValue;

    /** Total traded volume for the latest session (fetched live from global_watchlist). */
    private BigDecimal tradedVolume;

    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public Long getUserWatchlistId() { return userWatchlistId; }
    public void setUserWatchlistId(Long userWatchlistId) { this.userWatchlistId = userWatchlistId; }

    public String getWatchlistName() { return watchlistName; }
    public void setWatchlistName(String watchlistName) { this.watchlistName = watchlistName; }

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
}
