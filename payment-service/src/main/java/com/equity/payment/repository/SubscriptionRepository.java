package com.equity.payment.repository;

import com.equity.payment.entity.Subscription;
import com.equity.payment.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    /** Used by WebhookHandler to match incoming Razorpay webhook events. */
    Optional<Subscription> findByRazorpaySubscriptionId(String razorpaySubscriptionId);

    /**
     * Nightly expiry job: find subscriptions that should be cancelled/expired.
     * cancel_at_period_end=true AND period has ended.
     */
    @Query("SELECT s FROM Subscription s WHERE s.cancelAtPeriodEnd = true " +
           "AND s.currentPeriodEnd < :now " +
           "AND s.status = com.equity.payment.enums.SubscriptionStatus.ACTIVE")
    List<Subscription> findExpiredCancellations(LocalDateTime now);

    /**
     * Find HALTED subscriptions whose period has also ended.
     * These should be set to EXPIRED to revoke access.
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = com.equity.payment.enums.SubscriptionStatus.HALTED " +
           "AND s.currentPeriodEnd < :now")
    List<Subscription> findExpiredHalted(LocalDateTime now);
}
