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
 * JPA entity representing a named watchlist owned by a user.
 *
 * <p>Maps to the {@code user_watchlists} table. One user can have many
 * named watchlists (e.g. "Tech Stocks", "Dividend Picks"). Each named
 * watchlist contains one or more companies tracked in the {@code watchlist} table.
 */
@Entity
@Table(name = "user_watchlists", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "name"})
})
public class UserWatchlist {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the user who owns this watchlist. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Human-readable name for this watchlist (e.g. "My Watchlist"). */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Timestamp when this watchlist was created. */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Default constructor. Sets {@link #createdAt} to the current timestamp.
     */
    public UserWatchlist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
