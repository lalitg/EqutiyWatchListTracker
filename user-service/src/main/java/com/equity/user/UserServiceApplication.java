package com.equity.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Main entry point for the User Service.
 *
 * Responsibilities:
 * - User registration (hashes password with BCrypt before persisting)
 * - Profile management: view/update name, email, phone
 * - Investor profile: update investment_years + investment_amount → auto-compute investor_category
 * - Password change
 * - Internal endpoint for auth-service to validate a username during login
 *
 * Runs on port 8087.
 * JWT verification is done inside JwtAuthFilter — every protected endpoint
 * requires a valid Bearer token issued by auth-service.
 */
@SpringBootApplication
public class UserServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logger.info("User Service is READY on port 8087");
    }
}
