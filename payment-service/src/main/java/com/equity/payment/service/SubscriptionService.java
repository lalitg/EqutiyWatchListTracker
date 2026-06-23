package com.equity.payment.service;

import com.equity.payment.dto.CouponValidationResult;
import com.equity.payment.dto.CreateSubscriptionResponse;
import com.equity.payment.dto.SubscriptionResponse;
import com.equity.payment.entity.Payment;
import com.equity.payment.entity.Plan;
import com.equity.payment.entity.Subscription;
import com.equity.payment.enums.PaymentStatus;
import com.equity.payment.enums.PaymentType;
import com.equity.payment.enums.SubscriptionStatus;
import com.equity.payment.exception.PaymentException;
import com.equity.payment.exception.SubscriptionException;
import com.equity.payment.razorpay.RazorpayClientWrapper;
import com.equity.payment.repository.PaymentRepository;
import com.equity.payment.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates the full subscription lifecycle:
 *   createSubscription → verifyAndActivate → cancelSubscription
 *
 * Also runs the nightly @Scheduled job to expire ended subscriptions.
 *
 * Integration with Razorpay Subscriptions API:
 *   createSubscription()   → calls Razorpay to create recurring subscription
 *   cancelSubscription()   → calls Razorpay to stop future charges
 *   verifyAndActivate()    → verifies HMAC-SHA256 signature, activates in DB
 *
 * WebhookHandler handles auto-renewal (subscription.charged event).
 * This service handles user-initiated actions only.
 */
