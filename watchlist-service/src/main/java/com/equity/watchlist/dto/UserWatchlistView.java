package com.equity.watchlist.dto;

import java.time.LocalDateTime;

/**
 * Response DTO representing a named watchlist.
 */
public class UserWatchlistView {

    private Long id;
    private Long userId;
    private String name;
    private LocalDateTime createdAt;
    private int companyCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getCompanyCount() { return companyCount; }
    public void setCompanyCount(int companyCount) { this.companyCount = companyCount; }
}
