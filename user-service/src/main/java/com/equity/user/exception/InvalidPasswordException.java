package com.equity.user.exception;

/**
 * Thrown when a user supplies an incorrect current password during
 * PUT /api/v1/users/me/password.
 *
 * Caught by GlobalExceptionHandler and mapped to HTTP 400 Bad Request.
 *
 * Why 400 and not 401?
 * The user is already authenticated (JWT is valid). The failure is in the
 * request body (wrong current password), which is a bad-request scenario,
 * not an authentication failure.
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
