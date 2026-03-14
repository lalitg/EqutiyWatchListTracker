package com.companynews.newsscheduler.model;

import jakarta.persistence.*;

@Entity
@Table(name = "sectors")
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The sector name column — e.g. "Information Technology", "Banking"
    // Change "sector_name" to match your actual column name
    @Column(name = "sector_name")
    private String sectorName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
}
