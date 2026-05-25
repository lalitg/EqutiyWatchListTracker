package com.equity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

/**
 * Application-level Spring beans for the combined main-api-service.
 *
 * Both auth-service and user-service originally defined their own AppConfig with
 * a PasswordEncoder bean.  auth-service also defined a RestTemplate bean.
 * Both of those AppConfig classes are excluded from component-scan in
 * MainApiApplication, and this single class replaces them.
 *
 * Beans provided:
 *
 *   PasswordEncoder — BCrypt strength 12 (~400 ms/hash, acceptable on t3.micro).
 *     Used by:
 *       UserService   — hashes plain passwords on registration
 *       AuthService   — verifies login credential via BCrypt.matches()
 *
 *   RestTemplate — shared synchronous HTTP client.
 *     Used by:
 *       UserServiceClient  — self-loop calls to localhost:8080 (user endpoints)
 *       ProxyController    — forwards /api/news, /api/events, etc. to JVM 2 services
 */
@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
