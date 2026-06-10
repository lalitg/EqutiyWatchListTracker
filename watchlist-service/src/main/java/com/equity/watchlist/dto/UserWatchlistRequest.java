package com.equity.watchlist.dto;

/**
 * Request DTO for creating a named watchlist.
 */
public class UserWatchlistRequest {

    /** Human-readable name for the new watchlist (e.g. "Tech Stocks"). Required. */
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
