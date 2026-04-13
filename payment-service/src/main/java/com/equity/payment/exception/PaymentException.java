package com.equity.payment.exception;

/** Thrown for payment verification failures (invalid signature, etc.). → HTTP 400 */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) { super(message); }
}
