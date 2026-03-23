package com.companynews.newsscheduler.model;

import jakarta.persistence.*;

/**
 * JPA entity representing one row in the {@code global_watchlist} table.
 *
 * <p>The global watchlist stores company symbols that all users have added to their
 * watchlists. This service reads the list of symbols from this table to determine
 * which companies to fetch news for during the Google RSS scheduled run.
 *
 * <p>Note: the database column is named {@code company_code} (not {@code symbol}).
 * The Java field is named {@code companyCode} to match. The {@code @Column} annotation
 * bridges the naming gap so JPA maps correctly.
 */
@Entity
@Table(name = "global_watchlist")
public class GlobalWatchlist {

    /**
     * Auto-generated surrogate primary key for this watchlist entry.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The company symbol stored in the {@code company_code} column (e.g., {@code INFY}, {@code TCS}).
     * Field renamed from {@code symbol} to {@code companyCode} to match the actual column name.
     */
    @Column(name = "company_code")
    private String companyCode;

    /**
     * Returns the surrogate primary key of this watchlist entry.
     *
     * @return the record ID
     */
    public Long getId() { return id; }

    /**
     * Sets the surrogate primary key of this watchlist entry.
     *
     * @param id the record ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * Returns the company symbol (stored in the {@code company_code} column).
     *
     * @return the company symbol, e.g., {@code INFY}
     */
    public String getCompanyCode() { return companyCode; }

    /**
     * Sets the company symbol for this watchlist entry.
     *
     * @param companyCode the company symbol, e.g., {@code INFY}
     */
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
}
