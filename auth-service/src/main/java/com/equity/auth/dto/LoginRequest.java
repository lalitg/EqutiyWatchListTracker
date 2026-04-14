package com.equity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/auth/login.
 *
 * The LLD specifies a flexible "identifier" field that can be:
 *   - a username  (e.g. "alice")
 *   - an email    (e.g. "alice@example.com")
 *   - a phone     (e.g. "+919876543210")
 *
 * AuthService.login() passes this identifier to user-service's internal
 * /validate endpoint which resolves it to a user record.
 *
 * Why a single "identifier" field instead of three separate fields?
 * Cleaner API — the client sends whatever the user typed in the login field.
 * The resolution logic lives in user-service (single responsibility).
 */
public class LoginRequest {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
