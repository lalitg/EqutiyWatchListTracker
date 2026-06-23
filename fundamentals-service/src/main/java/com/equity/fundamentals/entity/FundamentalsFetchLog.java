package com.equity.fundamentals.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for the fundamentals_fetch_log table.
 *
 * Audit log for every fetch attempt — one row per company per job run.
 * Stores SUCCESS, FAILED, or SKIPPED with the error message on failure.
 *
 * Purpose:
 *   - Debug which companies failed without digging through application logs
 *   - Identify patterns (e.g., always failing for certain symbols)
 *   - Track data freshness per company
 *
 * This table grows over time but stays small (~1800 rows per full run).
 * No cleanup job is needed — disk cost is negligible.
 *
 * Table created by Hibernate (ddl-auto=update) on first startup.
 * Reference DDL: scripts/create_fundamentals_tables.sql
 */
@Entity
@Table(name = "fundamentals_fetch_log")
public class FundamentalsFetchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE symbol that was fetched — e.g. "RELIANCE". */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Which job produced this log entry — "QUARTERLY_RESULTS" or "BALANCE_SHEET". */
    @Column(nullable = false, length = 30)
    private String fetchType;

    /** Outcome — "SUCCESS", "FAILED", or "SKIPPED". */
    @Column(nullable = false, length = 10)
    private String status;

    /** Error message from the exception, null on SUCCESS. */
    @Column(columnDefinition = "text")
    private String errorMessage;

    /** Which data source was used — "YFINANCE", "EODHD", "SCREENER". */
    @Column(length = 20)
    private String dataSource;

    /** When this log row was written. */
    private LocalDateTime fetchedAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    public FundamentalsFetchLog() {}

    /** Convenience constructor for SUCCESS. */
    public FundamentalsFetchLog(String symbol, String fetchType, String dataSource) {
        this.symbol = symbol;
        this.fetchType = fetchType;
        this.status = "SUCCESS";
        this.dataSource = dataSource;
        this.fetchedAt = LocalDateTime.now();
    }

    /** Convenience constructor for FAILED. */
    public FundamentalsFetchLog(String symbol, String fetchType, String errorMessage, String dataSource) {
        this.symbol = symbol;
        this.fetchType = fetchType;
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.dataSource = dataSource;
        this.fetchedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getFetchType() { return fetchType; }
    public void setFetchType(String fetchType) { this.fetchType = fetchType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
