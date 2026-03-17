package com.companynews.newsscheduler.model;

import jakarta.persistence.*;

@Entity
// Changed table name from "sectors" to "sector_companies"
@Table(name = "sector_companies")
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sector_name")
    private String sectorName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
}
