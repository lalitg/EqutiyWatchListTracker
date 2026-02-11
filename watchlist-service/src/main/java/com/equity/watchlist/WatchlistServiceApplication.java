package com.equity.watchlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Main entry point for the Watchlist Service application.
 * Auto-creates the PostgreSQL database on first startup.
 */
@SpringBootApplication
public class WatchlistServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistServiceApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Watchlist Service Application");
        createDatabaseIfNotExists(args);
        SpringApplication.run(WatchlistServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Watchlist Service is READY on port 8080");
    }

    /**
     * Connects to the default 'postgres' database and creates the application
     * database if it does not already exist. Runs before Spring context starts.
     */
    private static void createDatabaseIfNotExists(String[] args) {
        String host = resolveProperty(args, "DB_HOST", "localhost");
        String port = resolveProperty(args, "DB_PORT", "5432");
        String dbName = resolveProperty(args, "DB_NAME", "watchlist");
        String username = resolveProperty(args, "DB_USERNAME", "postgres");
        String password = resolveProperty(args, "DB_PASSWORD", "");

        String postgresUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";

        try (Connection conn = DriverManager.getConnection(postgresUrl, username, password)) {
            String checkSql = "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSql)) {
                if (!rs.next()) {
                    stmt.executeUpdate("CREATE DATABASE " + dbName);
                    logger.info("Database '{}' created successfully", dbName);
                } else {
                    logger.info("Database '{}' already exists", dbName);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not auto-create database: {}", e.getMessage());
        }
    }

    /**
     * Resolves a property from command line args (--KEY=value), then environment
     * variables, then falls back to the default value.
     */
    private static String resolveProperty(String[] args, String key, String defaultValue) {
        String prefix = "--" + key + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        String envValue = System.getenv(key);
        return envValue != null ? envValue : defaultValue;
    }
}
