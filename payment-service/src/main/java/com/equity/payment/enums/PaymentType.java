package com.equity.payment.enums;

public enum PaymentType {
    /** First payment when subscribing. Coupon discount may apply. */
    FIRST_PAYMENT,
    /** Monthly auto-renewal charged by Razorpay. Always full plan price — no coupon. */
    RENEWAL
}
