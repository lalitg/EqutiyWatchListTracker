package com.equity.user.exception;

/**
 * Thrown when a registration or profile-update attempts to use a username,
 * email, or phone number that is already taken by another user.
 *
 * Caught by GlobalExceptionHandler and mapped to HTTP 409 Conflict.
 *
 * Use cases:
 * - POST /api/v1/users/register → duplicate username / email / phone
 * - PUT  /api/v1/users/me       → changing email or phone to one already in use
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
