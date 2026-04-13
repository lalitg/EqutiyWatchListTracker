package com.equity.payment.controller;

import com.equity.payment.dto.CreateSubscriptionRequest;
import com.equity.payment.dto.CreateSubscriptionResponse;
import com.equity.payment.dto.SubscriptionResponse;
import com.equity.payment.dto.VerifySubscriptionRequest;
import com.equity.payment.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * All subscription endpoints. All require a valid JWT.
 *
 * POST /api/v1/subscriptions/create  — Step 1: create Razorpay subscription
 * POST /api/v1/subscriptions/verify  — Step 2: verify first payment, activate
 * POST /api/v1/subscriptions/cancel  — Cancel at end of period
 * GET  /api/v1/subscriptions/me      — Current subscription status
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * POST /api/v1/subscriptions/create
     *
     * Step 1 of the payment flow.
     * Creates a Razorpay subscription and returns the subscriptionId for
     * the frontend to open the Razorpay checkout popup.
     *
     * Request: { "planId": 1, "couponCode": "LAUNCH50" }
     * Coupon is optional. If omitted or blank, full price is charged.
     */
    @PostMapping("/create")
    public ResponseEntity<CreateSubscriptionResponse> create(
            @Valid @RequestBody CreateSubscriptionRequest request) {

        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        CreateSubscriptionResponse response = subscriptionService.createSubscription(
                userId, request.getPlanId(), request.getCouponCode());

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/subscriptions/verify
     *
     * Step 2 of the payment flow.
     * Called after user completes payment in Razorpay popup.
     * Verifies the HMAC-SHA256 signature, updates subscription to ACTIVE.
     *
     * Request: { "razorpayPaymentId": "pay_XX", "razorpaySubscriptionId": "sub_YY",
     *            "razorpaySignature": "abc123" }
     */
    @PostMapping("/verify")
    public ResponseEntity<SubscriptionResponse> verify(
            @Valid @RequestBody VerifySubscriptionRequest request) {

        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        SubscriptionResponse response = subscriptionService.verifyAndActivate(
                userId,
                request.getRazorpayPaymentId(),
                request.getRazorpaySubscriptionId(),
                request.getRazorpaySignature());

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/subscriptions/cancel
     *
     * Cancels the subscription at the end of the current billing period.
     * Calls Razorpay cancel API to stop future charges.
     * User retains access until currentPeriodEnd.
     */
    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionResponse> cancel() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SubscriptionResponse response = subscriptionService.cancelSubscription(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/subscriptions/me
     *
     * Returns the current user's subscription status, plan name, and period dates.
     */
    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponse> getMySubscription() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(subscriptionService.getSubscription(userId));
    }
}
