package com.equity.watchlist.dto;

import java.math.BigDecimal;

/**
 * Request DTO for adding or updating a watchlist entry.
 *
 * <p>On add, only {@link #companyCode} is required — price fields are fetched
 * automatically from the global-watchlist-service.
 * On update, price fields are provided explicitly by the caller.
 */
public class WatchlistRequest {

    /** NSE stock symbol to add or update (e.g. {@code "INFY"}). Required on add. */
    private String companyCode;

    /** 52-week low price. Optional on add; used on update. */
    private BigDecimal week52Low;

    /** 52-week high price. Optional on add; used on update. */
    private BigDecimal week52High;

    /** All-time low price. Optional on add; used on update. */
    private BigDecimal allTimeLow;

    /** All-time high price. Optional on add; used on update. */
    private BigDecimal allTimeHigh;

    /** Latest traded price. Optional on add; used on update. */
    private BigDecimal currentValue;

    /** Total traded volume for the latest session. Optional on add; used on update. */
    private BigDecimal tradedVolume;

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
}
