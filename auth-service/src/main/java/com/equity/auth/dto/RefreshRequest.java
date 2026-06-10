package com.equity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/auth/refresh.
 *
 * The frontend sends the raw refresh token string it received from
 * /login or the previous /refresh call.
 *
 * AuthService hashes it with SHA-256 and looks up the hash in the DB.
 * The raw token string is never stored.
 */
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
