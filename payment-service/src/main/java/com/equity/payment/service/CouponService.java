package com.equity.payment.service;

import com.equity.payment.dto.CouponValidationResult;
import com.equity.payment.entity.Coupon;
import com.equity.payment.entity.CouponUsage;
import com.equity.payment.enums.DiscountType;
import com.equity.payment.exception.CouponException;
import com.equity.payment.repository.CouponRepository;
import com.equity.payment.repository.CouponUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * All coupon validation and consumption logic.
 *
 * Key rule: coupons apply ONLY to the first payment.
 * Auto-renewal payments (RENEWAL type) always charge full price.
 * This is enforced by calling validateCoupon() only from
 * SubscriptionService.createSubscription() — never from the webhook handler.
 *
 * Coupon consumption (coupon_usages insert) happens ONLY after
 * payment is confirmed (status=PAID) in verifyAndActivate().
 * If user abandons Razorpay checkout, no usage is recorded.
 */
@Service
@Transactional
public class CouponService {

    private static final Logger logger = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    public CouponService(CouponRepository couponRepository,
                         CouponUsageRepository couponUsageRepository) {
        this.couponRepository      = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
    }

    /**
     * Validates a coupon against all 7 rules and returns the discount calculation.
     * Does NOT consume the coupon — only validates and previews the discount.
     *
     * Called by:
     *   POST /api/v1/coupons/validate   (preview — no payment yet)
     *   SubscriptionService.createSubscription()  (re-validate before creating order)
     *
     * @param code        the coupon code the user typed
     * @param planId      the plan the user is subscribing to
     * @param userId      the authenticated user
     * @param planPrice   the plan's price in paise (fetched from DB by caller)
     * @return CouponValidationResult with discount amounts
     * @throws CouponException if any validation rule fails
     */
    @Transactional(readOnly = true)
    public CouponValidationResult validateCoupon(String code, Long planId, Long userId, int planPrice) {

        // Rule 1 — Coupon must exist and be active
        Coupon coupon = couponRepository.findByCodeAndActiveTrue(code.toUpperCase())
                .orElseThrow(() -> new CouponException("Coupon not found or inactive: " + code));

        // Rule 2 — Not expired (valid_from ≤ now ≤ valid_until)
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new CouponException("Coupon is not yet valid");
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            throw new CouponException("Coupon has expired");
        }

        // Rule 3 — Plan restriction (applicable_plan_id = null means all plans)
        if (coupon.getApplicablePlanId() != null && !coupon.getApplicablePlanId().equals(planId)) {
            throw new CouponException("Coupon is not valid for this plan");
        }

        // Rule 4 — Minimum order amount
        if (planPrice < coupon.getMinOrderPaise()) {
            throw new CouponException("Minimum order amount not met for this coupon");
        }

        // Rule 5 — Total redemption limit
        if (coupon.getMaxRedemptions() != null) {
            long totalUsages = couponUsageRepository.countByCouponId(coupon.getId());
            if (totalUsages >= coupon.getMaxRedemptions()) {
                throw new CouponException("Coupon has reached its usage limit");
            }
        }

        // Rule 6 — Per-user redemption limit
        long userUsages = couponUsageRepository.countByCouponIdAndUserId(coupon.getId(), userId);
        if (userUsages >= coupon.getMaxRedemptionsPerUser()) {
            throw new CouponException("You have already used this coupon");
        }

        // All rules passed — calculate discount
        int discountPaise = calculateDiscount(coupon, planPrice);
        int finalPaise    = Math.max(0, planPrice - discountPaise);

        logger.info("Coupon validated: code={}, userId={}, discount={}p, final={}p",
                code.toUpperCase(), userId, discountPaise, finalPaise);

        return new CouponValidationResult(coupon.getId(), discountPaise, finalPaise);
    }

    /**
     * Calculates the discount amount in paise.
     *
     * FLAT:       discount = discountValue (already in paise)
     * PERCENTAGE: discount = planPrice * discountValue / 100
     *             capped at maxDiscountPaise if set
     */
    int calculateDiscount(Coupon coupon, int planPricePaise) {
        if (coupon.getDiscountType() == DiscountType.FLAT) {
            return coupon.getDiscountValue();
        }
        // PERCENTAGE
        int discount = (planPricePaise * coupon.getDiscountValue()) / 100;
        if (coupon.getMaxDiscountPaise() != null) {
            discount = Math.min(discount, coupon.getMaxDiscountPaise());
        }
        return discount;
    }

    /**
     * Records a coupon redemption.
     * Called ONLY after payment.status = PAID is confirmed in verifyAndActivate().
     *
     * @param couponId  the validated coupon
     * @param userId    the user who redeemed it
     * @param paymentId the payment that triggered this redemption
     */
    public void consumeCoupon(Long couponId, Long userId, Long paymentId) {
        CouponUsage usage = new CouponUsage();
        usage.setCouponId(couponId);
        usage.setUserId(userId);
        usage.setPaymentId(paymentId);
        couponUsageRepository.save(usage);
        logger.info("Coupon consumed: couponId={}, userId={}, paymentId={}", couponId, userId, paymentId);
    }
}
