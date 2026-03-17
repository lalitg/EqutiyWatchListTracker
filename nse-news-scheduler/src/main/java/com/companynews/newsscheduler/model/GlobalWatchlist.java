package com.companynews.newsscheduler.model;

import jakarta.persistence.*;

@Entity
@Table(name = "global_watchlist")
public class GlobalWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Changed from "symbol" to "company_code" to match actual column name
    @Column(name = "company_code")
    private String companyCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    // Getter and setter renamed from getSymbol/setSymbol
    // to getCompanyCode/setCompanyCode
    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
}
