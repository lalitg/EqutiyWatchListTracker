package com.companynews.newsscheduler.controller;

import jakarta.validation.ConstraintViolationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * <p>WHY this is needed:
 * Without this, Spring returns its default error response which is verbose,
 * inconsistent, and leaks internal stack trace details to callers.
 *
 * <p>With this handler:
 * <ul>
 *   <li>All validation failures return a clean, structured JSON body.</li>
 *   <li>HTTP status codes are correct (400 for bad input, 500 for unexpected errors).</li>
 *   <li>No stack traces leak to the API caller.</li>
 *   <li>All errors are logged server-side for debugging.</li>
 * </ul>
 *
 * <p>Handles three error types:
 * <ol>
 *   <li>{@link ConstraintViolationException} — {@code @NotBlank} on {@code @RequestParam} (NewsController).</li>
 *   <li>{@link MethodArgumentNotValidException} — {@code @Valid} on {@code @RequestBody} (WatchlistEventController).</li>
 *   <li>{@link MissingServletRequestParameterException} — required param not sent at all.</li>
 *   <li>{@link Exception} — unexpected runtime errors (catch-all).</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles violations from {@code @NotBlank} / {@code @Valid} on {@code @RequestParam}.
     *
     * <p>Triggered when: {@code GET /api/news?key=} (blank key).
     *
     * @param ex the constraint violation exception containing field-level violation details
     * @return 400 Bad Request with a structured error body listing each violated constraint
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
     * Handles violations from {@code @Valid} on {@code @RequestBody} DTOs.
     *
     * <p>Triggered when: {@code POST /api/internal/watchlist/added} with blank or missing symbol.
     *
     * @param ex the binding result exception containing field-level error messages
     * @return 400 Bad Request with a structured error body listing each field error
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
     * Handles missing required {@code @RequestParam} (parameter not sent at all).
     *
     * <p>Triggered when: {@code GET /api/news} (no {@code key} param provided).
     *
     * @param ex the exception identifying which parameter was missing
     * @return 400 Bad Request naming the missing parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex) {

        log.warn("Missing required request parameter: {}", ex.getParameterName());
        return ResponseEntity.badRequest()
            .body(errorBody("Missing required parameter: " + ex.getParameterName(), List.of()));
    }

    /**
     * Catch-all handler for any unexpected runtime exception not matched by the handlers above.
     *
     * <p>Logs the full stack trace server-side but returns no internal details to the caller,
     * preventing information leakage.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("An unexpected error occurred", List.of()));
    }

    /**
     * Builds a structured error response body suitable for JSON serialization.
     *
     * @param message a human-readable summary of the error
     * @param errors  a list of individual error details (may be empty for generic errors)
     * @return an ordered map containing {@code timestamp}, {@code status}, {@code message},
     *         and optionally {@code errors}
     */
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
