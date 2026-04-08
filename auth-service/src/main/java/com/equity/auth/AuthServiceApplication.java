package com.equity.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Main entry point for the Auth Service.
 *
 * Responsibilities:
 * - Signup: validates input, calls user-service to create the user, returns 201
 * - Login: calls user-service internal /validate, BCrypt-verifies password,
 *          issues access token (15 min) + refresh token (30 days)
 * - Token refresh: validates refresh token, rotates it, issues new pair
 * - Logout: revokes the refresh token
 *
 * Runs on port 8088.
 * Auth-service is the ONLY service that signs JWTs.
 * All other services (user-service, watchlist-service) only VERIFY them.
 */
@SpringBootApplication
public class AuthServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logger.info("Auth Service is READY on port 8088");
    }
}
