package com.equity.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application-level Spring beans.
 *
 * Kept separate from SecurityConfig so that PasswordEncoder can be injected
 * into UserService without creating a circular dependency
 * (SecurityConfig → UserService → PasswordEncoder → SecurityConfig).
 *
 * BCrypt strength 12:
 * - Strength 10 (Spring default) takes ~100ms per hash on modern hardware.
 * - Strength 12 takes ~400ms — strong enough to make brute-force attacks
 *   expensive while still acceptable for a registration/login flow.
 * - Do NOT increase beyond 12 on a t3.micro (1 vCPU) as it will cause
 *   login timeouts under load.
 */
@Configuration
public class AppConfig {

    /**
     * BCrypt password encoder with strength 12.
     *
     * Used by UserService to:
     *   - Hash plain-text passwords on registration (encode)
     *   - Verify password on change-password flow (matches)
     *
     * Auth-service also uses BCrypt.matches() to verify login credentials
     * against the hash returned by the internal /validate endpoint.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
