package com.equity.payment.controller;

import com.equity.payment.dto.CouponValidateRequest;
import com.equity.payment.dto.CouponValidateResponse;
import com.equity.payment.dto.CouponValidationResult;
import com.equity.payment.entity.Coupon;
import com.equity.payment.entity.Plan;
import com.equity.payment.repository.CouponRepository;
import com.equity.payment.service.CouponService;
import com.equity.payment.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/coupons/validate
 *
 * JWT required — we need userId to check per-user usage limits.
 *
 * Validates a coupon against all 7 rules and returns a preview
 * of the discounted price. The coupon is NOT consumed at this step.
 *
 * Frontend uses this response to show the user their savings
 * before they click "Pay". If invalid, shows the error message.
 */
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService    couponService;
    private final PlanService      planService;
    private final CouponRepository couponRepository;

    public CouponController(CouponService couponService,
                             PlanService planService,
                             CouponRepository couponRepository) {
        this.couponService    = couponService;
        this.planService      = planService;
        this.couponRepository = couponRepository;
    }

    @PostMapping("/validate")
    public ResponseEntity<CouponValidateResponse> validate(@Valid @RequestBody CouponValidateRequest request) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Plan plan = planService.getPlanById(request.getPlanId());

        CouponValidationResult result = couponService.validateCoupon(
                request.getCode(), request.getPlanId(), userId, plan.getPricePaise());

        Coupon coupon = couponRepository.findByCodeAndActiveTrue(request.getCode().toUpperCase())
                .orElseThrow();

        CouponValidateResponse response = new CouponValidateResponse(
                true,
                request.getCode().toUpperCase(),
                coupon.getDiscountType().name(),
                plan.getPricePaise(),
                result.discountPaise(),
                result.finalAmountPaise()
        );
        return ResponseEntity.ok(response);
    }
}
