package com.watchlist.global.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class IndexSummary {

    private String nseParam;
    private String displayName;
    private BigDecimal ltp;
    private BigDecimal change;
    private BigDecimal changePercent;
    private boolean hasConstituents;
    private LocalDateTime lastUpdated;

    public IndexSummary() {}

    public IndexSummary(String nseParam, String displayName, boolean hasConstituents) {
        this.nseParam = nseParam;
        this.displayName = displayName;
        this.hasConstituents = hasConstituents;
    }

    public String getNseParam() { return nseParam; }
    public void setNseParam(String nseParam) { this.nseParam = nseParam; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public BigDecimal getLtp() { return ltp; }
    public void setLtp(BigDecimal ltp) { this.ltp = ltp; }

    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }

    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }

    public boolean isHasConstituents() { return hasConstituents; }
    public void setHasConstituents(boolean hasConstituents) { this.hasConstituents = hasConstituents; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
