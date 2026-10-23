package com.equity.watchlist.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.equity.watchlist.exception.EntitlementRequiredException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Global exception handler for all REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation and business rule violations — returns 400 Bad Request.
     */
    /**
     * The request was valid; the plan was not big enough. 403 rather than 400, and the body names
     * the tier and the feature so the browser can link straight to the row that explains it.
     */
    @ExceptionHandler(EntitlementRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleEntitlement(EntitlementRequiredException ex) {
        logger.info("Entitlement required: {} ({})", ex.getMessage(), ex.getFeature());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", ex.getMessage(),
                "code", "UPGRADE_REQUIRED",
                "requiredTier", ex.getRequiredTier(),
                "feature", ex.getFeature()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
