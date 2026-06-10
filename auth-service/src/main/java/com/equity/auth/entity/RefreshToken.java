package com.equity.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity mapping to the refresh_tokens table.
 *
 * Why store refresh tokens at all?
 * Access tokens are stateless (verified with JWT signature alone).
 * Refresh tokens must be stateful — we need to be able to REVOKE them
 * on logout and ROTATE them on each refresh call. This requires a DB record.
 *
 * Key design decisions:
 *
 * token_hash (not the raw token):
 *   The actual refresh token string is a random UUID. We store only its
 *   SHA-256 hash in the DB, similar to how passwords are BCrypt-hashed.
 *   If the DB is compromised, an attacker gets hashes — useless without the
 *   raw token values which only the client holds.
 *
 * revoked flag:
 *   Set to true on logout OR when the token is rotated (replaced with a new one).
 *   A rotated token is revoked to prevent replay — if someone steals an old
 *   refresh token they cannot use it.
 *
 * No FK to users table:
 *   auth-service and user-service share the same PostgreSQL DB but are
 *   independently deployable microservices. Cross-service FK constraints
 *   would couple them at the DB level — a practice avoided in microservice
 *   design. The relationship is maintained at the application layer.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Logical reference to users.id in user-service.
     * No DB-level FK — see class comment above.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * SHA-256 hex hash of the raw refresh token string.
     * Length: 64 hex characters (256 bits / 4 bits per hex char).
     */
    @Column(name = "token_hash", unique = true, nullable = false, length = 64)
    private String tokenHash;

    /** Absolute expiry timestamp. RefreshTokenService checks this on every use. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * false = valid and usable.
     * true  = revoked (logout or rotation). Must be rejected immediately.
     */
    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
