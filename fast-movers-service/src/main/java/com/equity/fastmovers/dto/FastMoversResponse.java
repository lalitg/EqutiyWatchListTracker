package com.equity.fastmovers.dto;

import java.time.LocalDateTime;
import java.util.List;

public class FastMoversResponse {

    private String range;
    private List<FastMoverEntry> gainers;
    private List<FastMoverEntry> losers;
    private LocalDateTime generatedAt;

    public FastMoversResponse() {}

    public FastMoversResponse(String range, List<FastMoverEntry> gainers,
                              List<FastMoverEntry> losers, LocalDateTime generatedAt) {
        this.range       = range;
        this.gainers     = gainers;
        this.losers      = losers;
        this.generatedAt = generatedAt;
    }

    public String getRange()                      { return range; }
    public void setRange(String range)            { this.range = range; }
    public List<FastMoverEntry> getGainers()      { return gainers; }
    public void setGainers(List<FastMoverEntry> g){ this.gainers = g; }
    public List<FastMoverEntry> getLosers()       { return losers; }
    public void setLosers(List<FastMoverEntry> l) { this.losers = l; }
    public LocalDateTime getGeneratedAt()         { return generatedAt; }
    public void setGeneratedAt(LocalDateTime t)   { this.generatedAt = t; }
}
