package com.equity.fastmovers.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_price",
       uniqueConstraints = @UniqueConstraint(columnNames = {"company_code", "price_date"}))
public class DailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "close_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal closePrice;

    public Long getId()                    { return id; }
    public String getCompanyCode()         { return companyCode; }
    public void setCompanyCode(String c)   { this.companyCode = c; }
    public LocalDate getPriceDate()        { return priceDate; }
    public void setPriceDate(LocalDate d)  { this.priceDate = d; }
    public BigDecimal getClosePrice()      { return closePrice; }
    public void setClosePrice(BigDecimal p){ this.closePrice = p; }
}
