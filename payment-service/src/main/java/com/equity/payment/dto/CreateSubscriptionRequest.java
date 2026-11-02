package com.equity.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateSubscriptionRequest {

    @NotNull(message = "Plan ID is required")
    private Long planId;

    /** Optional. If provided, discount is applied to first payment only. */
    private String couponCode;

    /**
     * The policy version shown beside the tick box the buyer accepted.
     *
     * <p>Required. A tick box enforced only in the browser could be skipped by calling this
     * endpoint directly, and would leave nothing on record afterwards.
     */
    @NotBlank(message = "Please accept the terms before subscribing")
    private String acceptedPolicyVersion;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getAcceptedPolicyVersion() { return acceptedPolicyVersion; }
    public void setAcceptedPolicyVersion(String v) { this.acceptedPolicyVersion = v; }
}
