package com.equity.payment.entity;

import com.equity.payment.enums.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity mapping to the coupons table.
 *
 * Coupons are created by the marketing team (directly in DB or future admin API).
 * They apply ONLY to the first payment — renewals are always full price.
 *
 * Discount calculation:
 *   FLAT:       finalAmount = planPrice - discountValue
 *   PERCENTAGE: discount    = planPrice * discountValue / 100
 *               capped at maxDiscountPaise if set
 *               finalAmount = planPrice - discount
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The code the user types: LAUNCH50, SAVE100 */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    /**
     * For FLAT: amount in paise (10000 = Rs.100).
     * For PERCENTAGE: 0-100 (50 = 50% off).
     */
    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    /**
     * For PERCENTAGE discounts: maximum discount in paise.
     * e.g. 50% off but max Rs.200 → maxDiscountPaise = 20000.
     * null means no cap.
     */
    @Column(name = "max_discount_paise")
    private Integer maxDiscountPaise;

    /** Minimum plan price required to use this coupon. Default 0. */
    @Column(name = "min_order_paise", nullable = false)
    private int minOrderPaise = 0;

    /**
     * Restrict to a specific plan ID.
     * null = valid on all plans.
     */
    @Column(name = "applicable_plan_id")
    private Long applicablePlanId;

    /**
     * Total redemption limit across all users.
     * null = unlimited.
     */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    /** How many times one user can use this coupon. Default 1. */
    @Column(name = "max_redemptions_per_user", nullable = false)
    private int maxRedemptionsPerUser = 1;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    /** null = never expires */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (validFrom == null) validFrom = LocalDateTime.now();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }
    public int getDiscountValue() { return discountValue; }
    public void setDiscountValue(int discountValue) { this.discountValue = discountValue; }
    public Integer getMaxDiscountPaise() { return maxDiscountPaise; }
    public void setMaxDiscountPaise(Integer maxDiscountPaise) { this.maxDiscountPaise = maxDiscountPaise; }
    public int getMinOrderPaise() { return minOrderPaise; }
    public void setMinOrderPaise(int minOrderPaise) { this.minOrderPaise = minOrderPaise; }
    public Long getApplicablePlanId() { return applicablePlanId; }
    public void setApplicablePlanId(Long applicablePlanId) { this.applicablePlanId = applicablePlanId; }
    public Integer getMaxRedemptions() { return maxRedemptions; }
    public void setMaxRedemptions(Integer maxRedemptions) { this.maxRedemptions = maxRedemptions; }
    public int getMaxRedemptionsPerUser() { return maxRedemptionsPerUser; }
    public void setMaxRedemptionsPerUser(int maxRedemptionsPerUser) { this.maxRedemptionsPerUser = maxRedemptionsPerUser; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
