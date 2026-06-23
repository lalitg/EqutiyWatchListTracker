package com.equity.fundamentals.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Read-only JPA entity mapping to the existing global_watchlist table.
 *
 * This table is OWNED by global-watchlist-service — it creates and maintains it.
 * fundamentals-service only READS the company_code column to know which companies
 * to fetch quarterly results and balance sheets for.
 *
 * Why global_watchlist instead of company_master?
 *   company_master has ~2,200 NSE-listed companies.
 *   global_watchlist has only the companies users are actually tracking.
 *   Fetching fundamentals for all 2,200 companies wastes API quota and takes 45+ minutes.
 *   Scoping to global_watchlist means we only fetch for companies that matter.
 *
 * DO NOT add SQL migration for this table in fundamentals-service.
 * DO NOT write to this table from fundamentals-service.
 *
 * Only the fields needed by fundamentals-service are mapped here.
 * The full schema has more columns (current_value, week_52_high, etc.)
 * which are managed by global-watchlist-service.
 */
@Entity
@Table(name = "global_watchlist")
public class GlobalWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NSE stock symbol — e.g. "RELIANCE", "INFY".
     * This is the key used to call the yfinance wrapper (appended with .NS).
     */
    @Column(name = "company_code", unique = true, nullable = false, length = 50)
    private String companyCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ── Getters (read-only — no setters) ──────────────────────────────────────

    public Long getId() { return id; }

    public String getCompanyCode() { return companyCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
