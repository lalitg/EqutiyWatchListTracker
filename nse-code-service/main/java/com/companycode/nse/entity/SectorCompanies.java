package com.companycode.nse.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity mapping to the {@code company_sectors} table.
 *
 * <p>One row per NSE-listed company from the Nifty 500 index. Stores the raw
 * CSV industry classification alongside a display name and news keyword used
 * by the Domestic Market page sector tabs.
 *
 * <p>Companies whose industry is not in the 10 configured domestic tabs still
 * get a row (for company-page sector badges) but have a null {@code newsKeyword}.
 */
@Entity
@Table(name = "company_sectors", uniqueConstraints = @UniqueConstraint(columnNames = "symbol"))
public class SectorCompanies {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NSE stock symbol (e.g. {@code "INFY"}). One row per symbol. */
    @Column(nullable = false)
    private String symbol;

    /** Full registered company name. */
    @Column(name = "company_name")
    private String companyName;

    /** ISIN code from the Nifty 500 CSV. */
    private String isin;

    /** Raw industry value from Nifty 500 CSV (e.g. {@code "Information Technology"}). */
    @Column(nullable = false)
    private String industry;

    /**
     * Mapped display name shown on the UI (e.g. {@code "IT"}, {@code "Auto"}).
     * Falls back to the raw {@code industry} value for unmapped industries.
     */
    @Column(name = "display_name")
    private String displayName;

    /**
     * Keyword used to fetch news for this sector from the news scheduler.
     * Null for industries not configured as Domestic Market page tabs.
     */
    @Column(name = "news_keyword")
    private String newsKeyword;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getNewsKeyword() { return newsKeyword; }
    public void setNewsKeyword(String newsKeyword) { this.newsKeyword = newsKeyword; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
