package com.watchlist.global.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GlobalIndexEntry {

    public enum Region { US_MARKETS, EUROPEAN_MARKETS, ASIAN_MARKETS, INDIAN_MARKETS }

    private String symbol;
    private String name;
    private String flagEmoji;
    private Region region;
    private BigDecimal ltp;
    private BigDecimal change;
    private BigDecimal changePercent;
    private LocalDateTime lastUpdated;

    public GlobalIndexEntry() {}

    public GlobalIndexEntry(String symbol, String name, String flagEmoji, Region region) {
        this.symbol = symbol;
        this.name = name;
        this.flagEmoji = flagEmoji;
        this.region = region;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFlagEmoji() { return flagEmoji; }
    public void setFlagEmoji(String flagEmoji) { this.flagEmoji = flagEmoji; }

    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }

    public BigDecimal getLtp() { return ltp; }
    public void setLtp(BigDecimal ltp) { this.ltp = ltp; }

    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }

    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
