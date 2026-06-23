package com.companycode.nse.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity mapping to the {@code company_master} table.
 *
 * <p>Stores the master list of all NSE-listed companies sourced from the
 * {@code EQUITY_L.csv} file published by NSE. Populated and refreshed by
 * {@link com.companycode.nse.service.NseSyncService}.
 */
@Entity
@Table(name = "company_master", uniqueConstraints = @UniqueConstraint(columnNames = "symbol"))
public class CompanyMaster {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE stock symbol (e.g. {@code "INFY"}). Unique and non-null. */
    @Column(nullable = false)
    private String symbol;

    /** Full registered company name (e.g. {@code "Infosys Limited"}). */
    private String companyName;

    /** ISIN (International Securities Identification Number) for the stock. */
    private String isin;

    /** Whether this company is currently active on NSE. Defaults to {@code true}. */
    private boolean active = true;

    /** Timestamp of the last successful sync for this row. */
    private LocalDateTime lastUpdated;

    /** @return the auto-generated primary key */
    public Long getId() { return id; }
    /** @param id the auto-generated primary key */
    public void setId(Long id) { this.id = id; }

    /** @return the NSE stock symbol */
    public String getSymbol() { return symbol; }
    /** @param symbol the NSE stock symbol */
    public void setSymbol(String symbol) { this.symbol = symbol; }

    /** @return the full registered company name */
    public String getCompanyName() { return companyName; }
    /** @param companyName the full registered company name */
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    /** @return the ISIN for this stock */
    public String getIsin() { return isin; }
    /** @param isin the ISIN for this stock */
    public void setIsin(String isin) { this.isin = isin; }

    /** @return {@code true} if this company is currently active on NSE */
    public boolean isActive() { return active; }
    /** @param active {@code true} if this company is active */
    public void setActive(boolean active) { this.active = active; }

    /** @return the timestamp of the last sync for this row */
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    /** @param lastUpdated the timestamp of the last sync */
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
