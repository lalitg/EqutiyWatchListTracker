
package com.watchlist.marketdata.entity;

import jakarta.persistence.*;

@Entity
@Table(name="company_master")
public class Company {

    @Id
    private Long id;

    private String company_name;

    private String symbol;

    private Boolean active;

    public Long getId(){return id;}
    public String getSymbol(){return symbol;}
    public Boolean getActive(){return active;}
}
