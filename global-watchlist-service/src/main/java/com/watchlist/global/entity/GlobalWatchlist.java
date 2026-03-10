package com.watchlist.global.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "global_watchlist")
public class GlobalWatchlist {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    public GlobalWatchlist() {}

    public GlobalWatchlist(Long companyId, String symbol) {
        this.companyId = companyId;
        this.symbol    = symbol;
        this.addedAt   = LocalDateTime.now();
    }

    public Long getCompanyId()        { return companyId; }
    public String getSymbol()         { return symbol; }
    public LocalDateTime getAddedAt() { return addedAt; }

    public void setCompanyId(Long v)        { companyId = v; }
    public void setSymbol(String v)         { symbol = v; }
    public void setAddedAt(LocalDateTime v) { addedAt = v; }
}
