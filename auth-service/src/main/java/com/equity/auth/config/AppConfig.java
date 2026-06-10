package com.equity.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

/**
 * Application-level Spring beans.
 *
 * Kept separate from SecurityConfig to avoid circular dependency:
 *   SecurityConfig → AuthService → PasswordEncoder → SecurityConfig
 *
 * BCrypt strength 12: ~400ms per hash — strong against brute force,
 * acceptable for login/signup on a t3.micro.
 *
 * RestTemplate: used by UserServiceClient to make HTTP calls to user-service.
 * A single shared instance is sufficient — RestTemplate is thread-safe.
 */
@Configuration
public class AppConfig {

    /**
     * BCrypt encoder used by AuthService to:
     * - Verify password on login: BCrypt.matches(plainPassword, hashFromUserService)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * HTTP client for synchronous REST calls to user-service.
     * Used exclusively by UserServiceClient.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
