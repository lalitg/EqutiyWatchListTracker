package com.equity.watchlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single company entry in a named watchlist.
 *
 * <p>Maps to the {@code watchlist} table. Each row belongs to a
 * {@link UserWatchlist} (via {@code user_watchlist_id}) and tracks one NSE symbol.
 * Price data is NOT stored here — it is fetched live from the
 * {@code global_watchlist} table via global-watchlist-service at read time.
 */
@Entity
@Table(name = "watchlist", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_watchlist_id", "company_code"})
})
public class Watchlist {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK to {@code user_watchlists.id} — identifies which named watchlist this entry belongs to.
     * Nullable to support existing rows before the DB migration is run.
     */
    @Column(name = "user_watchlist_id")
    private Long userWatchlistId;

    /** NSE stock symbol (e.g. {@code "INFY"}, {@code "RELIANCE"}). */
    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    /** Timestamp when this entry was first added to the watchlist. */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Default constructor. Sets {@link #createdAt} to the current timestamp.
     */
    public Watchlist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserWatchlistId() { return userWatchlistId; }
    public void setUserWatchlistId(Long userWatchlistId) { this.userWatchlistId = userWatchlistId; }

    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
