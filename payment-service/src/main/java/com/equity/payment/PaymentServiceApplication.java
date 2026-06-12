package com.equity.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Payment Service.
 *
 * Responsibilities:
 * - Serve plan listings (DB-driven, no hardcoded plans)
 * - Validate and apply discount coupons
 * - Create and manage recurring subscriptions via Razorpay Subscriptions API
 * - Handle Razorpay webhook events for auto-renewal and cancellation
 * - Maintain full payment audit trail
 *
 * Runs on port 8089.
 * Verifies JWTs signed by auth-service (never signs tokens itself).
 *
 * @EnableScheduling — enables the nightly subscription expiry job.
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logger.info("Payment Service is READY on port 8089");
    }
}
