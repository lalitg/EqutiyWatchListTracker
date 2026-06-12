package com.equity.fundamentals.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Read-only JPA entity mapping to the existing company_master table.
 *
 * This table is OWNED by nse-code-service — it creates and maintains it.
 * fundamentals-service only READS from it to get the list of all active NSE symbols.
 *
 * DO NOT add a SQL migration for this table in fundamentals-service.
 * DO NOT write to this table from fundamentals-service.
 *
 * Table columns:
 *   id           — auto-increment PK
 *   symbol       — NSE stock symbol e.g. "RELIANCE", unique, not null
 *   company_name — full registered company name
 *   isin         — International Securities Identification Number
 *   active       — true if currently listed on NSE
 *   last_updated — timestamp of last sync by nse-code-service
 */
@Entity
@Table(name = "company_master")
public class CompanyMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    private String companyName;

    private String isin;

    private boolean active;

    private LocalDateTime lastUpdated;

    public Long getId() { return id; }

    public String getSymbol() { return symbol; }

    public String getCompanyName() { return companyName; }

    public String getIsin() { return isin; }

    public boolean isActive() { return active; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
}
