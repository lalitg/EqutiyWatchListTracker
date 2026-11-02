package com.equity.payment.exception;

/** Thrown for invalid subscription operations (no active sub to cancel, etc.). → HTTP 400 */
public class SubscriptionException extends RuntimeException {
    public SubscriptionException(String message) { super(message); }
}
