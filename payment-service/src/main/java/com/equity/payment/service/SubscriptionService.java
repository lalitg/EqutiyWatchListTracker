package com.equity.payment.service;

import com.equity.payment.dto.CouponValidationResult;
import com.equity.payment.dto.CreateSubscriptionResponse;
import com.equity.payment.dto.SubscriptionResponse;
import com.equity.payment.entity.Payment;
import com.equity.payment.entity.Plan;
import com.equity.payment.entity.Subscription;
import com.equity.payment.enums.BillingCycle;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
// Explicit bean name: watchlist-service has its own SubscriptionService (news alerts) and both
// run in the same context inside main-api-service. Spring would name both "subscriptionService"
// and refuse to start. Injection is by type, so nothing else changes.
@Service("billingSubscriptionService")
@Transactional
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    /**
     * How many years of billing cycles a new mandate authorises.
     *
     * Razorpay will not accept an open-ended subscription, so this stands in for "until they
     * cancel": ten years of monthly charges is longer than any card on file survives, and
     * Razorpay stops at the count rather than charging past it.
     */
    private final int mandateYears;

    /**
     * How long an unpaid purchase stays reusable. Long enough to cover a reader who opened the
     * window, went to find their card and came back; short enough that a stale price or plan is
     * never quietly re-opened.
     */
    private final int attemptReuseMinutes;

    /**
     * How long after its period ends a subscription is left alone before being marked EXPIRED.
     * Razorpay retries a failed renewal over several days, and a webhook can arrive after a
     * restart, so expiring on the stroke of midnight would cut off customers who did pay.
     */
    private final int expiryGraceDays;

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository      paymentRepository;
    private final PlanService            planService;
    private final CouponService          couponService;
    private final RazorpayClientWrapper  razorpayClient;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PaymentRepository paymentRepository,
                               PlanService planService,
                               CouponService couponService,
                               RazorpayClientWrapper razorpayClient,
                               @Value("${razorpay.subscription.mandate-years:10}") int mandateYears,
                               @Value("${subscription.attempt.reuse-minutes:15}") int attemptReuseMinutes,
                               @Value("${subscription.expiry.grace-days:3}") int expiryGraceDays) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository      = paymentRepository;
        this.planService            = planService;
        this.couponService          = couponService;
        this.razorpayClient         = razorpayClient;
        this.mandateYears           = mandateYears;
        this.attemptReuseMinutes    = attemptReuseMinutes;
        this.expiryGraceDays        = expiryGraceDays;
    }

    /**
     * Step 1 of the subscription flow — called by POST /api/v1/subscriptions/create.
     *
     * <h2>What this deliberately does not do</h2>
     * It does not touch the user's existing subscription row. Starting a purchase is not the same
     * as completing one: a reader who opens the payment window and closes it must still have
     * exactly what they had a moment earlier. The previous version wrote the row to PENDING with
     * null period dates before the window even opened, which silently downgraded a paying customer
     * to the free tier the instant they looked at another plan.
     *
     * <p>The new Razorpay subscription id therefore lives on the Payment row — one row per attempt
     * — until a payment is actually confirmed. {@link #activate} is the only place a plan is
     * granted.
     *
     * <h2>Abandoned attempts</h2>
     * Each attempt creates a subscription at Razorpay. An attempt that is never paid for still
     * exists there, so any earlier unfinished attempt by this user is cancelled before a new one
     * is made: at most one live unauthenticated mandate per user, rather than one per click.
     *
     * <h2>Why this method holds no transaction</h2>
     * Two reasons, both practical. It calls Razorpay over the network, and a database transaction
     * held open across an external HTTP call is a connection hostage to somebody else's latency.
     * And when two requests race, the database refuses the second open purchase — inside a
     * transaction that refusal poisons the Hibernate session, so the recovery (read the winner's
     * attempt and return it) cannot run: Hibernate answers "don't flush the Session after an
     * exception occurs" and the caller gets a 500 instead of their checkout window. Each write
     * below therefore stands on its own, which is also the order they need to happen in.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateSubscriptionResponse createSubscription(Long userId, Long planId, String couponCode) {
        Plan plan = planService.getPlanById(planId);

        if (plan.getRazorpayPlanId() == null) {
            throw new SubscriptionException(
                    "This plan is not yet configured for payments. Please try again later.");
        }

        // Coupons are validated and recorded here but Razorpay charges the plan price for a
        // subscription: a discount has to be attached as a Razorpay Offer, which we do not create
        // yet. Rather than show one amount and charge another, the request is refused.
        if (couponCode != null && !couponCode.isBlank()) {
            throw new SubscriptionException(
                    "Discount codes cannot be applied to a subscription yet. "
                    + "Please subscribe without a code.");
        }

        // Refuse to sell somebody the plan they are already on - but only while it is genuinely
        // running. A row can sit at ACTIVE with a period that ended days ago (a renewal that never
        // arrived, a nightly job that has not caught it yet), and reading the status alone would
        // lock a returning customer out of buying the very plan they want back.
        Subscription existing = subscriptionRepository.findByUserId(userId).orElse(null);
        if (existing != null
                && existing.getStatus() == SubscriptionStatus.ACTIVE
                && existing.getPlanId().equals(planId)
                && !existing.isCancelAtPeriodEnd()
                && existing.getCurrentPeriodEnd() != null
                && existing.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            throw new SubscriptionException("You are already subscribed to this plan.");
        }

        // A purchase of this same plan that is already open and still fresh is reused rather than
        // replaced. Two tabs, a retry after a timeout or a double click that beats the button
        // being disabled then reopen the same checkout window instead of leaving a second mandate
        // behind at Razorpay.
        Payment open = openAttempt(userId);
        if (open != null
                && open.getPlanId().equals(planId)
                && open.getRazorpaySubscriptionId() != null
                && open.getCreatedAt() != null
                && open.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(attemptReuseMinutes))) {

            logger.info("Reusing the open purchase for userId={}, planId={}, rzpSubId={}",
                        userId, planId, open.getRazorpaySubscriptionId());
            return responseFor(open.getRazorpaySubscriptionId(), plan);
        }

        cancelUnfinishedAttempts(userId);

        // The cycle count is derived from the plan rather than hardcoded, so a monthly plan
        // authorises twelve charges a year and an annual one charges once.
        int cyclesPerYear = plan.getBillingCycle() == BillingCycle.ANNUAL ? 1 : 12;

        String rzpSubId;
        try {
            rzpSubId = razorpayClient.createSubscription(
                    plan.getRazorpayPlanId(), cyclesPerYear * mandateYears);
        } catch (RuntimeException e) {
            // Razorpay being unreachable, rate-limiting us, or rejecting the plan is not an
            // "unexpected error" to the person waiting on a payment window — it is something they
            // can act on by trying again. SubscriptionException is used because both this service
            // and the combined main-api answer it the same way, so the reader sees one message
            // wherever the service happens to be running.
            logger.error("Razorpay refused to start a subscription for userId={}, planId={}: {}",
                         userId, planId, e.getMessage());
            throw new SubscriptionException(
                    "We could not reach the payment provider just now. Please try again in a moment.");
        }

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setPlanId(planId);
        payment.setCouponId(null);
        payment.setPaymentType(PaymentType.FIRST_PAYMENT);
        payment.setOriginalAmountPaise(plan.getPricePaise());
        payment.setDiscountPaise(0);
        payment.setFinalAmountPaise(plan.getPricePaise());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setRazorpaySubscriptionId(rzpSubId);

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Two requests arrived together and the database kept its promise that a user has at
            // most one open purchase. This one lost: its mandate is cancelled so it cannot be
            // paid by mistake, and the winner's checkout is returned instead. Both callers end up
            // opening the same window, which is what the reader expected from one click anyway.
            logger.info("Concurrent purchase for userId={} — using the one that won the race", userId);
            safeCancel(rzpSubId);

            Payment winner = openAttempt(userId);
            if (winner == null || winner.getRazorpaySubscriptionId() == null) {
                throw new SubscriptionException(
                        "Could not start the payment. Please try again in a moment.");
            }
            return responseFor(winner.getRazorpaySubscriptionId(), planService.getPlanById(winner.getPlanId()));
        }

        logger.info("Purchase started: userId={}, planId={}, rzpSubId={}, amount={}",
                userId, planId, rzpSubId, plan.getPricePaise());

        return responseFor(rzpSubId, plan);
    }

    /** The one purchase this user currently has open, or null. */
    private Payment openAttempt(Long userId) {
        return paymentRepository
                .findFirstByUserIdAndStatusAndPaymentTypeOrderByCreatedAtDesc(
                        userId, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT)
                .orElse(null);
    }

    /** What the checkout window needs. Renewals are always at the full plan price. */
    private CreateSubscriptionResponse responseFor(String rzpSubId, Plan plan) {
        return new CreateSubscriptionResponse(
                rzpSubId,
                razorpayClient.getKeyId(),
                plan.getPricePaise(),
                0,
                plan.getPricePaise(),
                plan.getPricePaise()
        );
    }

    /** Cancels a mandate without letting the attempt fail because the tidy-up did. */
    private void safeCancel(String rzpSubId) {
        try {
            razorpayClient.cancelSubscription(rzpSubId, true);
        } catch (Exception e) {
            logger.warn("Could not cancel mandate {}: {}", rzpSubId, e.getMessage());
        }
    }

    /**
     * Cancels any subscription this user started at Razorpay but never paid for.
     *
     * <p>Best effort on purpose: a tidy-up failure must not stop someone buying. Each attempt is
     * marked FAILED locally either way, so the row stops looking like a payment that is still
     * expected.
     */
    private void cancelUnfinishedAttempts(Long userId) {
        List<Payment> unfinished = paymentRepository.findByUserIdAndStatusAndPaymentType(
                userId, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT);

        for (Payment attempt : unfinished) {
            String rzpSubId = attempt.getRazorpaySubscriptionId();
            if (rzpSubId != null) {
                safeCancel(rzpSubId);
            }
            attempt.setStatus(PaymentStatus.FAILED);
            attempt.setFailureReason("Abandoned — a newer attempt was started");
            // Flushed, not merely saved: Hibernate orders inserts before updates within a flush,
            // so a plain save would let the new attempt hit the "one open purchase" index while
            // this row still said CREATED, and a legitimate plan switch would fail.
            paymentRepository.saveAndFlush(attempt);
        }
    }

    /**
     * Step 2 of the subscription flow — called by POST /api/v1/subscriptions/verify once the
     * reader has paid inside the Razorpay window.
     *
     * <p>The signature is the whole security of this call: the three values the browser sends mean
     * nothing until the server recomputes the HMAC with its own key secret. Anything that fails
     * that check is a 400 and nothing is marked as paid.
     *
     * <p>Beyond the signature, the attempt is found by the Razorpay subscription it was created
     * for, and must belong to the caller. Looking it up by "this user's most recent payment", as
     * this once did, both picked the wrong row when a renewal landed in between and let one
     * user's confirmation settle against another user's subscription.
     */
    public SubscriptionResponse verifyAndActivate(Long userId,
                                                   String razorpayPaymentId,
                                                   String razorpaySubscriptionId,
                                                   String razorpaySignature) {

        boolean valid = razorpayClient.verifyPaymentSignature(
                razorpayPaymentId, razorpaySubscriptionId, razorpaySignature);
        if (!valid) {
            throw new PaymentException("Payment signature verification failed. Payment not confirmed.");
        }

        Payment payment = paymentRepository
                .findFirstByRazorpaySubscriptionIdAndPaymentTypeOrderByCreatedAtDesc(
                        razorpaySubscriptionId, PaymentType.FIRST_PAYMENT)
                .orElseThrow(() -> new SubscriptionException(
                        "No purchase found for this payment. Nothing has been charged twice — "
                        + "contact support with payment id " + razorpayPaymentId + "."));

        // A confirmation may only settle the caller's own attempt.
        if (!payment.getUserId().equals(userId)) {
            logger.warn("Rejected confirmation: userId={} tried to confirm a payment belonging to userId={}",
                        userId, payment.getUserId());
            throw new PaymentException("This payment does not belong to your account.");
        }

        // The browser and the webhook race each other, and a reader can submit twice. Whichever
        // arrives second finds the attempt already settled and returns the same answer rather
        // than an error about a payment that in fact succeeded.
        if (payment.getStatus() == PaymentStatus.PAID) {
            Subscription already = subscriptionRepository.findByUserId(payment.getUserId())
                    .orElseThrow(() -> new SubscriptionException("Subscription not found"));
            return buildSubscriptionResponse(already, payment.getPlanId());
        }

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);

        return activate(payment, razorpaySubscriptionId);
    }

    /**
     * Grants the plan an attempt was paid for. The one place a subscription becomes ACTIVE.
     *
     * <p>Shared by the browser confirmation and by the webhook that covers the reader who closed
     * their browser before it could be sent. Both arrive at the same state, which is what stops
     * the two paths drifting into different definitions of "paid".
     *
     * <h2>Switching plans</h2>
     * If the row already pointed at a different live Razorpay subscription, that one is cancelled
     * here — at the moment the replacement is actually paid for, not before. Leaving it alone, as
     * this code used to, left two live mandates charging the same customer every month with only
     * the newer id recorded, so the older one could not even be cancelled from our side.
     */
    private SubscriptionResponse activate(Payment payment, String razorpaySubscriptionId) {
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        if (payment.getCouponId() != null) {
            couponService.consumeCoupon(payment.getCouponId(), payment.getUserId(), payment.getId());
        }

        Subscription subscription = subscriptionRepository.findByUserId(payment.getUserId())
                .orElseGet(Subscription::new);

        String previous = subscription.getRazorpaySubscriptionId();
        if (previous != null && !previous.equals(razorpaySubscriptionId)) {
            try {
                razorpayClient.cancelSubscription(previous, true);
                logger.info("Replaced subscription {} with {} for userId={}",
                            previous, razorpaySubscriptionId, payment.getUserId());
            } catch (Exception e) {
                // The customer has paid; refusing to activate now would be the worse failure.
                // Logged loudly because it means a mandate may still be live at Razorpay.
                logger.error("PAYMENT ATTENTION: could not cancel the previous subscription {} for "
                             + "userId={} — it may still charge. Cancel it in the Razorpay dashboard: {}",
                             previous, payment.getUserId(), e.getMessage());
            }
        }

        Plan plan = planService.getPlanById(payment.getPlanId());
        LocalDateTime now = LocalDateTime.now();

        subscription.setUserId(payment.getUserId());
        subscription.setPlanId(payment.getPlanId());
        subscription.setRazorpaySubscriptionId(razorpaySubscriptionId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusDays(plan.getDurationDays()));
        subscriptionRepository.save(subscription);

        logger.info("Subscription activated: userId={}, rzpSubId={}, periodEnd={}",
                payment.getUserId(), razorpaySubscriptionId, subscription.getCurrentPeriodEnd());

        return buildSubscriptionResponse(subscription, plan.getId());
    }

    /**
     * The same activation, reached from the {@code subscription.authenticated} webhook.
     *
     * <p>That webhook is the safety net for the reader whose browser closed, or whose connection
     * dropped, between paying and confirming: Razorpay tells us the mandate was authenticated and
     * we grant the plan they paid for. The webhook's own signature has already been verified by
     * the controller, so there is no second signature to check here.
     *
     * @param razorpaySubscriptionId the subscription Razorpay has authenticated
     * @return true when this call granted the plan, false when there was nothing to do
     */
    public boolean activateFromWebhook(String razorpaySubscriptionId) {
        Payment payment = paymentRepository
                .findFirstByRazorpaySubscriptionIdAndPaymentTypeOrderByCreatedAtDesc(
                        razorpaySubscriptionId, PaymentType.FIRST_PAYMENT)
                .orElse(null);

        if (payment == null) {
            logger.warn("Webhook activation: no purchase found for rzpSubId={}", razorpaySubscriptionId);
            return false;
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            logger.debug("Webhook activation: already settled for rzpSubId={}", razorpaySubscriptionId);
            return false;
        }

        activate(payment, razorpaySubscriptionId);
        return true;
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

        // Tell Razorpay to stop future charges — best effort, and deliberately not fatal.
        //
        // Razorpay refuses this call in several ordinary situations: a mandate that was never
        // activated ("no billing cycle is going on"), one already cancelled, or Razorpay simply
        // being unreachable. Letting that become a 500 meant the customer could not cancel at
        // all and nothing was recorded — which is how a cancellation turns into a chargeback.
        // The intent is recorded locally either way; if the remote call did fail, the log says so
        // loudly enough to finish the job from the dashboard.
        try {
            razorpayClient.cancelSubscription(subscription.getRazorpaySubscriptionId());
        } catch (Exception e) {
            logger.error("PAYMENT ATTENTION: Razorpay refused to cancel {} for userId={} — the "
                         + "cancellation is recorded here, but check the dashboard in case the "
                         + "mandate is still live: {}",
                         subscription.getRazorpaySubscriptionId(), userId, e.getMessage());
        }

        // Recorded regardless — the nightly job sets CANCELLED once the paid period ends.
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

        // Subscriptions that simply ran out: still ACTIVE, never cancelled, and past their period
        // by more than the grace window. A renewal that is merely late is left alone — Razorpay
        // retries, and a webhook can arrive after a restart — but one that never came means the
        // customer stopped paying, and a row that says otherwise quietly inflates every count of
        // subscribers taken from this table.
        LocalDateTime cutoff = now.minusDays(expiryGraceDays);
        List<Subscription> lapsed = subscriptionRepository.findLapsedActive(cutoff);
        for (Subscription sub : lapsed) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            // Warn, not info: a handful of these in one run is the clearest signal there is that
            // renewal webhooks have stopped arriving.
            logger.warn("Subscription EXPIRED (no renewal arrived): userId={}, rzpSubId={}, periodEnded={}",
                        sub.getUserId(), sub.getRazorpaySubscriptionId(), sub.getCurrentPeriodEnd());
        }

        if (!toCancel.isEmpty() || !toExpire.isEmpty() || !lapsed.isEmpty()) {
            logger.info("Nightly job: {} cancelled, {} expired after a failed renewal, {} lapsed",
                        toCancel.size(), toExpire.size(), lapsed.size());
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
