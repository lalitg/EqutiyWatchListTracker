package com.watchlist.global.exception;

/**
 * Thrown when a requested NSE company code is not tracked in the global watchlist.
 *
 * <p>Caught by {@link GlobalExceptionHandler} and mapped to a {@code 404 Not Found}
 * response with a descriptive {@link ErrorResponse} body.
 */
public class CompanyNotFoundException extends RuntimeException {

    /**
     * Constructs the exception with a message identifying the missing company.
     *
     * @param companyCode the NSE symbol that was not found
     */
    public CompanyNotFoundException(String companyCode) {
        super("Company '" + companyCode + "' is not tracked in the global watchlist");
    }
}
