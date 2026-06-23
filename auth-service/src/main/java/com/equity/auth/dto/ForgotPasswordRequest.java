package com.equity.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/auth/forgot-password.
 *
 * The user provides their registered email address.
 * auth-service generates a one-time reset token and returns it
 * (or emails it in production once an email service is wired up).
 */
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
