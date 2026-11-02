package com.equity.payment.enums;

public enum PaymentStatus {
    /** Razorpay subscription created, awaiting first payment from user. */
    CREATED,
    /** Payment confirmed — via /verify endpoint or subscription.charged webhook. */
    PAID,
    /** Payment failed or Razorpay returned an error. */
    FAILED,
    /** Payment was refunded after successful charge. */
    REFUNDED
}
