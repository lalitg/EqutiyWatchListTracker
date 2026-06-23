package com.equity.user.exception;

/**
 * Thrown when a requested user does not exist or has been soft-deleted.
 *
 * Caught by GlobalExceptionHandler and mapped to HTTP 404 Not Found.
 *
 * Use cases:
 * - GET /api/v1/users/me  → userId from JWT not found in DB (race condition / deleted account)
 * - PUT  endpoints        → same
 * - Internal /validate    → auth-service sent an unknown username
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
