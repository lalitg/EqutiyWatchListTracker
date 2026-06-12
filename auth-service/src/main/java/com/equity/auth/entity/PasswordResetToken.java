package com.equity.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for the password_reset_tokens table.
 *
 * Stores one-time tokens used in the forgot-password flow.
 *
 * How it works:
 *   1. User calls POST /api/v1/auth/forgot-password with their email.
 *   2. auth-service generates a random UUID (raw token), SHA-256 hashes it,
 *      and saves this entity. The raw token is returned to the caller.
 *   3. User calls POST /api/v1/auth/reset-password with the raw token + new password.
 *   4. auth-service hashes the incoming token, looks it up here, validates
 *      it (not expired, not used), marks it used, and resets the password.
 *
 * Security decisions (same pattern as refresh_tokens):
 *   - Only the SHA-256 hash is stored — never the raw token.
 *   - Token expires in 15 minutes (configurable).
 *   - Single-use: the `used` flag is set true on first successful consumption.
 *
 * Table created by Hibernate (ddl-auto=update) on first startup.
 * Reference DDL: scripts/create_password_reset_tokens_table.sql
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The email address this reset token was issued for. */
    @Column(nullable = false, length = 150)
    private String email;

    /** SHA-256 hex of the raw UUID token. 64 chars. Never the raw token. */
    @Column(name = "token_hash", unique = true, nullable = false, length = 64)
    private String tokenHash;

    /** When this token expires (typically 15 minutes after creation). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** True once the token has been used — prevents replay. */
    @Column(nullable = false)
    private boolean used = false;

    /** Set once on INSERT; never updated. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PasswordResetToken() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
