package com.equity.auth.repository;

import com.equity.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the password_reset_tokens table.
 *
 * findByTokenHash — the only lookup needed:
 *   When a user submits a reset token, we SHA-256 hash it and look it up here.
 *   The entity holds the email, expiry, and used flag — all we need to
 *   validate and consume the token.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
