package com.equity.auth.dto;

/**
 * Response body returned by POST /api/v1/auth/login and POST /api/v1/auth/refresh.
 *
 * Fields:
 *   accessToken  — signed JWT, valid for 15 minutes (900 seconds).
 *                  The frontend sends this in: Authorization: Bearer <accessToken>
 *                  on every API call to user-service and watchlist-service.
 *
 *   refreshToken — random UUID token, valid for 30 days.
 *                  The frontend stores this (e.g. localStorage) and sends it
 *                  to POST /api/v1/auth/refresh when the access token expires.
 *                  Never sent as an Authorization header.
 *
 *   userType     — "CLIENT" or "ADMIN". Frontend uses this to decide which
 *                  navigation items to show (e.g. Admin Panel link).
 *
 *   expiresIn    — access token TTL in seconds (900). Frontend uses this to
 *                  schedule a proactive /refresh call before expiry.
 */
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String userType;
    private long expiresIn;

    public AuthResponse(String accessToken, String refreshToken, String userType, long expiresIn) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        this.userType     = userType;
        this.expiresIn    = expiresIn;
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getUserType() { return userType; }
    public long getExpiresIn() { return expiresIn; }
}
