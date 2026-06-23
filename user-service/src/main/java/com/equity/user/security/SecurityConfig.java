package com.equity.user.security;

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
 * Spring Security configuration for the User Service.
 *
 * Design:
 *   - Stateless (no sessions, no cookies) — every request must carry a JWT.
 *   - CSRF disabled — not needed for stateless REST APIs (CSRF exploits rely
 *     on session cookies, which we don't use).
 *   - JwtAuthFilter is inserted before Spring's default form-login filter so
 *     that JWT authentication runs first on every request.
 *
 * Access rules:
 *   POST /api/v1/users/register  → public (no JWT needed — this IS how users get created)
 *   /api/v1/internal/**          → permitted at the Security layer; the API key
 *                                   check is done inside InternalUserController
 *                                   (simpler than a full filter for one endpoint)
 *   Everything else              → requires a valid JWT (set by JwtAuthFilter)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Builds and returns the security filter chain.
     *
     * Lambda-based DSL (Spring Security 6 / Spring Boot 3):
     *   - csrf.disable()  — safe for stateless REST
     *   - sessionManagement STATELESS — no HttpSession created or used
     *   - authorizeHttpRequests — fine-grained URL access rules
     *   - addFilterBefore  — JwtAuthFilter runs before the standard auth filter
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Registration is public — no token needed
                .requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                // Internal endpoints are permitted here; InternalUserController
                // verifies the X-Internal-Api-Key header before processing
                .requestMatchers("/api/v1/internal/**").permitAll()
                // All other requests require a valid JWT
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Return 401 (not 403) when no JWT is provided on a protected endpoint
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );

        return http.build();
    }
}
