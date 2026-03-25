package com.equity.watchlist.dto;

import java.math.BigDecimal;

/**
 * Response DTO representing a watchlist entry returned to the client.
 *
 * <p>Returned by GET and POST endpoints on {@code /api/v1/watchlist}.
 * Price fields reflect the values stored at the time of last update;
 * they are not fetched live on every read.
 */
public class WatchlistView {

    /** NSE stock symbol (e.g. {@code "INFY"}). */
    private String companyCode;

    /** Full company name from {@code company_master} (e.g. {@code "Infosys Limited"}). May be {@code null} if not found. */
    private String companyName;

    /** 52-week low price. */
    private BigDecimal week52Low;

    /** 52-week high price. */
    private BigDecimal week52High;

    /** All-time low price. */
    private BigDecimal allTimeLow;

    /** All-time high price. */
    private BigDecimal allTimeHigh;

    /** Latest traded price at last update time. */
    private BigDecimal currentValue;

    /** Total traded volume for the latest session. */
    private BigDecimal tradedVolume;

    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

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
