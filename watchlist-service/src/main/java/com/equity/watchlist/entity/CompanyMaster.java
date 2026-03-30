package com.equity.watchlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code company_master} table.
 *
 * <p>This table is populated and maintained by the {@code nse-companycode-scheduler}
 * service, which periodically syncs the full list of NSE-listed companies.
 * The watchlist-service reads this table as a reference for symbol lookups
 * and autocomplete support.
 */
@Entity
@Table(name = "company_master")
public class CompanyMaster {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE stock symbol, unique across all listed companies (e.g. {@code "INFY"}). */
    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;

    /** Full registered company name (e.g. {@code "Infosys Limited"}). */
    @Column(name = "company_name")
    private String companyName;

    /**
     * Whether the company is currently active on NSE.
     * Inactive companies are excluded from autocomplete results.
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
