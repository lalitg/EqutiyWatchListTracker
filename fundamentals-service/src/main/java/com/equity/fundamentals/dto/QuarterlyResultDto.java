package com.equity.fundamentals.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO that maps the JSON response from the yfinance Python wrapper
 * for a single quarter's P&L data.
 *
 * The Python wrapper returns a list of these under the "quarters" key:
 * {
 *   "symbol": "RELIANCE.NS",
 *   "quarters": [
 *     {
 *       "periodDate": "2024-09-30",
 *       "totalRevenue": 239628000000.0,
 *       "grossProfit": 82000000000.0,
 *       "ebitda": 51000000000.0,
 *       "netIncome": 16563000000.0,
 *       "eps": 24.47
 *     }, ...
 *   ]
 * }
 *
 * Fields are nullable — yfinance may not have all fields for all companies.
 * The service handles null gracefully (stores null in DB, does not fail).
 */
public class QuarterlyResultDto {

    /** Last day of the quarter — e.g. "2024-09-30". */
    private LocalDate periodDate;

    private BigDecimal totalRevenue;

    private BigDecimal grossProfit;

    /** EBITDA or Operating Income from yfinance. */
    private BigDecimal ebitda;

    private BigDecimal netIncome;

    /** Diluted or Basic EPS from yfinance. */
    private BigDecimal eps;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public LocalDate getPeriodDate() { return periodDate; }
    public void setPeriodDate(LocalDate periodDate) { this.periodDate = periodDate; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public BigDecimal getGrossProfit() { return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit) { this.grossProfit = grossProfit; }

    public BigDecimal getEbitda() { return ebitda; }
    public void setEbitda(BigDecimal ebitda) { this.ebitda = ebitda; }

    public BigDecimal getNetIncome() { return netIncome; }
    public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }

    public BigDecimal getEps() { return eps; }
    public void setEps(BigDecimal eps) { this.eps = eps; }
}
