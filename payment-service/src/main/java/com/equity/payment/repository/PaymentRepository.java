package com.equity.payment.repository;

import com.equity.payment.entity.Payment;
import com.equity.payment.enums.PaymentStatus;
import com.equity.payment.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

    /**
     * The attempt a confirmation belongs to, newest first.
     *
     * Looked up by the Razorpay subscription rather than by user, so a confirmation can only ever
     * settle the attempt it was actually signed for — and so a retry of the same confirmation
     * finds the same row and can see that it is already paid.
     */
    Optional<Payment> findFirstByRazorpaySubscriptionIdAndPaymentTypeOrderByCreatedAtDesc(
            String razorpaySubscriptionId, PaymentType paymentType);

    /** Unfinished attempts for a user — the ones left behind when checkout is abandoned. */
    List<Payment> findByUserIdAndStatusAndPaymentType(Long userId, PaymentStatus status, PaymentType paymentType);

    /**
     * The one purchase this user currently has open, if any.
     *
     * <p>A database index guarantees there is at most one: see the partial unique index on
     * payments in the schema script. Two requests arriving together cannot both create an
     * attempt, and the one that loses the race reads the winner's with this.
     */
    Optional<Payment> findFirstByUserIdAndStatusAndPaymentTypeOrderByCreatedAtDesc(
            Long userId, PaymentStatus status, PaymentType paymentType);
}
