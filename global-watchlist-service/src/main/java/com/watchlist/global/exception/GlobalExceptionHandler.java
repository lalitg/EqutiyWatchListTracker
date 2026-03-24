package com.watchlist.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} to inherit handling of
 * standard Spring MVC exceptions (400 malformed body, 404 no resource, 405 method
 * not allowed, 415 unsupported media type, etc.) and overrides
 * {@link #handleExceptionInternal} so all those cases return a consistent
 * {@link ErrorResponse} JSON body instead of Spring's default empty or HTML response.
 *
 * <p>Handled cases:
 * <ul>
 *   <li>{@link IllegalArgumentException} — 400 Bad Request (invalid input from caller)</li>
 *   <li>Standard Spring MVC exceptions — routed through {@link #handleExceptionInternal}
 *       which wraps them in an {@link ErrorResponse}</li>
 *   <li>{@link Exception} — 500 Internal Server Error (catch-all for anything else)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles requests for a company code not tracked in the global watchlist.
     *
     * @param ex      the exception thrown by the controller
     * @param request the current HTTP request
     * @return {@code 404 Not Found} with a descriptive message
     */
    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCompanyNotFound(
            CompanyNotFoundException ex, HttpServletRequest request) {
        logger.debug("Company not found at '{}': {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Handles invalid input passed by the caller (e.g. blank company code).
     *
     * @param ex      the exception thrown
     * @param request the current HTTP request
     * @return {@code 400 Bad Request} with error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        logger.warn("Bad request at '{}': {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Catch-all handler for any unhandled exception.
     *
     * <p>Logs the full stack trace and returns a generic 500 without leaking
     * internal error details to the caller.
     *
     * @param ex      the unhandled exception
     * @param request the current HTTP request
     * @return {@code 500 Internal Server Error}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        logger.error("Unhandled exception at '{}': {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", request.getRequestURI());
    }

    /**
     * Overrides the parent handler for {@code @Valid} constraint violations to return
     * field-level error details instead of an empty body.
     *
     * <p>Collects all field errors into a single comma-separated message
     * (e.g. {@code "companyCode: must not be blank"}).
     *
     * @param ex      the validation exception containing one or more field errors
     * @param headers the response headers
     * @param status  the HTTP status (400)
     * @param request the current web request
     * @return {@code 400 Bad Request} with field-level error message
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Validation failed at '{}': {}", path, message);
        ErrorResponse body = new ErrorResponse(status.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), message, path);
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    /**
     * Overrides the parent handler for standard Spring MVC exceptions (400, 404, 405, 415, etc.)
     * to return a consistent {@link ErrorResponse} body instead of an empty body.
     *
     * @param ex      the Spring MVC exception
     * @param body    the default body (ignored — replaced with {@link ErrorResponse})
     * @param headers the response headers
     * @param status  the HTTP status code
     * @param request the current web request
     * @return a {@link ResponseEntity} with an {@link ErrorResponse} body
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        logger.warn("Spring MVC exception at '{}' ({}): {}", path, status, ex.getMessage());
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String reasonPhrase = httpStatus != null ? httpStatus.getReasonPhrase() : "Error";
        String message = ex.getMessage() != null ? ex.getMessage() : "Request could not be processed";
        ErrorResponse errorResponse = new ErrorResponse(status.value(), reasonPhrase, message, path);
        return ResponseEntity.status(status).headers(headers).body(errorResponse);
    }

    /**
     * Builds a {@link ResponseEntity} wrapping an {@link ErrorResponse}.
     *
     * @param status  the HTTP status to return
     * @param message the error detail message
     * @param path    the request path that triggered the error
     * @return a {@link ResponseEntity} with the error body and status
     */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message, path));
    }
}
