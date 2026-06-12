package com.equity.auth.repository;

import com.equity.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for RefreshToken.
 * All SQL is auto-generated from method names or JPQL annotations.
 *
 * findByTokenHash    → used on every /refresh and /logout call to look up the token
 * revokeAllForUser   → used on logout to revoke ALL active sessions for a user
 *                      (useful if "logout from all devices" is needed)
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Finds a refresh token record by its SHA-256 hash.
     * Called on /refresh and /logout with the hash of the token sent by the client.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes ALL non-revoked refresh tokens for a given user in one UPDATE.
     * Called on logout so all active sessions are terminated simultaneously.
     *
     * @Modifying  — tells Spring Data this is a write query (UPDATE/DELETE)
     * @Query      — custom JPQL since there is no method-name equivalent for bulk update
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    void revokeAllForUser(Long userId);
}
