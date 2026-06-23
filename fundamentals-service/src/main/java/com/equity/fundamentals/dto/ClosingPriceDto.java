package com.equity.fundamentals.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO that maps the JSON response from the yfinance Python wrapper
 * for the GET /closing-price endpoint.
 *
 * Response shape from Python:
 * {
 *   "symbol":       "RELIANCE.NS",
 *   "date":         "2026-05-04",
 *   "closingPrice": 2487.50
 * }
 *
 * closingPrice and date are nullable — returned as null when the symbol
 * is not found on Yahoo Finance or the price is unavailable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClosingPriceDto {

    private String symbol;
    private LocalDate date;
    private BigDecimal closingPrice;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public BigDecimal getClosingPrice() { return closingPrice; }
    public void setClosingPrice(BigDecimal closingPrice) { this.closingPrice = closingPrice; }
}
