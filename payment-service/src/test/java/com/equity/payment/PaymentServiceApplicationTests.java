package com.equity.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/watchlisttracker",
    "auth.jwt.secret=test-secret-at-least-32-characters-long",
    "razorpay.key.id=rzp_test_placeholder",
    "razorpay.key.secret=placeholder",
    "razorpay.webhook.secret=placeholder"
})
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
