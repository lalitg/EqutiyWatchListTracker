package com.companynews.newsscheduler.controller;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized exception handler for all controllers in this service.
 *
 * WHY this is needed:
 * Without this, Spring returns its default error response which is verbose,
 * inconsistent, and leaks internal stack trace details to callers.
 *
 * With this handler:
 * - All validation failures return a clean, structured JSON body
 * - HTTP status codes are correct (400 for bad input, 500 for unexpected errors)
 * - No stack traces leak to the API caller
 * - All errors are logged server-side for debugging
 *
 * Handles three error types:
 * 1. ConstraintViolationException     — @NotBlank on @RequestParam (NewsController)
 * 2. MethodArgumentNotValidException  — @Valid on @RequestBody (WatchlistEventController)
 * 3. Exception                        — unexpected runtime errors (catch-all)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles violations from @NotBlank / @Valid on @RequestParam.
     * Triggered when: GET /api/news?key= (blank key)
     * Returns: 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex) {

        List<String> errors = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .toList();

        log.warn("Request param validation failed: {}", errors);
        return ResponseEntity.badRequest().body(errorBody("Validation failed", errors));
    }

    /**
     * Handles violations from @Valid on @RequestBody DTOs.
     * Triggered when: POST /api/internal/watchlist/added with blank/missing symbol
     * Returns: 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList();

        log.warn("Request body validation failed: {}", errors);
        return ResponseEntity.badRequest().body(errorBody("Validation failed", errors));
    }

    /**
     * Handles missing required @RequestParam entirely (param not sent at all).
     * Triggered when: GET /api/news (no key param provided)
     * Returns: 400 Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {

        log.warn("Missing required request parameter: {}", ex.getParameterName());
        return ResponseEntity.badRequest()
            .body(errorBody("Missing required parameter: " + ex.getParameterName(), List.of()));
    }

    /**
     * Catch-all for any unexpected runtime exception.
     * Returns: 500 Internal Server Error — with no internal detail exposed to caller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("An unexpected error occurred", List.of()));
    }

    private Map<String, Object> errorBody(String message, List<String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", "error");
        body.put("message", message);
        if (!errors.isEmpty()) {
            body.put("errors", errors);
        }
        return body;
    }
}
