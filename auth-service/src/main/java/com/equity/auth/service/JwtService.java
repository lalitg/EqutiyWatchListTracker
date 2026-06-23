package com.equity.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Responsible for creating (signing) JWT access tokens.
 *
 * auth-service is the ONLY place that calls Jwts.builder() to SIGN tokens.
 * user-service and watchlist-service only VERIFY — they call Jwts.parser().
 *
 * JWT payload (from LLD Section 10):
 * {
 *   "sub":              "42",          // userId as string
 *   "username":         "alice",
 *   "userType":         "CLIENT",      // CLIENT | ADMIN
 *   "investorCategory": "ADVANCED",   // BEGINNER | INTERMEDIATE | ADVANCED | PRO
 *   "iat":              1711929600,    // issued-at (Unix timestamp)
 *   "exp":              1711930500    // expiry = iat + 900 seconds
 * }
 *
 * Algorithm: HMAC-SHA256 (HS256) with a shared secret.
 * The same secret must be set in JWT_SECRET env var on all services.
 *
 * Why embed userType and investorCategory in the JWT?
 * Stateless design — watchlist-service can gate features by reading these
 * claims directly from the token without calling user-service on every request.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpirySeconds;

    public JwtService(@Value("${auth.jwt.secret}") String jwtSecret,
                      @Value("${auth.jwt.access-expiry-seconds}") long accessExpirySeconds) {
        this.signingKey          = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirySeconds = accessExpirySeconds;
    }

    /**
     * Creates and signs a JWT access token.
     *
     * @param userId          the user's DB id (stored as JWT subject)
     * @param username        login handle
     * @param userType        "CLIENT" or "ADMIN"
     * @param investorCategory "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "PRO"
     * @return signed JWT string — send this to the client as accessToken
     */
    public String generateAccessToken(Long userId, String username,
                                       String userType, String investorCategory) {
        long nowMillis = System.currentTimeMillis();
        Date issuedAt  = new Date(nowMillis);
        Date expiry    = new Date(nowMillis + accessExpirySeconds * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("userType", userType)
                .claim("investorCategory", investorCategory)
                .issuedAt(issuedAt)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the access token TTL in seconds.
     * Included in AuthResponse as expiresIn so the frontend knows
     * when to proactively call /refresh.
     */
    public long getAccessExpirySeconds() {
        return accessExpirySeconds;
    }
}
