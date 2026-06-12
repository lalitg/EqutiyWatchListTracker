package com.equity.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/auth/signup.
 *
 * auth-service receives this, validates it, then forwards it to
 * user-service POST /api/v1/users/register to actually create the user.
 * auth-service does NOT directly write to the users table.
 *
 * Validation rules (from LLD Section 13):
 * - username: required, max 50 chars
 * - name: required, max 100 chars
 * - At least one of email OR phoneNumber must be provided — enforced in AuthService
 * - password: min 8 chars, at least 1 uppercase, 1 digit, 1 special character
 * - investmentYears and investmentAmount: optional strings (enum names)
 */
public class SignupRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username must be at most 50 characters")
    private String username;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    @Size(max = 15, message = "Phone number must be at most 15 characters")
    private String phoneNumber;

    /**
     * Password policy from LLD Section 13:
     * min 8 chars, at least 1 uppercase, 1 number, 1 special character.
     * Pattern enforced here so the check happens before calling user-service.
     */
    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must be at least 8 characters with 1 uppercase, 1 number, and 1 special character"
    )
    private String password;

    /** Optional: ZERO_TO_FIVE | FIVE_TO_TEN | TEN_TO_FIFTEEN | FIFTEEN_PLUS */
    private String investmentYears;

    /** Optional: ZERO_TO_5L | FIVE_TO_10L | TEN_TO_20L | TWENTY_TO_50L | FIFTY_PLUS */
    private String investmentAmount;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getInvestmentYears() { return investmentYears; }
    public void setInvestmentYears(String investmentYears) { this.investmentYears = investmentYears; }

    public String getInvestmentAmount() { return investmentAmount; }
    public void setInvestmentAmount(String investmentAmount) { this.investmentAmount = investmentAmount; }
}
