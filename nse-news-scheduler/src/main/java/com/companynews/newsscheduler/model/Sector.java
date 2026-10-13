package com.companynews.newsscheduler.model;

import jakarta.persistence.*;

/**
 * JPA entity representing one row in the {@code sector_companies} table.
 *
 * <p>Each row holds the name of a market sector (e.g., {@code Banking}, {@code Pharma}).
 * This service reads all sector names from this table as part of the keyword list used
 * during the Google RSS scheduled fetch, so sector-level news articles are also collected.
 *
 * <p>Note: the physical table name is {@code sector_companies} (not {@code sectors}).
 * The {@link Table} annotation maps the entity to the correct table name.
 */
@Entity
@Table(name = "sector_companies")
public class Sector {

    /**
     * Auto-generated surrogate primary key for this sector entry.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The display name of the sector (e.g., {@code Banking}, {@code Information Technology}).
     * Used as a search keyword in the Google RSS scheduler.
     */
    @Column(name = "news_keyword")
    private String sectorName;

    /**
     * Returns the surrogate primary key of this sector entry.
     *
     * @return the record ID
     */
    public Long getId() { return id; }

    /**
     * Sets the surrogate primary key of this sector entry.
     *
     * @param id the record ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * Returns the sector name.
     *
     * @return the sector name (e.g., {@code Banking})
     */
    public String getSectorName() { return sectorName; }

    /**
     * Sets the sector name.
     *
     * @param sectorName the sector name (e.g., {@code Banking})
     */
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
}
