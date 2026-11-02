package com.equity.payment.dto;

/**
 * Internal result from CouponService.validateCoupon().
 * Carries the coupon ID and calculated discount amounts.
 * Used by SubscriptionService — not returned directly to the client.
 */
public record CouponValidationResult(Long couponId, int discountPaise, int finalAmountPaise) {}