@Service
@Transactional
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository      paymentRepository;
    private final PlanService            planService;
    private final CouponService          couponService;
    private final RazorpayClientWrapper  razorpayClient;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PaymentRepository paymentRepository,
                               PlanService planService,
                               CouponService couponService,
                               RazorpayClientWrapper razorpayClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository      = paymentRepository;
        this.planService            = planService;
        this.couponService          = couponService;
        this.razorpayClient         = razorpayClient;
    }

    /**
     * Step 1 of the subscription flow.
     *
     * Called by POST /api/v1/subscriptions/create.
     *
     * Steps:
     *   1. Fetch plan from DB (validates planId)
     *   2. If couponCode provided, validate it — get discount amounts
     *   3. Verify plan has a razorpay_plan_id (set at startup by PlanService)
     *   4. Call Razorpay to create a subscription → get razorpay_subscription_id
     *   5. Upsert Subscription row: status=PENDING
     *   6. Create Payment row: status=CREATED, FIRST_PAYMENT type
     *   7. Return response with razorpaySubscriptionId and amounts for frontend
     *
     * NOTE: Coupon is NOT consumed here. It is consumed only after payment
     * is confirmed in verifyAndActivate().
     */
    public CreateSubscriptionResponse createSubscription(Long userId, Long planId, String couponCode) {
        // Step 1 — fetch and validate plan
        Plan plan = planService.getPlanById(planId);

        if (plan.getRazorpayPlanId() == null) {
            throw new SubscriptionException(
                    "This plan is not yet configured for payments. Please try again later.");
        }

        // Step 2 — validate coupon if provided
        int discountPaise = 0;
        int finalPaise    = plan.getPricePaise();
        Long couponId     = null;

        if (couponCode != null && !couponCode.isBlank()) {
            CouponValidationResult couponResult =
                    couponService.validateCoupon(couponCode, planId, userId, plan.getPricePaise());
            discountPaise = couponResult.discountPaise();
            finalPaise    = couponResult.finalAmountPaise();
            couponId      = couponResult.couponId();
        }

        // Step 3 — create Razorpay subscription
        String rzpSubId = razorpayClient.createSubscription(plan.getRazorpayPlanId());

        // Step 4 — upsert subscription row (PENDING until first payment verified)
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElse(new Subscription());
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setCurrentPeriodStart(null);
        subscription.setCurrentPeriodEnd(null);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setRazorpaySubscriptionId(rzpSubId);
        subscriptionRepository.save(subscription);

        // Step 5 — create Payment row
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setPlanId(planId);
        payment.setCouponId(couponId);
        payment.setPaymentType(PaymentType.FIRST_PAYMENT);
        payment.setOriginalAmountPaise(plan.getPricePaise());
        payment.setDiscountPaise(discountPaise);
        payment.setFinalAmountPaise(finalPaise);
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);

        logger.info("Subscription created: userId={}, rzpSubId={}, finalAmount={}",
                userId, rzpSubId, finalPaise);

        return new CreateSubscriptionResponse(
                rzpSubId,
                razorpayClient.getKeyId(),
                plan.getPricePaise(),
                discountPaise,
                finalPaise,
                plan.getPricePaise()   // renewal is always full price
        );
    }

    /**
     * Step 2 of the subscription flow.
     *
     * Called by POST /api/v1/subscriptions/verify after user completes payment
     * in the Razorpay checkout popup.
     *
     * Steps:
     *   1. Verify HMAC-SHA256 signature (security check — rejects tampered payments)
     *   2. Find the Payment row by userId + FIRST_PAYMENT + CREATED status
     *   3. Update Payment: status=PAID, razorpayPaymentId filled
     *   4. Consume coupon if one was applied
     *   5. Activate Subscription: status=ACTIVE, set period dates
     *
     * Idempotent: if Payment is already PAID (duplicate /verify call), skip and return.
     */
    public SubscriptionResponse verifyAndActivate(Long userId,
                                                   String razorpayPaymentId,
                                                   String razorpaySubscriptionId,
                                                   String razorpaySignature) {
        // Step 1 — verify HMAC-SHA256 signature
        boolean valid = razorpayClient.verifyPaymentSignature(
                razorpayPaymentId, razorpaySubscriptionId, razorpaySignature);
        if (!valid) {
            throw new PaymentException("Payment signature verification failed. Payment not confirmed.");
        }

        // Step 2 — find subscription
        Subscription subscription = subscriptionRepository
                .findByRazorpaySubscriptionId(razorpaySubscriptionId)
                .orElseThrow(() -> new SubscriptionException("Subscription not found"));

        // Step 3 — find the pending payment for this user
        // Use paymentRepository to find CREATED payment for this user
        Payment payment = paymentRepository
                .findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.Pageable.ofSize(1))
                .stream()
                .filter(p -> p.getStatus() == PaymentStatus.CREATED
                          && p.getPaymentType() == PaymentType.FIRST_PAYMENT)
                .findFirst()
                .orElseThrow(() -> new SubscriptionException("No pending payment found for this user"));

        // Idempotency check
        if (payment.getStatus() == PaymentStatus.PAID) {
            return buildSubscriptionResponse(subscription, payment.getPlanId());
        }

        // Step 4 — update payment to PAID
        payment.setStatus(PaymentStatus.PAID);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        paymentRepository.save(payment);

        // Step 5 — consume coupon (ONLY after payment confirmed)
        if (payment.getCouponId() != null) {
            couponService.consumeCoupon(payment.getCouponId(), userId, payment.getId());
        }

        // Step 6 — activate subscription with period dates
        Plan plan = planService.getPlanById(subscription.getPlanId());
        LocalDateTime now = LocalDateTime.now();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusDays(plan.getDurationDays()));
        subscriptionRepository.save(subscription);

        logger.info("Subscription activated: userId={}, rzpSubId={}, periodEnd={}",
                userId, razorpaySubscriptionId, subscription.getCurrentPeriodEnd());

        return buildSubscriptionResponse(subscription, plan.getId());
    }

    /**
     * Cancels a subscription.
     * Called by POST /api/v1/subscriptions/cancel.
     *
     * Steps:
     *   1. Find the user's active subscription
     *   2. Call Razorpay cancel API — stops future auto-charges
     *   3. Set cancel_at_period_end=true in DB
     *   4. User retains access until currentPeriodEnd
     *   5. Nightly job sets status=CANCELLED after period ends
     */
    public SubscriptionResponse cancelSubscription(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriptionException("No active subscription found"));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new SubscriptionException("Only ACTIVE subscriptions can be cancelled. Current status: "
                    + subscription.getStatus());
        }

        if (subscription.isCancelAtPeriodEnd()) {
            throw new SubscriptionException("Subscription is already scheduled for cancellation");
        }

        // Call Razorpay to stop future charges
        razorpayClient.cancelSubscription(subscription.getRazorpaySubscriptionId());

        // Mark in DB — nightly job will set CANCELLED after period ends
        subscription.setCancelAtPeriodEnd(true);
        subscriptionRepository.save(subscription);

        logger.info("Subscription cancellation scheduled: userId={}, accessUntil={}",
                userId, subscription.getCurrentPeriodEnd());

        return buildSubscriptionResponse(subscription, subscription.getPlanId());
    }

    /**
     * Returns the current subscription for a user.
     * Called by GET /api/v1/subscriptions/me.
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriptionException("No subscription found for this user"));
        return buildSubscriptionResponse(subscription, subscription.getPlanId());
    }

    /**
     * Nightly job — runs at midnight via @Scheduled.
     *
     * Two tasks:
     *   1. Set status=CANCELLED for subscriptions where cancel_at_period_end=true and period ended.
     *   2. Set status=EXPIRED for HALTED subscriptions whose period has also ended.
     *
     * After this job runs, these users' JWTs will show no active plan on next /refresh.
     */
    @Scheduled(cron = "${subscription.expiry.cron}")
    public void expireAndCancelSubscriptions() {
        LocalDateTime now = LocalDateTime.now();

        // Cancel subscriptions that reached their end date
        List<Subscription> toCancel = subscriptionRepository.findExpiredCancellations(now);
        for (Subscription sub : toCancel) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
            logger.info("Subscription CANCELLED (period ended): userId={}", sub.getUserId());
        }

        // Expire HALTED subscriptions whose period also ended
        List<Subscription> toExpire = subscriptionRepository.findExpiredHalted(now);
        for (Subscription sub : toExpire) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            logger.info("Subscription EXPIRED (halted + period ended): userId={}", sub.getUserId());
        }

        if (!toCancel.isEmpty() || !toExpire.isEmpty()) {
            logger.info("Nightly job: {} cancelled, {} expired", toCancel.size(), toExpire.size());
        }
    }

    private SubscriptionResponse buildSubscriptionResponse(Subscription sub, Long planId) {
        Plan plan = planService.getPlanById(planId);
        return new SubscriptionResponse(
                plan.getDisplayName(),
                sub.getStatus().name(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.isCancelAtPeriodEnd(),
                plan.getPricePaise()
        );
    }
}
