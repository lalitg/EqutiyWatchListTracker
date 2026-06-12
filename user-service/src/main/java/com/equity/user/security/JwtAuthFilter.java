package com.equity.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT authentication filter — runs once per request before Spring Security's
 * default UsernamePasswordAuthenticationFilter.
 *
 * What it does:
 *   1. Reads the Authorization header. If it doesn't start with "Bearer ",
 *      the request passes through unauthenticated (SecurityConfig will then
 *      reject it for protected endpoints).
 *   2. Parses and verifies the JWT using HMAC-SHA256 with the shared secret.
 *   3. On success: builds a UsernamePasswordAuthenticationToken with:
 *        - principal = userId (Long, from JWT `sub` claim)
 *        - credentials = null (we don't store credentials in the thread)
 *        - authorities = ["ROLE_CLIENT"] or ["ROLE_ADMIN"] from `userType` claim
 *      and sets it in SecurityContextHolder so downstream code can read it.
 *   4. On failure (expired, tampered, wrong signature): logs a warning and
 *      lets the request continue unauthenticated. SecurityConfig then rejects
 *      it for protected endpoints with HTTP 401.
 *
 * Why OncePerRequestFilter?
 *   Guarantees the filter executes exactly once per HTTP request, even if the
 *   request is dispatched forward internally by Spring MVC.
 *
 * JWT claims expected (issued by auth-service):
 *   sub            → userId as a string, e.g. "42"
 *   username       → login handle
 *   userType       → "CLIENT" or "ADMIN"
 *   investorCategory → "BEGINNER" / "INTERMEDIATE" / "ADVANCED" / "PRO"
 *   iat            → issued-at timestamp
 *   exp            → expiry (15 minutes from issue)
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final SecretKey signingKey;

    /**
     * The JWT secret is injected from application.properties / environment.
     * The same secret must be configured in auth-service so tokens it signs
     * can be verified here.
     */
    public JwtAuthFilter(@Value("${auth.jwt.secret}") String jwtSecret) {
        // Derive an HMAC-SHA256 key from the raw secret bytes.
        // Both services must use the same derivation — raw UTF-8 bytes here.
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No Authorization header or not a Bearer token — skip JWT processing.
        // SecurityConfig will handle access control for this unauthenticated request.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);  // strip "Bearer "

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // sub is the userId stored as a string in the JWT
            Long userId = Long.parseLong(claims.getSubject());

            // Map userType claim → Spring Security GrantedAuthority
            String userType = claims.get("userType", String.class);
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + userType)
            );

            // Build the authentication object.
            // principal = userId (Long) — controllers read this via:
            //   Long userId = (Long) SecurityContextHolder.getContext()
            //                         .getAuthentication().getPrincipal();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException ex) {
            // Expired, tampered, or wrong signature — reject silently.
            // SecurityConfig will return 401 for protected endpoints.
            logger.warn("JWT validation failed: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }
}
