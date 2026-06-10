package com.equity.auth.exception;

/**
 * Thrown when login fails due to wrong password or unknown identifier.
 *
 * Mapped to HTTP 401 Unauthorized by GlobalExceptionHandler.
 *
 * Security note: the message is intentionally vague ("Invalid credentials")
 * and does NOT say whether the username or password was wrong.
 * Distinguishing the two would allow attackers to enumerate valid usernames
 * via login attempts (username enumeration attack).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
