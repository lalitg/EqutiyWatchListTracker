package com.equity.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity mapping to the coupon_usages table.
 *
 * Records every coupon redemption. Used to enforce:
 *   - max_redemptions: total uses across all users
 *   - max_redemptions_per_user: how many times one user can reuse a coupon
 *
 * A row is inserted ONLY after payment.status = PAID is confirmed in
 * SubscriptionService.verifyAndActivate(). If the user abandons Razorpay
 * checkout without paying, no coupon_usages row is created.
 */
@Entity
@Table(name = "coupon_usages")
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    /** Logical reference to users.id in user-service (no FK). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The payment record that redeemed this coupon. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "used_at", nullable = false, updatable = false)
    private LocalDateTime usedAt;

    @PrePersist
    void prePersist() {
        usedAt = LocalDateTime.now();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public LocalDateTime getUsedAt() { return usedAt; }
}
