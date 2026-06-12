package com.equity.payment.exception;

/** Thrown when coupon validation fails for any of the 7 rules. → HTTP 400 */
public class CouponException extends RuntimeException {
    public CouponException(String message) { super(message); }
}
