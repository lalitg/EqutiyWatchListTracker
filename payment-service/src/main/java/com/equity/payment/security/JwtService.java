package com.equity.payment.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT verification for payment-service.
 *
 * payment-service ONLY verifies JWTs — it never signs them.
 * auth-service is the sole issuer.
 *
 * The same JWT_SECRET must be configured on all services.
 * payment-service uses it to verify the HMAC-SHA256 signature.
 *
 * Extracted claims used:
 *   sub → userId (Long) — identifies the authenticated user
 */
@Service
public class JwtService {

    private final SecretKey verifyKey;

    public JwtService(@Value("${auth.jwt.secret}") String jwtSecret) {
        this.verifyKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses and validates the JWT. Returns all claims if valid.
     * Throws JwtException if signature is invalid or token is expired.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(verifyKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts userId from the JWT subject claim.
     * auth-service sets sub = String.valueOf(userId).
     */
    public Long extractUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * Returns true if the token parses without exception.
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
