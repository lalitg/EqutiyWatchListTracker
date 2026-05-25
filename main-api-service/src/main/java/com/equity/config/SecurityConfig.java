package com.equity.config;

import com.equity.watchlist.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Unified Spring Security configuration for the combined main-api-service.
 *
 * Each of the three original services had its own SecurityConfig bean:
 *   - auth-service:      all endpoints public (it issues tokens, not consumes them)
 *   - user-service:      JWT filter + protected /api/v1/users/** except register + internal
 *   - watchlist-service: JWT filter + protected /api/v1/watchlist/**
 *
 * All three per-service SecurityConfig classes are excluded from component-scan
 * in MainApiApplication.  This single config replaces all three with unified rules.
 *
 * JwtAuthFilter used:
 *   com.equity.watchlist.security.JwtAuthFilter — kept from the watchlist-service
 *   library (uses Log4j2, consistent with the combined app's logging setup).
 *   user-service's JwtAuthFilter is excluded — identical logic, duplicate bean.
 *
 * Access rules (in order):
 *
 *   PUBLIC (no JWT required):
 *     POST /api/v1/auth/**         — login, signup, refresh, logout (auth-service)
 *     /api/auth/**                 — proxy path → forwards to /api/v1/auth/**
 *     /api/user/**                 — proxy path → forwards to /api/v1/users/**
 *     POST /api/v1/users/register  — user registration
 *     /api/v1/internal/**          — internal user validate (guarded by API key in controller)
 *     /api/v1/companies/**         — company search typeahead (read-only)
 *     /api/news/**                 — proxied to JVM 2 news service
 *     /api/events/**               — proxied to JVM 2 events service
 *     /api/global-watchlist/**     — proxied to JVM 2 global-watchlist service
 *     /api/fast-movers/**          — proxied to JVM 2 fast-movers service
 *     /api/internal/**             — proxied to JVM 2 news service (internal notifications)
 *     /**                          — React SPA static assets + frontend routes
 *
 *   PROTECTED (valid JWT required):
 *     /api/v1/watchlist/**         — all watchlist CRUD operations
 *     /api/v1/users/**             — user profile endpoints (change password, update profile)
 *       exceptions above: register + internal are already declared PUBLIC
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST API — no sessions, no cookies
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth

                // ── auth-service endpoints (all public — auth-service issues tokens) ──
                .requestMatchers("/api/v1/auth/**").permitAll()

                // ── Proxy paths (watchlist frontend calls these) ──────────────────────
                // ProxyController forwards /api/auth/** → /api/v1/auth/**
                //                          /api/user/** → /api/v1/users/**
                // Both must be public so the frontend can reach login/signup without a JWT.
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/user/**").permitAll()

                // ── user-service public endpoints ─────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                // Internal endpoint — protected by X-Internal-Api-Key header in controller,
                // not by JWT.  Security layer just lets it through.
                .requestMatchers("/api/v1/internal/**").permitAll()

                // ── Company search — read-only, no user context needed ─────────────────
                .requestMatchers("/api/v1/companies/**").permitAll()

                // ── Proxy paths to JVM 2 services (news, events, schedulers) ──────────
                .requestMatchers("/api/news/**").permitAll()
                .requestMatchers("/api/events/**").permitAll()
                .requestMatchers("/api/global-watchlist/**").permitAll()
                .requestMatchers("/api/fast-movers/**").permitAll()
                .requestMatchers("/api/internal/**").permitAll()

                // ── Protected: all watchlist operations require a valid JWT ─────────────
                .requestMatchers("/api/v1/watchlist/**").authenticated()

                // ── Protected: user profile operations require a valid JWT ──────────────
                // (register + internal are already declared public above)
                .requestMatchers("/api/v1/users/**").authenticated()

                // ── Everything else — React SPA static files + frontend routes ─────────
                .anyRequest().permitAll()
            )

            // JWT filter runs before Spring's default form-login filter.
            // Reads Authorization: Bearer <token>, verifies it, sets SecurityContext.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // Return HTTP 401 (not a redirect to login page) for unauthenticated requests.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );

        return http.build();
    }
}
