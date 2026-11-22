package com.equity.payment.service;

import com.equity.payment.entity.Payment;
import com.equity.payment.entity.Subscription;
import com.equity.payment.enums.PaymentStatus;
import com.equity.payment.enums.PaymentType;
import com.equity.payment.enums.SubscriptionStatus;
import com.equity.payment.repository.PaymentRepository;
import com.equity.payment.repository.SubscriptionRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles all Razorpay webhook events.
 *
 * All handlers are IDEMPOTENT — safe to call multiple times with the same event.
 * Razorpay may deliver the same webhook more than once. We handle this by
 * checking if the razorpay_payment_id already exists in the payments table
 * before inserting a new row.
 *
 * Events handled:
 *   subscription.authenticated  → first payment mandate confirmed (backup of /verify)
 *   subscription.charged        → KEY AUTO-RENEWAL EVENT — extends subscription by 30 days
 *   subscription.halted         → all Razorpay retries failed — mark as HALTED
 *   subscription.cancelled      → Razorpay confirms cancellation
 */
@Service
@Transactional
public class WebhookHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebhookHandler.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository      paymentRepository;
    private final PlanService            planService;
    private final SubscriptionService    subscriptionService;

    public WebhookHandler(SubscriptionRepository subscriptionRepository,
                          PaymentRepository paymentRepository,
                          PlanService planService,
                          SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository      = paymentRepository;
        this.planService            = planService;
        this.subscriptionService    = subscriptionService;
    }

    /**
     * Routes incoming webhook events to the appropriate handler.
     * Called by WebhookController after signature is verified.
     */
    public void handle(String event, JSONObject payload) {
        logger.info("Webhook received: {}", event);
        switch (event) {
            case "subscription.authenticated" -> handleAuthenticated(payload);
            case "subscription.charged"       -> handleCharged(payload);
            case "subscription.halted"        -> handleHalted(payload);
            case "subscription.cancelled"     -> handleCancelled(payload);
            default -> logger.debug("Unhandled webhook event: {}", event);
        }
    }

    /**
     * subscription.authenticated
     *
     * Razorpay fires this when the user successfully sets up their payment mandate
     * (card saved, UPI mandate created). This is a backup for the /verify endpoint.
     *
     * If /verify was already called and subscription is ACTIVE, this is a no-op.
     * If /verify was missed (network timeout, browser closed), this activates it.
     */
    private void handleAuthenticated(JSONObject payload) {
        try {
            String rzpSubId = payload
                    .getJSONObject("subscription")
                    .getJSONObject("entity")
                    .getString("id");

            // Delegated rather than done here: SubscriptionService.activate is the single place
            // that knows how to grant a plan, and it sets the period dates. This handler used to
            // flip the status to ACTIVE on its own and leave currentPeriodEnd null, which gave the
            // reader a subscription with no end date — access for ever in the eyes of the
            // entitlement check, and a guaranteed null pointer at the first renewal.
            boolean granted = subscriptionService.activateFromWebhook(rzpSubId);

            if (granted) {
                logger.info("subscription.authenticated: granted the plan for rzpSubId={}", rzpSubId);
            } else {
                logger.debug("subscription.authenticated: nothing to do for rzpSubId={}", rzpSubId);
            }

        } catch (Exception e) {
            logger.error("Error handling subscription.authenticated: {}", e.getMessage(), e);
        }
    }

    /**
     * subscription.charged — THE KEY AUTO-RENEWAL HANDLER.
     *
     * Razorpay fires this every month when it automatically charges the user.
     * This is the event that drives auto-renewal — no scheduler needed.
     *
     * Steps:
     *   1. Idempotency check — skip if this razorpayPaymentId already processed
     *   2. Find subscription by Razorpay subscription ID
     *   3. Insert a new RENEWAL Payment row (status=PAID, no coupon, full price)
     *   4. Extend subscription period: start = old end, end = old end + duration_days
     *   5. Ensure status = ACTIVE (may have been HALTED before successful retry)
     */
    private void handleCharged(JSONObject payload) {
        try {
            JSONObject subEntity = payload
                    .getJSONObject("subscription")
                    .getJSONObject("entity");
            JSONObject payEntity = payload
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String rzpSubId = subEntity.getString("id");
            String rzpPayId = payEntity.getString("id");
            int    amount   = payEntity.getInt("amount");   // in paise

            // Step 1 — idempotency check
            if (paymentRepository.existsByRazorpayPaymentId(rzpPayId)) {
                logger.debug("subscription.charged: duplicate webhook, already processed. rzpPayId={}", rzpPayId);
                return;
            }

            // Step 2 — find subscription
            Subscription sub = subscriptionRepository
                    .findByRazorpaySubscriptionId(rzpSubId)
                    .orElse(null);

            if (sub == null) {
                logger.error("subscription.charged: subscription not found for rzpSubId={}", rzpSubId);
                return;
            }

            // Step 3 — insert RENEWAL payment row
            Payment renewal = new Payment();
            renewal.setUserId(sub.getUserId());
            renewal.setPlanId(sub.getPlanId());
            renewal.setCouponId(null);                      // renewals never have coupons
            renewal.setPaymentType(PaymentType.RENEWAL);
            renewal.setOriginalAmountPaise(amount);
            renewal.setDiscountPaise(0);
            renewal.setFinalAmountPaise(amount);
            renewal.setStatus(PaymentStatus.PAID);
            renewal.setRazorpayPaymentId(rzpPayId);
            paymentRepository.save(renewal);

            // Step 4 — extend subscription period.
            //
            // The new period runs from the end of the old one, so a renewal paid a day early does
            // not cost the customer a day. Two cases cannot use that end date: it may be null
            // (the first charge for a subscription activated by webhook), and it may be in the
            // past (a lapse, or a retry that finally succeeded). Both start from now instead.
            // Reading it straight, as this did, threw a NullPointerException that the catch below
            // swallowed — leaving a customer charged, a PAID row written, and no access granted.
            var plan = planService.getPlanById(sub.getPlanId());
            var now = java.time.LocalDateTime.now();
            var oldEnd = sub.getCurrentPeriodEnd();
            var base = (oldEnd == null || oldEnd.isBefore(now)) ? now : oldEnd;

            sub.setCurrentPeriodStart(base);
            sub.setCurrentPeriodEnd(base.plusDays(plan.getDurationDays()));
            sub.setStatus(SubscriptionStatus.ACTIVE);       // re-activate if it was HALTED

            // A charge usually means the subscription was not cancelled - but not always, and the
            // exception is the one that costs money. Cancel at 23:58 on the last day of a cycle
            // and Razorpay may already have the renewal in flight; the charge then lands here a
            // few minutes later. Clearing the flag at that point would extend the plan, take the
            // payment, and erase every trace that the customer ever asked to stop - which is a
            // chargeback with nothing on our side to answer it.
            //
            // So the intent survives the charge. The period is still extended, because they were
            // charged and must get what they paid for, and the cancellation still stands, because
            // they asked for it first. Support gets told, since this is the one case here that
            // wants a human and probably a goodwill refund.
            if (sub.isCancelAtPeriodEnd()) {
                logger.error("PAYMENT ATTENTION: userId={} was charged {} paise on rzpPayId={} "
                             + "after cancelling - the mandate is cancelled at Razorpay but this "
                             + "charge arrived anyway. Access now runs to {}; consider refunding.",
                             sub.getUserId(), amount, rzpPayId, sub.getCurrentPeriodEnd());
            } else {
                sub.setCancelAtPeriodEnd(false);
            }

            subscriptionRepository.save(sub);

            logger.info("AUTO-RENEWAL: userId={}, rzpPayId={}, newPeriodEnd={}",
                    sub.getUserId(), rzpPayId, sub.getCurrentPeriodEnd());

        } catch (Exception e) {
            logger.error("Error handling subscription.charged: {}", e.getMessage(), e);
        }
    }

    /**
     * subscription.halted
     *
     * Razorpay fires this when all automatic retries have failed.
     * e.g. user's card expired, insufficient balance after 3 retry attempts.
     *
     * Mark subscription as HALTED. The nightly job will set it to EXPIRED
     * once the current period ends. User retains access until period ends.
     */
    private void handleHalted(JSONObject payload) {
        try {
            String rzpSubId = payload
                    .getJSONObject("subscription")
                    .getJSONObject("entity")
                    .getString("id");

            Subscription sub = subscriptionRepository
                    .findByRazorpaySubscriptionId(rzpSubId)
                    .orElse(null);

            if (sub == null) {
                logger.warn("subscription.halted: subscription not found for rzpSubId={}", rzpSubId);
                return;
            }

            // Insert failed RENEWAL payment for audit trail
            Payment failedPayment = new Payment();
            failedPayment.setUserId(sub.getUserId());
            failedPayment.setPlanId(sub.getPlanId());
            failedPayment.setPaymentType(PaymentType.RENEWAL);
            failedPayment.setOriginalAmountPaise(planService.getPlanById(sub.getPlanId()).getPricePaise());
            failedPayment.setDiscountPaise(0);
            failedPayment.setFinalAmountPaise(planService.getPlanById(sub.getPlanId()).getPricePaise());
            failedPayment.setStatus(PaymentStatus.FAILED);
            failedPayment.setFailureReason("All Razorpay retries exhausted");
            paymentRepository.save(failedPayment);

            sub.setStatus(SubscriptionStatus.HALTED);
            subscriptionRepository.save(sub);

            logger.warn("Subscription HALTED (all retries failed): userId={}", sub.getUserId());

        } catch (Exception e) {
            logger.error("Error handling subscription.halted: {}", e.getMessage(), e);
        }
    }

    /**
     * subscription.cancelled
     *
     * Razorpay confirms the subscription has been cancelled on their end.
     * This is a backup for the cancel flow — sets cancel_at_period_end=true
     * if not already set. The nightly job will set CANCELLED after period ends.
     */
    private void handleCancelled(JSONObject payload) {
        try {
            String rzpSubId = payload
                    .getJSONObject("subscription")
                    .getJSONObject("entity")
                    .getString("id");

            Subscription sub = subscriptionRepository
                    .findByRazorpaySubscriptionId(rzpSubId)
                    .orElse(null);

            if (sub == null) {
                logger.warn("subscription.cancelled: subscription not found for rzpSubId={}", rzpSubId);
                return;
            }

            if (!sub.isCancelAtPeriodEnd()) {
                sub.setCancelAtPeriodEnd(true);
                subscriptionRepository.save(sub);
            }

            logger.info("subscription.cancelled confirmed by Razorpay: userId={}", sub.getUserId());

        } catch (Exception e) {
            logger.error("Error handling subscription.cancelled: {}", e.getMessage(), e);
        }
    }
}
