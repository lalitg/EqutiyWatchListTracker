package com.watchlist.global.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Uniform JSON error body returned by {@link GlobalExceptionHandler} for all error responses.
 *
 * <p>Example response body:
 * <pre>{@code
 * {
 *   "timestamp": "2026-03-23T17:05:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Company 'XYZ' not found in global watchlist",
 *   "path": "/api/global-watchlist/XYZ"
 * }
 * }</pre>
 */
public class ErrorResponse {

    /** Timestamp when the error occurred. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    /** HTTP status code (e.g. 400, 404, 500). */
    private final int status;

    /** Short HTTP status description (e.g. "Bad Request", "Not Found"). */
    private final String error;

    /** Human-readable error detail. */
    private final String message;

    /** The request path that triggered the error. */
    private final String path;

    /**
     * Constructs an error response with all fields.
     *
     * @param status  HTTP status code
     * @param error   short status description
     * @param message human-readable error detail
     * @param path    request path that triggered the error
     */
    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status    = status;
        this.error     = error;
        this.message   = message;
        this.path      = path;
    }

    /** @return the timestamp when the error occurred */
    public LocalDateTime getTimestamp() { return timestamp; }

    /** @return the HTTP status code */
    public int getStatus() { return status; }

    /** @return the short HTTP status description */
    public String getError() { return error; }

    /** @return the human-readable error detail */
    public String getMessage() { return message; }

    /** @return the request path that triggered the error */
    public String getPath() { return path; }
}
