package com.watchlist.global.model;

import java.math.BigDecimal;

public class IndexCompanyEntry {

    private String symbol;
    private BigDecimal ltp;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal week52High;
    private BigDecimal week52Low;
    private BigDecimal previousClose;
    private BigDecimal tradedVolume;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getLtp() { return ltp; }
    public void setLtp(BigDecimal ltp) { this.ltp = ltp; }

    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }

    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }

    public BigDecimal getWeek52High() { return week52High; }
    public void setWeek52High(BigDecimal week52High) { this.week52High = week52High; }

    public BigDecimal getWeek52Low() { return week52Low; }
    public void setWeek52Low(BigDecimal week52Low) { this.week52Low = week52Low; }

    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }

    public BigDecimal getTradedVolume() { return tradedVolume; }
    public void setTradedVolume(BigDecimal tradedVolume) { this.tradedVolume = tradedVolume; }
}
