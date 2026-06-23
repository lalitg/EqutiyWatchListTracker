package com.equity.user.enums;

/**
 * Lifecycle state of a user account.
 *
 * ACTIVE  — account is in good standing; login is allowed.
 * BLOCKED — admin has suspended the account; login is rejected by auth-service.
 * DELETED — soft-deleted; record is retained for audit but treated as non-existent
 *           in all public-facing queries.
 *
 * Stored as VARCHAR(10) in the DB column `status`.
 * Soft-delete (DELETED) is preferred over hard-delete to preserve referential
 * integrity with watchlists, subscriptions, and payment records.
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    DELETED
}
