package com.companycode.nse.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_indices", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "index_key"}))
public class IndexCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "company_name")
    private String companyName;

    private String isin;

    private String industry;

    @Column(name = "index_key", nullable = false)
    private String indexKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getIndexKey() { return indexKey; }
    public void setIndexKey(String indexKey) { this.indexKey = indexKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
