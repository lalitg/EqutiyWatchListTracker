package com.equity.config;

import com.equity.auth.exception.InvalidCredentialsException;
import com.equity.auth.exception.TokenException;
import com.equity.auth.exception.UserBlockedException;
import com.equity.user.exception.InvalidPasswordException;
import com.equity.user.exception.UserAlreadyExistsException;
import com.equity.user.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Single merged exception handler for the combined main-api-service.
 *
 * All three services originally had their own GlobalExceptionHandler class.
 * Spring derives the bean name from the simple class name, so all three
 * resolved to "globalExceptionHandler" and caused a ConflictingBeanDefinitionException
 * at startup.  All three are excluded from component-scan in MainApiApplication
 * and this class handles every exception type from all three services.
 *
 * Covered exceptions:
 *   auth-service  : InvalidCredentialsException, UserBlockedException, TokenException
 *   user-service  : UserNotFoundException, UserAlreadyExistsException, InvalidPasswordException
 *   watchlist     : IllegalArgumentException
 *   shared        : MethodArgumentNotValidException (@Valid failures), Exception (catch-all)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    // ── auth-service exceptions ───────────────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        logger.warn("InvalidCredentials: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(UserBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleUserBlocked(UserBlockedException ex) {
        logger.warn("UserBlocked: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<Map<String, Object>> handleToken(TokenException ex) {
        logger.warn("TokenException: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // ── user-service exceptions ───────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        logger.warn("UserNotFound: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        logger.warn("UserAlreadyExists: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(InvalidPasswordException ex) {
        logger.warn("InvalidPassword: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── watchlist-service exceptions ──────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("BadRequest: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── shared: @Valid validation failures ────────────────────────────────────

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

    // ── SPA fallback — forward React Router paths to index.html ──────────────
    // NoResourceFoundException is thrown when Spring can't find a static resource
    // for a React Router path (e.g. /watchlist, /market/nifty50) on browser refresh.
    // Forward to index.html so React Router resolves the route client-side.

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource(NoResourceFoundException ex,
                                  HttpServletRequest request,
                                  HttpServletResponse response) throws IOException, jakarta.servlet.ServletException {
        request.getRequestDispatcher("/index.html").forward(request, response);
    }

    // ── catch-all ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        logger.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
