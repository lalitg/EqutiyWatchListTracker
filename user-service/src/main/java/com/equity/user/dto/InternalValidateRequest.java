package com.equity.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the internal endpoint:
 *   POST /api/v1/internal/users/validate
 *
 * Called exclusively by auth-service during the login flow.
 * auth-service sends whatever the user typed in the "identifier" field
 * (username, email, or phone); user-service resolves it against all three
 * columns and returns credentials + role so auth-service can verify the
 * password and issue a JWT without needing direct DB access.
 *
 * This endpoint is NOT exposed to the public internet — it is protected by
 * the X-Internal-Api-Key header checked in InternalUserController.
 */
public class InternalValidateRequest {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
}
