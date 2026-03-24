package com.companycode.nse.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity mapping to the {@code sector_companies} table.
 *
 * <p>Each row represents one NSE sectoral index (e.g. {@code "NIFTY IT"}) and stores
 * its constituent stock symbols as a JSON array string
 * (e.g. {@code ["INFY","TCS","WIPRO","HCLTECH"]}). Populated and refreshed by
 * {@link com.companycode.nse.service.NseSectorSyncService}.
 */
@Entity
@Table(name = "sector_companies")
public class SectorCompanies {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the NSE sectoral index (e.g. {@code "NIFTY IT"}, {@code "NIFTY PHARMA"}).
     * Unique — one row per sector.
     */
    @Column(name = "sector_name", unique = true, nullable = false)
    private String sectorName;

    /**
     * JSON array of stock symbols belonging to this sector,
     * stored as plain text (e.g. {@code ["INFY","TCS","WIPRO"]}).
     */
    @Column(columnDefinition = "text", nullable = false)
    private String companies;

    /** Timestamp of the last successful sync for this sector. */
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /** @return the auto-generated primary key */
    public Long getId() { return id; }
    /** @param id the auto-generated primary key */
    public void setId(Long id) { this.id = id; }

    /** @return the NSE sectoral index name */
    public String getSectorName() { return sectorName; }
    /** @param sectorName the NSE sectoral index name */
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }

    /** @return the JSON array of constituent stock symbols */
    public String getCompanies() { return companies; }
    /** @param companies the JSON array of constituent stock symbols */
    public void setCompanies(String companies) { this.companies = companies; }

    /** @return the timestamp of the last sync for this sector */
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    /** @param lastUpdated the timestamp of the last sync */
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
