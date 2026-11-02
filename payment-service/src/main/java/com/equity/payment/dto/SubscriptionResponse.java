package com.equity.payment.dto;

import java.time.LocalDateTime;

/**
 * Response for GET /api/v1/subscriptions/me, POST /verify, POST /cancel.
 */
public class SubscriptionResponse {

    private String        planName;
    private String        status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private boolean       cancelAtPeriodEnd;
    private int           renewalAmountPaise;

    public SubscriptionResponse(String planName, String status,
                                 LocalDateTime currentPeriodStart, LocalDateTime currentPeriodEnd,
                                 boolean cancelAtPeriodEnd, int renewalAmountPaise) {
        this.planName           = planName;
        this.status             = status;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd   = currentPeriodEnd;
        this.cancelAtPeriodEnd  = cancelAtPeriodEnd;
        this.renewalAmountPaise = renewalAmountPaise;
    }

    public String getPlanName() { return planName; }
    public String getStatus() { return status; }
    public LocalDateTime getCurrentPeriodStart() { return currentPeriodStart; }
    public LocalDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public int getRenewalAmountPaise() { return renewalAmountPaise; }
}
