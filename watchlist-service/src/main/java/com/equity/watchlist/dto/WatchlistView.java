package com.equity.watchlist.dto;

import java.math.BigDecimal;

/**
 * Response DTO representing a watchlist entry returned to the client.
 */
public class WatchlistView {

    private String companyCode;
    private String companyName;
    private BigDecimal week52Low;
    private BigDecimal week52High;
    private BigDecimal allTimeLow;
    private BigDecimal allTimeHigh;
    private BigDecimal currentValue;
    private String trendSentiment;
    private BigDecimal peRatio;
    private BigDecimal eps;

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

    public String getTrendSentiment() { return trendSentiment; }
    public void setTrendSentiment(String trendSentiment) { this.trendSentiment = trendSentiment; }

    public BigDecimal getPeRatio() { return peRatio; }
    public void setPeRatio(BigDecimal peRatio) { this.peRatio = peRatio; }

    public BigDecimal getEps() { return eps; }
    public void setEps(BigDecimal eps) { this.eps = eps; }
}
