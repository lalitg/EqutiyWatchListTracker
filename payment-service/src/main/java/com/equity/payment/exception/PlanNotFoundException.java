package com.equity.payment.exception;

/** Thrown when a planId does not exist or is inactive. → HTTP 404 */
public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(String message) { super(message); }
}
