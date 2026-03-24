package com.watchlist.global.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the {@code POST /api/global-watchlist/add} endpoint.
 *
 * <p>Validated by the Bean Validation framework before the controller method
 * is invoked. Constraint violations are handled by
 * {@link com.watchlist.global.exception.GlobalExceptionHandler} and returned
 * as a structured {@link com.watchlist.global.exception.ErrorResponse}.
 */
public class AddCompanyRequest {

    /**
     * NSE stock symbol to add to the global watchlist (e.g. {@code "INFY"}).
     * Must be non-null, non-blank, and at most 50 characters.
     */
    @NotBlank(message = "companyCode must not be blank")
    @Size(max = 50, message = "companyCode must not exceed 50 characters")
    private String companyCode;

    /** @return the NSE stock symbol */
    public String getCompanyCode() { return companyCode; }

    /** @param companyCode the NSE stock symbol */
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
}
