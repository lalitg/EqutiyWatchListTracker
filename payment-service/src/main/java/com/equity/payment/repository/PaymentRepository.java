package com.equity.payment.repository;

import com.equity.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Idempotency check in WebhookHandler.
     * If this razorpayPaymentId is already in our DB, the webhook is a duplicate.
     */
    boolean existsByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    /** Paginated payment history for GET /api/v1/payments/history. */
    Page<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
