package com.watchlist.global.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "watchlist")
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code")
    private String companyCode;

    public Long getId()            { return id; }
    public String getCompanyCode() { return companyCode; }
}
