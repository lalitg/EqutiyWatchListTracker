package com.equity.user.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception → HTTP response mapping for all controllers.
 *
 * @RestControllerAdvice intercepts every exception thrown out of a @RestController
 * and converts it to a structured JSON error response instead of Spring's default
 * white-label error page.
 *
 * Response body shape (consistent across all error responses):
 * {
 *   "timestamp": "2025-01-15T10:30:00",
 *   "status":    404,
 *   "error":     "Not Found",
 *   "message":   "User not found: 42"
 * }
 *
 * Validation errors (from @Valid) include an additional "errors" map:
 * {
 *   ...,
 *   "errors": { "username": "Username is required", "password": "..." }
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ─── Domain exceptions ────────────────────────────────────────────────

    /**
     * Handles UserNotFoundException → HTTP 404.
     * Logged at WARN level (not ERROR) since it is a client-caused condition.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        logger.warn("UserNotFoundException: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles UserAlreadyExistsException → HTTP 409 Conflict.
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        logger.warn("UserAlreadyExistsException: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles InvalidPasswordException → HTTP 400 Bad Request.
     */
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(InvalidPasswordException ex) {
        logger.warn("InvalidPasswordException: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ─── Validation exceptions ────────────────────────────────────────────

    /**
     * Handles @Valid failures on request bodies → HTTP 400.
     *
     * Collects all field-level constraint violations into an "errors" map
     * so the client knows exactly which fields failed and why.
     *
     * Example response:
     * { "errors": { "username": "Username is required", "password": "..." } }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Blocked account — 403 */
    @ExceptionHandler(UserBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleUserBlocked(UserBlockedException ex) {
        logger.warn("UserBlockedException: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ─── Catch-all ────────────────────────────────────────────────────────

    /**
     * Safety net for any unhandled exception → HTTP 500.
     * Logged at ERROR level with full stack trace for investigation.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        logger.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    // ─── Private helper ───────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
