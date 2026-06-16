package com.equity.fastmovers.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fast_movers_cache",
       uniqueConstraints = @UniqueConstraint(columnNames = {"price_date", "type", "rank"}))
public class FastMoversCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    /** 'GAINER' or 'LOSER' */
    @Column(name = "type", nullable = false, length = 10)
    private String type;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "current_price", precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "pct_change", precision = 10, scale = 4)
    private BigDecimal pctChange;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    public Long getId()                          { return id; }
    public LocalDate getPriceDate()              { return priceDate; }
    public void setPriceDate(LocalDate d)        { this.priceDate = d; }
    public String getType()                      { return type; }
    public void setType(String t)                { this.type = t; }
    public Integer getRank()                     { return rank; }
    public void setRank(Integer r)               { this.rank = r; }
    public String getCompanyCode()               { return companyCode; }
    public void setCompanyCode(String c)         { this.companyCode = c; }
    public String getCompanyName()               { return companyName; }
    public void setCompanyName(String n)         { this.companyName = n; }
    public BigDecimal getCurrentPrice()          { return currentPrice; }
    public void setCurrentPrice(BigDecimal p)    { this.currentPrice = p; }
    public BigDecimal getPctChange()             { return pctChange; }
    public void setPctChange(BigDecimal p)       { this.pctChange = p; }
    public LocalDateTime getFetchedAt()          { return fetchedAt; }
    public void setFetchedAt(LocalDateTime t)    { this.fetchedAt = t; }
}
