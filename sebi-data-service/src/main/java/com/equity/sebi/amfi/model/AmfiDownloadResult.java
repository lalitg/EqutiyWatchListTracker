package com.equity.sebi.amfi.model;

import java.time.Instant;

/**
 * Outcome of a single AMFI document download attempt.
 */
public record AmfiDownloadResult(
        boolean success,
        String targetMonth,
        String sourceUrl,
        String savedPath,
        String message,
        Instant attemptedAt
) {
    public static AmfiDownloadResult success(String targetMonth, String sourceUrl, String savedPath) {
        return new AmfiDownloadResult(true, targetMonth, sourceUrl, savedPath, "Downloaded successfully", Instant.now());
    }

    public static AmfiDownloadResult failure(String targetMonth, String sourceUrl, String message) {
        return new AmfiDownloadResult(false, targetMonth, sourceUrl, null, message, Instant.now());
    }
}
