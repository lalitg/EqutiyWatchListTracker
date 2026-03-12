package com.companycode.nse.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a NSE sectoral index and its constituent companies.
 * Each row stores one sector (e.g. "NIFTY IT") and a JSON array of stock symbols
 * belonging to that sector (e.g. ["INFY","TCS","WIPRO"]).
 *
 * Maps to the "sector_companies" table in PostgreSQL.
 */
@Entity
@Table(name = "sector_companies")
public class SectorCompanies {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Name of the NSE sectoral index e.g. "NIFTY IT", "NIFTY PHARMA"
    // Unique constraint ensures one row per sector
    @Column(name = "sector_name", unique = true, nullable = false)
    private String sectorName;

    // JSON array of stock symbols belonging to this sector
    // Stored as PostgreSQL jsonb for efficient querying
    // e.g. ["INFY", "TCS", "WIPRO", "HCLTECH"]
    @Column(columnDefinition = "text", nullable = false)
    private String companies;

    // Timestamp of the last successful sync for this sector
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }

    public String getCompanies() { return companies; }
    public void setCompanies(String companies) { this.companies = companies; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
