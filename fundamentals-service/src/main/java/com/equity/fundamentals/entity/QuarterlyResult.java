package com.equity.fundamentals.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the quarterly_results table.
 *
 * Stores P&L data for one company for one quarter.
 * The UNIQUE constraint on (symbol, quarter) ensures that:
 *   - Each company has at most one row per quarter (e.g. one row for RELIANCE Q1FY25)
 *   - Re-fetching the same quarter overwrites the row (upsert via find-or-create)
 *   - New quarters add new rows — old quarters are never deleted
 *
 * quarter format: Q1FY25, Q2FY25, Q3FY25, Q4FY25
 * Indian fiscal year: April–March. Q1 = Apr–Jun, Q2 = Jul–Sep, Q3 = Oct–Dec, Q4 = Jan–Mar.
 *
 * rawData stores the full JSON from the yfinance wrapper for future reference.
 * If we need a new field later, we can parse it from rawData without re-fetching.
 *
 * Table created by Hibernate (ddl-auto=update) on first startup.
 * Reference DDL: scripts/create_fundamentals_tables.sql
 */
@Entity
@Table(
    name = "quarterly_results",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_quarterly_symbol_quarter",
        columnNames = {"symbol", "quarter"}
    )
)
public class QuarterlyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE symbol — e.g. "RELIANCE". Never null. */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Indian fiscal quarter — e.g. "Q1FY25". Never null. */
    @Column(nullable = false, length = 10)
    private String quarter;

    /** Last day of the quarter — e.g. 2024-06-30 for Q1FY25. */
    private LocalDate periodEndDate;

    /** Total Revenue / Net Sales in the company's reporting currency (usually INR). */
    @Column(precision = 20, scale = 2)
    private BigDecimal revenue;

    /** Gross Profit = Revenue - Cost of Goods Sold. */
    @Column(precision = 20, scale = 2)
    private BigDecimal grossProfit;

    /** Operating Profit / EBITDA. */
    @Column(precision = 20, scale = 2)
    private BigDecimal operatingProfit;

    /** Net Profit / PAT (Profit After Tax). */
    @Column(precision = 20, scale = 2)
    private BigDecimal netProfit;

    /** Earnings Per Share (diluted or basic). */
    @Column(precision = 10, scale = 4)
    private BigDecimal eps;

    /** Revenue growth vs same quarter previous year, as a percentage. */
    @Column(precision = 8, scale = 4)
    private BigDecimal revenueGrowthYoy;

    /** Net profit growth vs same quarter previous year, as a percentage. */
    @Column(precision = 8, scale = 4)
    private BigDecimal profitGrowthYoy;

    /** Full JSON response from the yfinance wrapper stored as text for reference. */
    @Column(columnDefinition = "text")
    private String rawData;

    /** Source of this data — YFINANCE, EODHD, or SCREENER. */
    @Column(length = 20)
    private String dataSource;

    /** When this row was last fetched/updated. */
    private LocalDateTime fetchedAt;

    // ── Getters & Setters ─────��──────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public LocalDate getPeriodEndDate() { return periodEndDate; }
    public void setPeriodEndDate(LocalDate periodEndDate) { this.periodEndDate = periodEndDate; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public BigDecimal getGrossProfit() { return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit) { this.grossProfit = grossProfit; }

    public BigDecimal getOperatingProfit() { return operatingProfit; }
    public void setOperatingProfit(BigDecimal operatingProfit) { this.operatingProfit = operatingProfit; }

    public BigDecimal getNetProfit() { return netProfit; }
    public void setNetProfit(BigDecimal netProfit) { this.netProfit = netProfit; }

    public BigDecimal getEps() { return eps; }
    public void setEps(BigDecimal eps) { this.eps = eps; }

    public BigDecimal getRevenueGrowthYoy() { return revenueGrowthYoy; }
    public void setRevenueGrowthYoy(BigDecimal revenueGrowthYoy) { this.revenueGrowthYoy = revenueGrowthYoy; }

    public BigDecimal getProfitGrowthYoy() { return profitGrowthYoy; }
    public void setProfitGrowthYoy(BigDecimal profitGrowthYoy) { this.profitGrowthYoy = profitGrowthYoy; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
