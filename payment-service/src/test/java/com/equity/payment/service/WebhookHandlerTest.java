package com.equity.payment.service;

import com.equity.payment.entity.Plan;
import com.equity.payment.entity.Subscription;
import com.equity.payment.enums.SubscriptionStatus;
import com.equity.payment.repository.PaymentRepository;
import com.equity.payment.repository.SubscriptionRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * What the renewal webhook is allowed to change about a cancellation.
 *
 * <p>The interesting case is not the ordinary renewal — it is the charge that arrives after the
 * customer has already cancelled. Razorpay is told to stop at cycle end, but a cancellation made
 * minutes before a renewal fires can lose that race, and the charge lands anyway. Whatever this
 * handler does then is what the customer sees when they check their statement, so it is worth
 * pinning down rather than leaving to a comment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookHandlerTest {

    private static final String RZP_SUB = "sub_RENEW";
    private static final String RZP_PAY = "pay_RENEW";
    private static final Long   USER    = 7L;

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository      paymentRepository;
    @Mock private PlanService            planService;
    @Mock private SubscriptionService    subscriptionService;

    private WebhookHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WebhookHandler(subscriptionRepository, paymentRepository,
                                     planService, subscriptionService);

        Plan plan = new Plan();
        plan.setDurationDays(30);
        when(planService.getPlanById(3L)).thenReturn(plan);
        when(paymentRepository.existsByRazorpayPaymentId(RZP_PAY)).thenReturn(false);
    }

    /** The shape Razorpay sends for subscription.charged, trimmed to what the handler reads. */
    private static JSONObject chargedPayload() {
        return new JSONObject()
                .put("subscription", new JSONObject()
                        .put("entity", new JSONObject().put("id", RZP_SUB)))
                .put("payment", new JSONObject()
                        .put("entity", new JSONObject().put("id", RZP_PAY).put("amount", 14900)));
    }

    private Subscription subscription(LocalDateTime periodEnd, boolean cancelAtPeriodEnd) {
        Subscription sub = new Subscription();
        sub.setUserId(USER);
        sub.setPlanId(3L);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodEnd(periodEnd);
        sub.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        sub.setRazorpaySubscriptionId(RZP_SUB);
        return sub;
    }

    @Nested
    @DisplayName("A renewal charge")
    class Charged {

        @Test
        @DisplayName("extends the period and leaves a running plan running")
        void ordinaryRenewal() {
            LocalDateTime end = LocalDateTime.now().plusDays(1);
            Subscription sub = subscription(end, false);
            when(subscriptionRepository.findByRazorpaySubscriptionId(RZP_SUB))
                    .thenReturn(Optional.of(sub));

            handler.handle("subscription.charged", chargedPayload());

            assertEquals(end.plusDays(30), sub.getCurrentPeriodEnd());
            assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
            assertFalse(sub.isCancelAtPeriodEnd());
        }

        @Test
        @DisplayName("does not quietly un-cancel a subscription the customer already cancelled")
        void doesNotResurrectACancellation() {
            // The race: cancelled late in the cycle, charged minutes later. Clearing the flag here
            // would take the money and erase the evidence that they ever asked to stop.
            Subscription sub = subscription(LocalDateTime.now().plusMinutes(2), true);
            when(subscriptionRepository.findByRazorpaySubscriptionId(RZP_SUB))
                    .thenReturn(Optional.of(sub));

            handler.handle("subscription.charged", chargedPayload());

            assertTrue(sub.isCancelAtPeriodEnd(),
                       "the cancellation must survive a charge that beat it");
        }

        @Test
        @DisplayName("still grants the time that was charged for")
        void stillExtendsWhatWasPaidFor() {
            // They were charged, so they get the month - the cancellation governs the renewal
            // after it, not the period they have just paid for.
            LocalDateTime end = LocalDateTime.now().plusMinutes(2);
            Subscription sub = subscription(end, true);
            when(subscriptionRepository.findByRazorpaySubscriptionId(RZP_SUB))
                    .thenReturn(Optional.of(sub));

            handler.handle("subscription.charged", chargedPayload());

            assertEquals(end.plusDays(30), sub.getCurrentPeriodEnd());
        }
    }

    @Nested
    @DisplayName("Razorpay confirming a cancellation")
    class Cancelled {

        @Test
        @DisplayName("records a cancellation made outside the app")
        void recordsADashboardCancellation() {
            Subscription sub = subscription(LocalDateTime.now().plusDays(10), false);
            when(subscriptionRepository.findByRazorpaySubscriptionId(RZP_SUB))
                    .thenReturn(Optional.of(sub));

            handler.handle("subscription.cancelled", new JSONObject()
                    .put("subscription", new JSONObject()
                            .put("entity", new JSONObject().put("id", RZP_SUB))));

            assertTrue(sub.isCancelAtPeriodEnd());
            assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus(),
                         "access runs to the period end; the nightly job ends it");
        }
    }
}
