package com.companynews.newsscheduler.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body DTO for {@code POST /api/internal/watchlist/added}.
 *
 * <p>WHY a dedicated DTO instead of {@code Map<String, String>}:
 * Using a raw {@code Map} means Spring cannot apply {@code @Valid} bean validation to
 * individual fields — validation annotations only work on POJO fields.
 * A typed DTO lets us declare {@code @NotBlank} directly on the field, so Spring
 * automatically rejects blank or null symbols before the controller method runs,
 * without any manual {@code if (symbol == null)} checks in the controller body.
 * {@link com.companynews.newsscheduler.controller.GlobalExceptionHandler} converts
 * violations into a structured 400 Bad Request response.
 */
public class WatchlistAddedRequest {

    /**
     * The company symbol that was added to the global watchlist (e.g., {@code WIPRO}, {@code INFY}).
     * Must not be blank — validated by Spring before the controller method is invoked.
     */
    @NotBlank(message = "symbol must not be blank")
    private String symbol;

    /** Default no-arg constructor required by Jackson for JSON deserialization. */
    public WatchlistAddedRequest() {}

    /**
     * Constructs a {@code WatchlistAddedRequest} with the given symbol.
     *
     * @param symbol the company symbol (e.g., {@code WIPRO})
     */
    public WatchlistAddedRequest(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Returns the company symbol from the request body.
     *
     * @return the company symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Sets the company symbol on this request body.
     *
     * @param symbol the company symbol
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
