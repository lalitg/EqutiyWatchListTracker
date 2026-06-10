package com.equity.payment.dto;

import jakarta.validation.constraints.NotNull;

public class CreateSubscriptionRequest {

    @NotNull(message = "Plan ID is required")
    private Long planId;

    /** Optional. If provided, discount is applied to first payment only. */
    private String couponCode;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
}
