package com.equity.auth.service;

import com.equity.auth.entity.RefreshToken;
import com.equity.auth.exception.TokenException;
import com.equity.auth.repository.RefreshTokenRepository;
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
 * Manages the full lifecycle of refresh tokens:
 * create → validate → rotate → revoke.
 *
 * Key design — store only the hash, never the raw token:
 *   The raw refresh token is a random UUID (128-bit random value).
 *   We SHA-256 hash it before storing in the DB.
 *   If the DB is compromised the attacker gets hashes — useless without the
 *   raw values. This mirrors how passwords are stored as BCrypt hashes.
 *
 * Refresh token rotation (from LLD Section 7.3):
 *   Every /refresh call revokes the old token and creates a new one.
 *   This means each refresh token can only be used ONCE.
 *   If an attacker steals a refresh token and the legitimate user refreshes
 *   first, the stolen token is already revoked before the attacker uses it.
 */
@Service
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpiryDays;
    private final long idleTimeoutMinutes;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                @Value("${auth.jwt.refresh-expiry-days}") long refreshExpiryDays,
                                @Value("${auth.refresh-token.idle-timeout-minutes:30}") long idleTimeoutMinutes) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpiryDays      = refreshExpiryDays;
        this.idleTimeoutMinutes     = idleTimeoutMinutes;
    }

    /**
     * Creates a new refresh token for the user.
     *
     * Steps:
     *   1. Generate a random UUID — this is the raw token sent to the client.
     *   2. SHA-256 hash the UUID — only the hash is stored in the DB.
     *   3. Save RefreshToken entity with hash + expiry + userId.
     *   4. Return the RAW token string to the caller (AuthService).
     *      AuthService puts it in the AuthResponse. It is never stored raw.
     *
     * @param userId the user this token belongs to
     * @return the raw refresh token string (UUID format) — send to client
     */
    public String createRefreshToken(Long userId) {
        String rawToken  = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshExpiryDays));
        refreshToken.setRevoked(false);
        refreshToken.setLastUsedAt(LocalDateTime.now());

        refreshTokenRepository.save(refreshToken);
        return rawToken;  // return raw — only sent to client once, never stored
    }

    /**
     * Validates a refresh token sent by the client.
     *
     * Steps:
     *   1. SHA-256 hash the incoming raw token.
     *   2. Look up by hash in the DB.
     *   3. Reject if: not found, already revoked, or past expiry.
     *   4. Return the valid RefreshToken entity.
     *
     * Called by AuthService on /refresh and /logout before taking action.
     *
     * @param rawToken the raw token string sent by the client
     * @return the valid RefreshToken entity
     * @throws TokenException if invalid, revoked, or expired
     */
    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String rawToken) {
        String tokenHash = sha256(rawToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenException("Refresh token not found"));

        if (token.isRevoked()) {
            throw new TokenException("Refresh token has been revoked");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenException("Refresh token has expired");
        }

        if (token.getLastUsedAt() != null) {
            LocalDateTime idleDeadline = token.getLastUsedAt().plusMinutes(idleTimeoutMinutes);
            if (idleDeadline.isBefore(LocalDateTime.now())) {
                throw new TokenException("Session expired due to inactivity");
            }
        }

        return token;
    }

    /**
     * Rotates a refresh token: revokes the old one and creates a new one.
     *
     * Called on every /refresh request.
     * Returns the new raw refresh token to be sent to the client.
     *
     * @param oldToken the validated RefreshToken entity to revoke
     * @return new raw refresh token string
     */
    public String rotateRefreshToken(RefreshToken oldToken) {
        // Revoke the old token — it cannot be used again
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        // Create a fresh token; lastUsedAt is set inside createRefreshToken, resetting the idle clock
        return createRefreshToken(oldToken.getUserId());
    }

    /**
     * Revokes ALL active refresh tokens for a user.
     * Called on logout — terminates all active sessions (all devices).
     *
     * @param userId the user to log out
     */
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    /**
     * Computes SHA-256 hash of a string and returns it as a lowercase hex string.
     * Used to hash the raw refresh token before DB storage or lookup.
     *
     * SHA-256 always produces 32 bytes = 64 hex characters.
     * This matches the token_hash VARCHAR(64) column definition.
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in the JVM — this cannot happen
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
