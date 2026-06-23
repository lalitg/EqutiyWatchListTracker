package com.equity.watchlist.dto;

/**
 * Request DTO for adding or updating a watchlist entry.
 *
 * <p>On add, {@link #companyCode} is required. {@link #userWatchlistId} identifies
 * which named watchlist to add the company to — if omitted, the service defaults
 * to the first watchlist for the current user.
 * Price data is never sent in the request; it is always fetched live from
 * global-watchlist-service.
 */
public class WatchlistRequest {

    /** NSE stock symbol to add or update (e.g. {@code "INFY"}). Required on add. */
    private String companyCode;

    /** ID of the named watchlist to add this company to. Optional — defaults to first watchlist. */
    private Long userWatchlistId;

    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public Long getUserWatchlistId() { return userWatchlistId; }
    public void setUserWatchlistId(Long userWatchlistId) { this.userWatchlistId = userWatchlistId; }
}
