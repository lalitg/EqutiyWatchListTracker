package com.equity.payment.entity;

import com.equity.payment.enums.BillingCycle;
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
 * JPA entity mapping to the plans table.
 *
 * Plans are DB-driven — no plan name, price, or duration is hardcoded in Java.
 * The marketing team manages plans directly in the DB or via a future admin API.
 *
 * razorpay_plan_id: when a plan row is inserted, PlanService.seedPlanToRazorpay()
 * calls the Razorpay Plans API to create a matching plan there and stores its ID here.
 * This ID is required to create Razorpay Subscriptions linked to this plan.
 */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Price in paise. Rs.499 = 49900. Never use floating-point for money. */
    @Column(name = "price_paise", nullable = false)
    private int pricePaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 10)
    private BillingCycle billingCycle;

    /** How many days this plan covers: 30 for MONTHLY, 365 for ANNUAL. */
    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    /**
     * Razorpay Plan ID (e.g. "plan_XXXXXX").
     * Filled at startup by PlanService.seedPlanToRazorpay() if null.
     * Required to create Razorpay Subscriptions.
     */
    @Column(name = "razorpay_plan_id", length = 100)
    private String razorpayPlanId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPricePaise() { return pricePaise; }
    public void setPricePaise(int pricePaise) { this.pricePaise = pricePaise; }
    public BillingCycle getBillingCycle() { return billingCycle; }
    public void setBillingCycle(BillingCycle billingCycle) { this.billingCycle = billingCycle; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
    public String getRazorpayPlanId() { return razorpayPlanId; }
    public void setRazorpayPlanId(String razorpayPlanId) { this.razorpayPlanId = razorpayPlanId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
