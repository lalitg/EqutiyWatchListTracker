package com.equity.fastmovers.entity;

import jakarta.persistence.*;

/** Read-only mapping to the shared company_master table. */
@Entity
@Table(name = "company_master")
public class CompanyMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId()                      { return id; }
    public String getSymbol()                { return symbol; }
    public String getCompanyName()           { return companyName; }
    public boolean isActive()                { return active; }
}
