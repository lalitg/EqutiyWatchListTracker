package com.equity.auth.service;

import com.equity.auth.entity.PasswordResetToken;
import com.equity.auth.exception.TokenException;
import com.equity.auth.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages the lifecycle of password-reset tokens: create → validate → consume.
 *
 * Same security design as RefreshTokenService:
 *   - Raw token is a random UUID — sent to the user, never stored.
 *   - Only the SHA-256 hash of the raw token is stored in the DB.
 *   - Token is single-use: the `used` flag is set true on first consumption.
 *   - Token expires in 15 minutes (configurable via auth.forgot-password.expiry-minutes).
 *
 * Flow:
 *   createResetToken(email)
 *     → generates UUID, hashes it, saves PasswordResetToken, returns raw UUID
 *   validateAndConsume(rawToken)
 *     → hashes incoming token, looks up in DB, checks expiry + used flag,
 *       marks used=true, returns the email the token was issued for
 */
@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository passwordResetTokenRepo;

    @Value("${auth.forgot-password.expiry-minutes:15}")
    private int expiryMinutes;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepo) {
        this.passwordResetTokenRepo = passwordResetTokenRepo;
    }

    /**
     * Generates a password reset token for the given email address.
     *
     * Steps:
     *   1. Generate a random UUID — this is the raw token returned to the caller.
     *   2. SHA-256 hash the UUID — only this hash is stored in the DB.
     *   3. Save PasswordResetToken with email, hash, and expiry.
     *   4. Return the raw token (shown to the user / emailed in production).
     *
     * Note: This method does NOT verify that the email exists in user-service.
     * That check happens implicitly at the reset step when user-service is called.
     * This prevents an attacker from enumerating registered emails via this endpoint.
     *
     * @param email the email address to issue the token for (already lowercased by caller)
     * @return the raw reset token string — give this to the user
     */
    public String createResetToken(String email) {
        String rawToken  = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        token.setCreatedAt(LocalDateTime.now());
        passwordResetTokenRepo.save(token);

        log.info("Password reset token created for email={}", email);
        return rawToken;
    }

    /**
     * Validates a reset token and marks it as used.
     *
     * Steps:
     *   1. SHA-256 hash the incoming raw token.
     *   2. Look up by hash in the DB.
     *   3. Reject if not found, already used, or expired.
     *   4. Mark used=true — the token cannot be reused after this call.
     *   5. Return the email address stored on the token.
     *
     * The returned email is used by auth-service to tell user-service which
     * account's password to update.
     *
     * @param rawToken the raw token string submitted by the user
     * @return the email address this token was issued for
     * @throws TokenException if the token is invalid, already used, or expired
     */
    public String validateAndConsume(String rawToken) {
        String tokenHash = sha256(rawToken);

        PasswordResetToken token = passwordResetTokenRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenException("Invalid or expired password reset token"));

        if (token.isUsed()) {
            throw new TokenException("Password reset token has already been used");
        }

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new TokenException("Password reset token has expired");
        }

        token.setUsed(true);
        passwordResetTokenRepo.save(token);

        log.info("Password reset token consumed for email={}", token.getEmail());
        return token.getEmail();
    }

    /**
     * SHA-256 hash of the input string, returned as a lowercase hex string.
     * Identical implementation to RefreshTokenService — both hash tokens the same way.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
