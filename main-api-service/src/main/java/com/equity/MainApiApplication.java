package com.equity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.event.EventListener;

/**
 * Single JVM entry point for the combined main-api-service.
 *
 * Packages three previously standalone services into one Spring Boot fat JAR:
 *   - watchlist-service  (com.equity.watchlist.*)
 *   - user-service       (com.equity.user.*)
 *   - auth-service       (com.equity.auth.*)
 *
 * ─── Bean conflict resolution ──────────────────────────────────────────────
 *
 * Each service was originally a standalone Spring Boot app, so each has its own
 * @SpringBootApplication, AppConfig (PasswordEncoder / RestTemplate), SecurityConfig,
 * and JwtAuthFilter.  Bringing all three onto the same classpath would cause:
 *
 *   - Multiple @SpringBootApplication classes being picked up by component-scan
 *   - Three conflicting SecurityFilterChain beans
 *   - Two duplicate PasswordEncoder beans (auth + user both define one)
 *   - Two duplicate JwtAuthFilter beans (user + watchlist both define one)
 *
 * The excludeFilters below remove every conflicting infrastructure class from
 * the component-scan.  Replacements are provided in com.equity.config:
 *   - com.equity.config.AppConfig      → single PasswordEncoder + RestTemplate
 *   - com.equity.config.SecurityConfig → single unified SecurityFilterChain
 *
 * com.equity.watchlist.security.JwtAuthFilter is kept (NOT excluded) and is
 * injected into the unified SecurityConfig.
 *
 * ─── HTTP self-loop strategy ───────────────────────────────────────────────
 *
 * auth-service's UserServiceClient (calls user-service) and
 * watchlist-service's ProxyController (proxies /api/auth and /api/user) are
 * left completely unchanged.  In application.properties the target URLs are
 * set to http://localhost:8080 — the same port this combined JVM listens on.
 * The calls loop back via localhost TCP (~0.1 ms).  No code changes required.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {
        "com.equity.auth",
        "com.equity.user",
        "com.equity.watchlist",
        "com.equity.config"
    },
    excludeFilters = {
        // ── Standalone application entry points (each has @SpringBootApplication) ──
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            com.equity.auth.AuthServiceApplication.class,
            com.equity.user.UserServiceApplication.class,
            com.equity.watchlist.WatchlistServiceApplication.class
        }),
        // ── Duplicate PasswordEncoder + RestTemplate beans ────────────────────────
        // Replaced by com.equity.config.AppConfig in this module.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            com.equity.auth.config.AppConfig.class,
            com.equity.user.config.AppConfig.class
        }),
        // ── Conflicting SecurityFilterChain beans ─────────────────────────────────
        // Each service's SecurityConfig defines its own chain with .anyRequest() rules
        // that would conflict.  Replaced by com.equity.config.SecurityConfig.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            com.equity.auth.config.SecurityConfig.class,
            com.equity.user.security.SecurityConfig.class,
            com.equity.watchlist.security.SecurityConfig.class
        }),
        // ── Duplicate JwtAuthFilter ───────────────────────────────────────────────
        // user-service and watchlist-service both define a JwtAuthFilter bean.
        // We keep com.equity.watchlist.security.JwtAuthFilter (uses Log4j2, matches
        // the logging setup in this combined app) and exclude user-service's version.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            com.equity.user.security.JwtAuthFilter.class
        }),
        // ── Duplicate GlobalExceptionHandler ─────────────────────────────────────
        // All three services define a class named GlobalExceptionHandler.
        // Spring derives the bean name from the simple class name, so all three
        // resolve to "globalExceptionHandler" and conflict at startup.
        // All three are excluded here; com.equity.config.GlobalExceptionHandler
        // in this module merges all exception types from all three services.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            com.equity.auth.exception.GlobalExceptionHandler.class,
            com.equity.user.exception.GlobalExceptionHandler.class,
            com.equity.watchlist.controller.GlobalExceptionHandler.class
        })
    }
)
public class MainApiApplication {

    private static final Logger logger = LogManager.getLogger(MainApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MainApiApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logger.info("main-api-service is READY — user-service + auth-service + watchlist-service running on port 8080");
    }
}
