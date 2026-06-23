package com.equity.fundamentals.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the balance_sheets table.
 *
 * Stores the annual balance sheet for one company for one fiscal year.
 * The UNIQUE constraint on (symbol, fiscal_year) ensures:
 *   - Each company has at most one row per fiscal year (e.g. RELIANCE FY2024)
 *   - Re-fetching the same year overwrites the row (upsert via find-or-create)
 *   - New fiscal years add new rows — old years are never deleted
 *
 * fiscal_year format: FY2024, FY2025 (Indian fiscal: April–March)
 * FY2024 = April 2023 – March 2024. period_end_date = 2024-03-31.
 *
 * Table created by Hibernate (ddl-auto=update) on first startup.
 * Reference DDL: scripts/create_fundamentals_tables.sql
 */
@Entity
@Table(
    name = "balance_sheets",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_balance_symbol_fiscal_year",
        columnNames = {"symbol", "fiscal_year"}
    )
)
public class BalanceSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE symbol — e.g. "RELIANCE". Never null. */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Indian fiscal year — e.g. "FY2024". Never null. */
    @Column(name = "fiscal_year", nullable = false, length = 10)
    private String fiscalYear;

    /** Last day of the fiscal year — e.g. 2024-03-31 for FY2024. */
    private LocalDate periodEndDate;

    // ── Assets ───────────────────────────────────────────────────────────────

    @Column(precision = 20, scale = 2)
    private BigDecimal totalAssets;

    @Column(precision = 20, scale = 2)
    private BigDecimal currentAssets;

    /** Cash + short-term equivalents. */
    @Column(precision = 20, scale = 2)
    private BigDecimal cashAndEquivalents;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalInvestments;

    /** Net Fixed Assets / Property Plant Equipment. */
    @Column(precision = 20, scale = 2)
    private BigDecimal fixedAssets;

    // ── Liabilities ──────────────────────────────────────────��───────────────

    @Column(precision = 20, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(precision = 20, scale = 2)
    private BigDecimal currentLiabilities;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalDebt;

    @Column(precision = 20, scale = 2)
    private BigDecimal longTermDebt;

    // ── Equity ────────────────────────────────────────────────────────────────

    @Column(precision = 20, scale = 2)
    private BigDecimal shareholdersEquity;

    @Column(precision = 20, scale = 2)
    private BigDecimal retainedEarnings;

    /** Paid-up share capital. */
    @Column(precision = 20, scale = 2)
    private BigDecimal shareCapital;

    // ── Meta ────────────────────────────────��────────────────────────────────

    /** Full JSON response from yfinance wrapper stored as text for reference. */
    @Column(columnDefinition = "text")
    private String rawData;

    @Column(length = 20)
    private String dataSource;

    private LocalDateTime fetchedAt;

    // ── Getters & Setters ───────��────────────���────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(String fiscalYear) { this.fiscalYear = fiscalYear; }

    public LocalDate getPeriodEndDate() { return periodEndDate; }
    public void setPeriodEndDate(LocalDate periodEndDate) { this.periodEndDate = periodEndDate; }

    public BigDecimal getTotalAssets() { return totalAssets; }
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }

    public BigDecimal getCurrentAssets() { return currentAssets; }
    public void setCurrentAssets(BigDecimal currentAssets) { this.currentAssets = currentAssets; }

    public BigDecimal getCashAndEquivalents() { return cashAndEquivalents; }
    public void setCashAndEquivalents(BigDecimal cashAndEquivalents) { this.cashAndEquivalents = cashAndEquivalents; }

    public BigDecimal getTotalInvestments() { return totalInvestments; }
    public void setTotalInvestments(BigDecimal totalInvestments) { this.totalInvestments = totalInvestments; }

    public BigDecimal getFixedAssets() { return fixedAssets; }
    public void setFixedAssets(BigDecimal fixedAssets) { this.fixedAssets = fixedAssets; }

    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }

    public BigDecimal getCurrentLiabilities() { return currentLiabilities; }
    public void setCurrentLiabilities(BigDecimal currentLiabilities) { this.currentLiabilities = currentLiabilities; }

    public BigDecimal getTotalDebt() { return totalDebt; }
    public void setTotalDebt(BigDecimal totalDebt) { this.totalDebt = totalDebt; }

    public BigDecimal getLongTermDebt() { return longTermDebt; }
    public void setLongTermDebt(BigDecimal longTermDebt) { this.longTermDebt = longTermDebt; }

    public BigDecimal getShareholdersEquity() { return shareholdersEquity; }
    public void setShareholdersEquity(BigDecimal shareholdersEquity) { this.shareholdersEquity = shareholdersEquity; }

    public BigDecimal getRetainedEarnings() { return retainedEarnings; }
    public void setRetainedEarnings(BigDecimal retainedEarnings) { this.retainedEarnings = retainedEarnings; }

    public BigDecimal getShareCapital() { return shareCapital; }
    public void setShareCapital(BigDecimal shareCapital) { this.shareCapital = shareCapital; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
