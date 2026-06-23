package com.equity.user.dto;

import com.equity.user.enums.InvestmentAmount;
import com.equity.user.enums.InvestmentYears;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/users/register.
 *
 * Mandatory fields: username, name, password.
 * Optional at registration: email, phoneNumber, investmentYears, investmentAmount.
 * The user can fill those in later via PUT /api/v1/users/me or
 * PUT /api/v1/users/me/investor-profile.
 *
 * @NotBlank — rejects null and blank strings (validated by Spring's @Valid).
 * @Size     — enforces max lengths matching the DB column sizes.
 */
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username must be at most 50 characters")
    private String username;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    /** Optional — can be null if user prefers phone-only registration. */
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    /** Optional — can be null if user prefers email-only registration. */
    @Size(max = 15, message = "Phone number must be at most 15 characters")
    private String phoneNumber;

    /**
     * Plain-text password. UserService passes this through
     * BCryptPasswordEncoder before saving — the raw value is never persisted.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /** Optional at registration. Defaults to BEGINNER if not provided. */
    private InvestmentYears investmentYears;

    /** Optional at registration. Defaults to BEGINNER if not provided. */
    private InvestmentAmount investmentAmount;

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

    public InvestmentYears getInvestmentYears() { return investmentYears; }
    public void setInvestmentYears(InvestmentYears investmentYears) { this.investmentYears = investmentYears; }

    public InvestmentAmount getInvestmentAmount() { return investmentAmount; }
    public void setInvestmentAmount(InvestmentAmount investmentAmount) { this.investmentAmount = investmentAmount; }
}
