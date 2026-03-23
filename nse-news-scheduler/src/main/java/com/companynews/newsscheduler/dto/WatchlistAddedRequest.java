package com.companynews.newsscheduler.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/internal/watchlist/added.
 *
 * WHY a dedicated DTO instead of Map<String, String>:
 * Using a raw Map means Spring cannot apply @Valid bean validation to the
 * individual fields — validation annotations only work on POJO fields.
 * A typed DTO lets us declare @NotBlank directly on the field, so
 * Spring automatically rejects blank/null symbols before the method runs,
 * without any manual if-null checks in the controller body.
 */
public class WatchlistAddedRequest {

    @NotBlank(message = "symbol must not be blank")
    private String symbol;

    public WatchlistAddedRequest() {}

    public WatchlistAddedRequest(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
