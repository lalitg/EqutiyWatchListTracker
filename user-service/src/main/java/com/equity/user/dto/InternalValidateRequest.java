package com.equity.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the internal endpoint:
 *   POST /api/v1/internal/users/validate
 *
 * Called exclusively by auth-service during the login flow.
 * auth-service sends the username the user typed; user-service looks it up
 * and returns credentials + role so auth-service can verify the password
 * and issue a JWT without needing direct DB access.
 *
 * This endpoint is NOT exposed to the public internet — it is protected by
 * the X-Internal-Api-Key header checked in InternalUserController.
 */
public class InternalValidateRequest {

    @NotBlank(message = "Username is required")
    private String username;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
