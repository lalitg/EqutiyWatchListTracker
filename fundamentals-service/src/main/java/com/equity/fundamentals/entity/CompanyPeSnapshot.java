package com.equity.fundamentals.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the company_pe_snapshot table.
 *
 * Stores one P/E ratio snapshot per company per trading day.
 * Calculated nightly after NSE market close (4:30 PM IST, Mon–Fri).
 *
 * Formula:
 *   ttm_eps      = SUM of eps from the last 4 rows in quarterly_results for this symbol
 *   trailing_pe  = closing_price / ttm_eps
 *
 * trailing_pe is null when:
 *   - ttm_eps is zero (division by zero — would produce infinity)
 *   - ttm_eps is null (no quarterly EPS data available yet)
 *   - closing_price could not be fetched from yfinance
 *
 * A negative trailing_pe is stored as-is when ttm_eps < 0 (loss-making company).
 * The frontend should display "N/A" for null or negative P/E values.
 *
 * quarters_used tracks how many quarters contributed to ttm_eps.
 * A value less than 4 means the company doesn't yet have a full year of data.
 *
 * UNIQUE (symbol, pe_date) — one snapshot per company per day.
 * Old snapshots are never deleted — they form a P/E history over time.
 *
 * Table created by Hibernate (ddl-auto=update) on first startup.
 */
@Entity
@Table(
    name = "company_pe_snapshot",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_pe_symbol_date",
        columnNames = {"symbol", "pe_date"}
    )
)
public class CompanyPeSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE symbol — e.g. "RELIANCE". Never null. */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** The trading date whose closing price was used — e.g. 2026-05-04. Never null. */
    @Column(name = "pe_date", nullable = false)
    private LocalDate peDate;

    /** NSE closing price on pe_date in INR. Null if price fetch failed. */
    @Column(name = "closing_price", precision = 12, scale = 2)
    private BigDecimal closingPrice;

    /** Sum of EPS from the last N quarters (see quarters_used). Null if no EPS data. */
    @Column(name = "ttm_eps", precision = 12, scale = 4)
    private BigDecimal ttmEps;

    /** closing_price / ttm_eps. Null if ttm_eps is zero/null or price unavailable. */
    @Column(name = "trailing_pe", precision = 10, scale = 4)
    private BigDecimal trailingPe;

    /** Number of quarters whose EPS was summed to produce ttm_eps (max 4). */
    @Column(name = "quarters_used")
    private Integer quartersUsed;

    /** When this snapshot row was calculated and saved. */
    private LocalDateTime fetchedAt;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDate getPeDate() { return peDate; }
    public void setPeDate(LocalDate peDate) { this.peDate = peDate; }

    public BigDecimal getClosingPrice() { return closingPrice; }
    public void setClosingPrice(BigDecimal closingPrice) { this.closingPrice = closingPrice; }

    public BigDecimal getTtmEps() { return ttmEps; }
    public void setTtmEps(BigDecimal ttmEps) { this.ttmEps = ttmEps; }

    public BigDecimal getTrailingPe() { return trailingPe; }
    public void setTrailingPe(BigDecimal trailingPe) { this.trailingPe = trailingPe; }

    public Integer getQuartersUsed() { return quartersUsed; }
    public void setQuartersUsed(Integer quartersUsed) { this.quartersUsed = quartersUsed; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
