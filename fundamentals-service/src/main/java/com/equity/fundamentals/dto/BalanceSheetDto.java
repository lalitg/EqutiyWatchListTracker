package com.equity.fundamentals.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO that maps the JSON response from the yfinance Python wrapper
 * for a single year's balance sheet data.
 *
 * The Python wrapper returns a list of these under the "years" key:
 * {
 *   "symbol": "RELIANCE.NS",
 *   "years": [
 *     {
 *       "periodDate": "2024-03-31",
 *       "totalAssets": 1234567000000.0,
 *       "currentAssets": 234567000000.0,
 *       "cashAndEquivalents": 12345000000.0,
 *       "totalInvestments": 89000000000.0,
 *       "fixedAssets": 560000000000.0,
 *       "totalLiabilities": 890000000000.0,
 *       "currentLiabilities": 123000000000.0,
 *       "totalDebt": 345000000000.0,
 *       "longTermDebt": 222000000000.0,
 *       "shareholdersEquity": 344567000000.0,
 *       "retainedEarnings": 234000000000.0,
 *       "shareCapital": 6765000000.0
 *     }, ...
 *   ]
 * }
 *
 * All fields are nullable — yfinance may not have every field for all companies.
 */
public class BalanceSheetDto {

    /** Last day of the fiscal year — e.g. "2024-03-31" for FY2024. */
    private LocalDate periodDate;

    private BigDecimal totalAssets;
    private BigDecimal currentAssets;
    private BigDecimal cashAndEquivalents;
    private BigDecimal totalInvestments;
    private BigDecimal fixedAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal currentLiabilities;
    private BigDecimal totalDebt;
    private BigDecimal longTermDebt;
    private BigDecimal shareholdersEquity;
    private BigDecimal retainedEarnings;
    private BigDecimal shareCapital;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public LocalDate getPeriodDate() { return periodDate; }
    public void setPeriodDate(LocalDate periodDate) { this.periodDate = periodDate; }

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
}
