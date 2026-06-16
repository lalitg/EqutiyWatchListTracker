package com.equity.fastmovers.dto;

import java.math.BigDecimal;

public class FastMoverEntry {

    private int rank;
    private String companyCode;
    private String companyName;
    private BigDecimal currentPrice;
    private BigDecimal basePrice;
    private BigDecimal pctChange;

    public FastMoverEntry() {}

    public FastMoverEntry(int rank, String companyCode, String companyName,
                          BigDecimal currentPrice, BigDecimal basePrice, BigDecimal pctChange) {
        this.rank        = rank;
        this.companyCode = companyCode;
        this.companyName = companyName;
        this.currentPrice = currentPrice;
        this.basePrice   = basePrice;
        this.pctChange   = pctChange;
    }

    public int getRank()                       { return rank; }
    public void setRank(int rank)              { this.rank = rank; }
    public String getCompanyCode()             { return companyCode; }
    public void setCompanyCode(String c)       { this.companyCode = c; }
    public String getCompanyName()             { return companyName; }
    public void setCompanyName(String n)       { this.companyName = n; }
    public BigDecimal getCurrentPrice()        { return currentPrice; }
    public void setCurrentPrice(BigDecimal p)  { this.currentPrice = p; }
    public BigDecimal getBasePrice()           { return basePrice; }
    public void setBasePrice(BigDecimal p)     { this.basePrice = p; }
    public BigDecimal getPctChange()           { return pctChange; }
    public void setPctChange(BigDecimal p)     { this.pctChange = p; }
}
