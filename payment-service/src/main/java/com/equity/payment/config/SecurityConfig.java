package com.equity.payment.config;

import com.equity.payment.security.JwtAuthFilter;
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
 * Spring Security configuration for payment-service.
 *
 * Public endpoints (no JWT needed):
 *   GET  /api/v1/plans         — pricing page
 *   GET  /api/v1/plans/{id}    — single plan
 *   POST /api/v1/webhooks/**   — Razorpay webhooks (secured by signature, not JWT)
 *   /api/v1/internal/**        — service-to-service (secured by X-Internal-Api-Key header)
 *
 * All other endpoints require a valid JWT (issued by auth-service).
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
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,  "/api/v1/plans").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/v1/plans/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
                // Internal service-to-service lookup. Not open: the controller checks a shared
                // secret header, exactly as user-service does for /internal/users/validate.
                // Spring Security lets it past because there is no JWT on these calls.
                .requestMatchers("/api/v1/internal/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
