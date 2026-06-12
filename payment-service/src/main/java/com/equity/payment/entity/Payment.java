package com.equity.payment.entity;

import com.equity.payment.enums.PaymentStatus;
import com.equity.payment.enums.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity mapping to the payments table.
 *
 * Every payment — first-time and auto-renewal — has a row here.
 * This is the full audit trail.
 *
 * Stores three amount fields separately for transparency:
 *   originalAmountPaise: plan's full price
 *   discountPaise:       discount applied (0 for renewals)
 *   finalAmountPaise:    amount actually charged
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logical reference to users.id in user-service (no DB FK). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** null if no coupon was applied (always null for RENEWAL). */
    @Column(name = "coupon_id")
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 15)
    private PaymentType paymentType;

    @Column(name = "original_amount_paise", nullable = false)
    private int originalAmountPaise;

    @Column(name = "discount_paise", nullable = false)
    private int discountPaise = 0;

    @Column(name = "final_amount_paise", nullable = false)
    private int finalAmountPaise;

    @Column(nullable = false, length = 5)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentStatus status;

    /** Filled after Razorpay processes the payment. */
    @Column(name = "razorpay_payment_id", unique = true, length = 100)
    private String razorpayPaymentId;

    /** HMAC signature received from Razorpay — stored for audit. */
    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    /** Filled on failed payments — reason from Razorpay. */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }
    public PaymentType getPaymentType() { return paymentType; }
    public void setPaymentType(PaymentType paymentType) { this.paymentType = paymentType; }
    public int getOriginalAmountPaise() { return originalAmountPaise; }
    public void setOriginalAmountPaise(int originalAmountPaise) { this.originalAmountPaise = originalAmountPaise; }
    public int getDiscountPaise() { return discountPaise; }
    public void setDiscountPaise(int discountPaise) { this.discountPaise = discountPaise; }
    public int getFinalAmountPaise() { return finalAmountPaise; }
    public void setFinalAmountPaise(int finalAmountPaise) { this.finalAmountPaise = finalAmountPaise; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public String getRazorpaySignature() { return razorpaySignature; }
    public void setRazorpaySignature(String razorpaySignature) { this.razorpaySignature = razorpaySignature; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
