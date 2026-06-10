package com.equity.auth.exception;

/**
 * Thrown when a BLOCKED or DELETED user attempts to log in.
 *
 * Mapped to HTTP 403 Forbidden by GlobalExceptionHandler.
 *
 * From LLD Section 13:
 *   "Blocked users: Login returns 403 immediately — checked BEFORE password validation"
 *
 * Why check status BEFORE BCrypt verification?
 * BCrypt.matches() takes ~400ms (strength 12). If we check status after,
 * a blocked user's login still takes 400ms — unnecessary CPU usage.
 * Checking status first: instant 403 for blocked users.
 */
public class UserBlockedException extends RuntimeException {

    public UserBlockedException(String message) {
        super(message);
    }
}
