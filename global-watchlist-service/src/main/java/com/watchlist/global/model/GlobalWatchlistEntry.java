package com.watchlist.global.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * In-memory value object stored in the {@link java.util.concurrent.ConcurrentHashMap}
 * maintained by {@link com.watchlist.global.service.GlobalWatchlistService}.
 *
 * <p>Not a JPA entity — never written to the database directly.
 * It is populated from {@link com.watchlist.global.entity.GlobalWatchlist} on startup
 * and refreshed with live prices every 5 minutes during market hours.
 */
public class GlobalWatchlistEntry {

    /** NSE stock symbol (e.g. {@code "INFY"}). */
    private String companyCode;

    /** Full registered company name. May be null if not yet fetched. */
    private String companyName;

    /** Latest live traded price. */
    private BigDecimal currentValue;

    /** 52-week low price. */
    private BigDecimal week52Low;

    /** 52-week high price. */
    private BigDecimal week52High;

    /** All-time low price. */
    private BigDecimal allTimeLow;

    /** All-time high price. */
    private BigDecimal allTimeHigh;

    /** Total traded volume for the latest session. */
    private BigDecimal tradedVolume;

    /** Previous closing price. */
    private BigDecimal previousClose;

    /** Absolute change from previous close. */
    private BigDecimal changeValue;

    /** Percentage change from previous close. */
    private BigDecimal percentChange;

    /** Whether this company is part of the NIFTY 50 index. */
    private boolean nifty50;

    /** Timestamp of the last in-memory price update. */
    private LocalDateTime lastUpdated;

    /** @return the NSE stock symbol */
    public String getCompanyCode() { return companyCode; }
    /** @param companyCode the NSE stock symbol */
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    /** @return the latest live traded price */
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

    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }

    public BigDecimal getChangeValue() { return changeValue; }
    public void setChangeValue(BigDecimal changeValue) { this.changeValue = changeValue; }

    public BigDecimal getPercentChange() { return percentChange; }
    public void setPercentChange(BigDecimal percentChange) { this.percentChange = percentChange; }

    /** @return true if this company is part of the NIFTY 50 index */
    public boolean isNifty50() { return nifty50; }
    /** @param nifty50 true if this company is part of the NIFTY 50 index */
    public void setNifty50(boolean nifty50) { this.nifty50 = nifty50; }

    /** @return the timestamp of the last in-memory price update */
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    /** @param lastUpdated the timestamp of the last in-memory price update */
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
