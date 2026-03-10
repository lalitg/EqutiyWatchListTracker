package com.watchlist.global.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "company_master")
public class CompanyMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "active")
    private boolean active;

    public Long getId()       { return id; }
    public String getSymbol() { return symbol; }
    public boolean isActive() { return active; }
}
