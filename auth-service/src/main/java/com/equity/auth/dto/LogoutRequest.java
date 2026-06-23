package com.equity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/auth/logout.
 *
 * The client sends the refresh token it wants to revoke.
 * AuthService hashes it, finds the DB record, and sets revoked=true.
 *
 * All active refresh tokens for the user are revoked (not just this one),
 * effectively logging out all devices.
 *
 * The access token itself cannot be revoked (stateless JWT). It will
 * naturally expire within its 15-minute TTL. For production, a short
 * TTL is the mitigation — 15 minutes limits the damage of a stolen token.
 */
public class LogoutRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
