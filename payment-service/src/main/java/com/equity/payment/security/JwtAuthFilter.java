package com.equity.payment.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the JWT from the Authorization header, verifies it,
 * and sets the authenticated userId in the SecurityContext.
 *
 * Pattern: "Authorization: Bearer eyJ..."
 *
 * If the token is missing or invalid, the filter passes the request through.
 * Spring Security's authorizeHttpRequests rules then reject protected endpoints.
 *
 * Principal is set to userId (Long) — controllers read it via:
 *   Long userId = (Long) SecurityContextHolder.getContext()
 *                         .getAuthentication().getPrincipal();
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                Long userId = jwtService.extractUserId(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("JWT authenticated: userId={}, uri={}", userId, request.getRequestURI());
            } catch (JwtException | IllegalArgumentException e) {
                logger.warn("JWT rejected for {}: {}", request.getRequestURI(), e.getMessage());
                // Let Spring Security handle the 401 — do not set authentication
            }
        }

        filterChain.doFilter(request, response);
    }
}
