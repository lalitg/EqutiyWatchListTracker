package com.companynews.newsscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the NSE News Scheduler microservice.
 *
 * <p>This service performs two functions:
 * <ol>
 *   <li>Polls the NSE corporate-announcements API every 15 minutes and stores new
 *       announcements per company symbol into the {@code company_news} table.</li>
 *   <li>Polls Google RSS feeds for macro/sector/company keywords on a configurable
 *       daily schedule and stores relevant articles in the same table.</li>
 * </ol>
 *
 * <p>{@code @EnableScheduling} activates Spring's scheduling infrastructure so that
 * all {@code @Scheduled} annotations across the application are picked up and executed.
 * Without this annotation, the scheduler beans are created but never triggered.
 */
@SpringBootApplication
@EnableScheduling
public class NseNewsSchedulerApplication {

    /**
     * Application entry point. Bootstraps the Spring application context.
     *
     * @param args command-line arguments (passed through to Spring Boot)
     */
    public static void main(String[] args) {
        SpringApplication.run(NseNewsSchedulerApplication.class, args);
    }
}
