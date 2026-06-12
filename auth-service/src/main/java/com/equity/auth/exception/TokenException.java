package com.equity.auth.exception;

/**
 * Thrown when a refresh token is invalid — expired, revoked, or not found.
 *
 * Mapped to HTTP 401 Unauthorized by GlobalExceptionHandler.
 *
 * From LLD Section 15:
 *   REFRESH_TOKEN_INVALID → 401
 *
 * When the frontend receives 401 on /auth/refresh, it clears localStorage
 * and redirects to the login page (per LLD Section 16).
 */
public class TokenException extends RuntimeException {

    public TokenException(String message) {
        super(message);
    }
}
