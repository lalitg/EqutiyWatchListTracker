package com.equity.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Spring Security configuration for the Auth Service.
 *
 * auth-service is simpler than user-service from a security perspective:
 * ALL endpoints are public — signup, login, refresh, and logout do not
 * require an incoming JWT to be present.
 *
 * Why no JwtAuthFilter here?
 * auth-service is the ISSUER of JWTs, not a consumer.
 * - /signup and /login: client has no JWT yet
 * - /refresh: client sends a refresh token in the body, not a Bearer JWT
 * - /logout: client sends a refresh token in the body
 *
 * Spring Security is still kept (not removed entirely) because:
 * 1. BCryptPasswordEncoder bean needs to be available
 * 2. It protects against basic web vulnerabilities (clickjacking etc.)
 * 3. If admin endpoints are added later, the filter chain is already in place
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // All auth endpoints are public — no JWT required to call auth-service
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );

        return http.build();
    }
}
