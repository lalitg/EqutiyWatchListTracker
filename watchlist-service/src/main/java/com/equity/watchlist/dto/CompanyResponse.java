package com.equity.watchlist.dto;

/**
 * DTO returned by GET /api/v1/company/list for autocomplete.
 */
public class CompanyResponse {

    private String symbol;
    private String companyName;

    public CompanyResponse() {}

    public CompanyResponse(String symbol, String companyName) {
        this.symbol = symbol;
        this.companyName = companyName;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
}
