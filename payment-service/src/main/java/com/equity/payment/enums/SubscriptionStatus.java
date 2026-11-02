package com.equity.payment.enums;

public enum SubscriptionStatus {
    /** Razorpay subscription created, first payment not yet completed. */
    PENDING,
    /** Subscription is live — user has full access. */
    ACTIVE,
    /** Razorpay failed to charge after all automatic retries. */
    HALTED,
    /** User cancelled — access continues until current_period_end. */
    CANCELLED,
    /** Period ended — access revoked. */
    EXPIRED
}
