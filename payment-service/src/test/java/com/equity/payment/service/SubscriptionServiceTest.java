package com.equity.payment.service;

import com.equity.payment.dto.CreateSubscriptionResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules that decide whether somebody is charged, charged twice, or charged and given nothing.
 *
 * <p>Every test here stands for a defect that was live in this service: a purchase that destroyed
 * the plan already paid for, a plan switch that left two mandates charging, a confirmation that
 * could settle against another user's subscription, a retry that told a paying customer their
 * payment had failed, and a discount promised but never applied.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    private static final Long USER = 42L;
    private static final Long OTHER_USER = 99L;
    private static final Long PLAN_ID = 2L;
    private static final String RZP_PLAN = "plan_ADVANCED";
    private static final String RZP_SUB = "sub_NEW";
    private static final String RZP_PAY = "pay_123";
    private static final String SIGNATURE = "a-valid-looking-signature";

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository      paymentRepository;
    @Mock private PlanService            planService;
    @Mock private CouponService          couponService;
    @Mock private RazorpayClientWrapper  razorpayClient;

    private SubscriptionService service;

    private Plan plan;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, paymentRepository, planService,
                                          couponService, razorpayClient, 10, 15, 3);

        plan = new Plan();
        plan.setName("ADVANCED_MONTHLY");
        plan.setDisplayName("Advanced");
        plan.setPricePaise(29900);
        plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setDurationDays(30);
        plan.setRazorpayPlanId(RZP_PLAN);

        // any() rather than PLAN_ID: the response builder looks the plan up again by the entity's
        // own id, which a unit-test Plan never has.
        when(planService.getPlanById(any())).thenReturn(plan);
        when(razorpayClient.createSubscription(anyString(), anyInt())).thenReturn(RZP_SUB);
        when(razorpayClient.getKeyId()).thenReturn("rzp_test_key");
        when(paymentRepository.findByUserIdAndStatusAndPaymentType(any(), any(), any()))
                .thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findFirstByUserIdAndStatusAndPaymentTypeOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Subscription activeSubscription(String rzpSubId, LocalDateTime end) {
        Subscription sub = new Subscription();
        sub.setUserId(USER);
        sub.setPlanId(1L);                       // on some other plan
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(end.minusDays(30));
        sub.setCurrentPeriodEnd(end);
        sub.setRazorpaySubscriptionId(rzpSubId);
        return sub;
    }

    private Payment attempt(Long userId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setPlanId(PLAN_ID);
        payment.setPaymentType(PaymentType.FIRST_PAYMENT);
        payment.setStatus(status);
        payment.setOriginalAmountPaise(29900);
        payment.setFinalAmountPaise(29900);
        payment.setRazorpaySubscriptionId(RZP_SUB);
        return payment;
    }

    private void givenAttemptExists(Payment payment) {
        when(paymentRepository.findFirstByRazorpaySubscriptionIdAndPaymentTypeOrderByCreatedAtDesc(
                RZP_SUB, PaymentType.FIRST_PAYMENT)).thenReturn(Optional.of(payment));
    }

    // ─── starting a purchase ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Starting a purchase")
    class Create {

        @Test
        @DisplayName("leaves a paid-up subscription exactly as it was")
        void doesNotTouchTheExistingSubscription() {
            // The defect: this wrote the row to PENDING with null dates before the payment window
            // even opened, so closing that window downgraded a paying customer to free.
            LocalDateTime end = LocalDateTime.now().plusDays(20);
            Subscription existing = activeSubscription("sub_OLD", end);
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.of(existing));

            service.createSubscription(USER, PLAN_ID, null);

            verify(subscriptionRepository, never()).save(any(Subscription.class));
            assertEquals(SubscriptionStatus.ACTIVE, existing.getStatus());
            assertEquals(end, existing.getCurrentPeriodEnd());
            assertEquals("sub_OLD", existing.getRazorpaySubscriptionId());
        }

        @Test
        @DisplayName("records the Razorpay subscription on the attempt, so the confirmation can find it")
        void stampsTheAttempt() {
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            CreateSubscriptionResponse response = service.createSubscription(USER, PLAN_ID, null);

            // saveAndFlush, because the insert has to reach the database before the next statement:
            // see the "one open purchase" index.
            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(saved.capture());
            assertEquals(RZP_SUB, saved.getValue().getRazorpaySubscriptionId());
            assertEquals(PaymentStatus.CREATED, saved.getValue().getStatus());
            assertEquals(RZP_SUB, response.getRazorpaySubscriptionId());
            assertEquals(29900, response.getFirstPaymentAmountPaise());
        }

        @Test
        @DisplayName("cancels an abandoned attempt rather than leaving a second mandate alive")
        void cancelsAbandonedAttempts() {
            Payment abandoned = attempt(USER, PaymentStatus.CREATED);
            abandoned.setRazorpaySubscriptionId("sub_ABANDONED");
            when(paymentRepository.findByUserIdAndStatusAndPaymentType(
                    USER, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT)).thenReturn(List.of(abandoned));
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            service.createSubscription(USER, PLAN_ID, null);

            verify(razorpayClient).cancelSubscription("sub_ABANDONED", true);
            assertEquals(PaymentStatus.FAILED, abandoned.getStatus());
        }

        @Test
        @DisplayName("refuses a discount code instead of promising one it cannot apply")
        void refusesCoupons() {
            // Razorpay charges the plan price for a subscription; a discount needs an Offer, which
            // we do not create. Showing a reduced price and charging full would be worse.
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            assertThrows(SubscriptionException.class,
                         () -> service.createSubscription(USER, PLAN_ID, "LAUNCH50"));
            verify(razorpayClient, never()).createSubscription(anyString(), anyInt());
        }

        @Test
        @DisplayName("refuses to sell the plan the user is already on")
        void refusesDuplicatePurchase() {
            Subscription existing = activeSubscription("sub_OLD", LocalDateTime.now().plusDays(10));
            existing.setPlanId(PLAN_ID);
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.of(existing));

            assertThrows(SubscriptionException.class,
                         () -> service.createSubscription(USER, PLAN_ID, null));
        }

        @Test
        @DisplayName("lets a lapsed customer buy the same plan again")
        void allowsRepurchaseWhenThePeriodHasEnded() {
            // A row can sit at ACTIVE with a period that ended days ago - a renewal that never
            // arrived. Reading the status alone would refuse the customer who wants to come back.
            Subscription stale = activeSubscription("sub_OLD", LocalDateTime.now().minusDays(3));
            stale.setPlanId(PLAN_ID);
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.of(stale));

            assertNotNull(service.createSubscription(USER, PLAN_ID, null));
        }

        @Test
        @DisplayName("authorises twelve cycles a year for a monthly plan")
        void authorisesEnoughCycles() {
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            service.createSubscription(USER, PLAN_ID, null);

            // Razorpay refuses total_count = 0; ten years of monthly charges stands in for
            // "until they cancel".
            verify(razorpayClient).createSubscription(RZP_PLAN, 120);
        }
    }

    // ─── confirming a payment ────────────────────────────────────────────────

    @Nested
    @DisplayName("Confirming a payment")
    class Verify {

        @BeforeEach
        void signatureIsGood() {
            when(razorpayClient.verifyPaymentSignature(RZP_PAY, RZP_SUB, SIGNATURE)).thenReturn(true);
        }

        @Test
        @DisplayName("grants the plan and dates the period")
        void activates() {
            givenAttemptExists(attempt(USER, PaymentStatus.CREATED));
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            service.verifyAndActivate(USER, RZP_PAY, RZP_SUB, SIGNATURE);

            ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(saved.capture());
            Subscription sub = saved.getValue();
            assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
            assertEquals(RZP_SUB, sub.getRazorpaySubscriptionId());
            assertNotNull(sub.getCurrentPeriodStart(), "a subscription with no start cannot expire");
            assertNotNull(sub.getCurrentPeriodEnd(), "a subscription with no end would never expire");
            assertTrue(sub.getCurrentPeriodEnd().isAfter(LocalDateTime.now().plusDays(29)));
        }

        @Test
        @DisplayName("stops the mandate being replaced, so a switch does not charge twice")
        void cancelsThePreviousMandate() {
            givenAttemptExists(attempt(USER, PaymentStatus.CREATED));
            when(subscriptionRepository.findByUserId(USER))
                    .thenReturn(Optional.of(activeSubscription("sub_OLD", LocalDateTime.now().plusDays(12))));

            service.verifyAndActivate(USER, RZP_PAY, RZP_SUB, SIGNATURE);

            verify(razorpayClient).cancelSubscription("sub_OLD", true);
        }

        @Test
        @DisplayName("still grants the plan when the old mandate cannot be cancelled")
        void activatesEvenIfTheOldCancelFails() {
            // The customer has paid. Refusing to activate because our tidy-up failed would be the
            // worse of the two failures.
            givenAttemptExists(attempt(USER, PaymentStatus.CREATED));
            when(subscriptionRepository.findByUserId(USER))
                    .thenReturn(Optional.of(activeSubscription("sub_OLD", LocalDateTime.now().plusDays(12))));
            org.mockito.Mockito.doThrow(new RuntimeException("razorpay down"))
                    .when(razorpayClient).cancelSubscription(eq("sub_OLD"), anyBoolean());

            service.verifyAndActivate(USER, RZP_PAY, RZP_SUB, SIGNATURE);

            ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(saved.capture());
            assertEquals(SubscriptionStatus.ACTIVE, saved.getValue().getStatus());
        }

        @Test
        @DisplayName("a repeated confirmation returns the same answer instead of an error")
        void isIdempotent() {
            // The defect: the lookup filtered for CREATED and then asked whether the row was PAID,
            // which could never be true, so a retry told a paying customer "no pending payment".
            Payment settled = attempt(USER, PaymentStatus.PAID);
            givenAttemptExists(settled);
            when(subscriptionRepository.findByUserId(USER))
                    .thenReturn(Optional.of(activeSubscription(RZP_SUB, LocalDateTime.now().plusDays(30))));

            assertNotNull(service.verifyAndActivate(USER, RZP_PAY, RZP_SUB, SIGNATURE));

            verify(subscriptionRepository, never()).save(any(Subscription.class));
        }

        @Test
        @DisplayName("refuses a confirmation for somebody else's purchase")
        void refusesAnotherUsersPayment() {
            givenAttemptExists(attempt(OTHER_USER, PaymentStatus.CREATED));

            assertThrows(PaymentException.class,
                         () -> service.verifyAndActivate(USER, RZP_PAY, RZP_SUB, SIGNATURE));
            verify(subscriptionRepository, never()).save(any(Subscription.class));
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("a bad signature changes nothing")
        void refusesABadSignature() {
            when(razorpayClient.verifyPaymentSignature(RZP_PAY, RZP_SUB, "forged")).thenReturn(false);

            assertThrows(PaymentException.class,
                         () -> service.verifyAndActivate(USER, RZP_PAY, RZP_SUB, "forged"));
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(subscriptionRepository, never()).save(any(Subscription.class));
        }

        @Test
        @DisplayName("a confirmation with no matching purchase is refused, not guessed at")
        void refusesUnknownSubscription() {
            when(paymentRepository.findFirstByRazorpaySubscriptionIdAndPaymentTypeOrderByCreatedAtDesc(
                    RZP_SUB, PaymentType.FIRST_PAYMENT)).thenReturn(Optional.empty());

            assertThrows(SubscriptionException.class,
                         () -> service.verifyAndActivate(USER, RZP_PAY, RZP_SUB, SIGNATURE));
        }
    }

    // ─── two requests at once, and purchases that run out ────────────────────

    @Nested
    @DisplayName("Two purchases at once")
    class Concurrency {

        /** An attempt the database would consider open, created a moment ago. */
        private Payment openAttempt(Long planId, String rzpSubId, LocalDateTime createdAt) {
            Payment open = attempt(USER, PaymentStatus.CREATED);
            open.setPlanId(planId);
            open.setRazorpaySubscriptionId(rzpSubId);
            // createdAt is written by @PrePersist, so a unit test has to set it directly.
            try {
                var field = Payment.class.getDeclaredField("createdAt");
                field.setAccessible(true);
                field.set(open, createdAt);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            return open;
        }

        @Test
        @DisplayName("a second click reopens the same checkout instead of making a second mandate")
        void reusesTheOpenAttempt() {
            Payment open = openAttempt(PLAN_ID, "sub_OPEN", LocalDateTime.now().minusMinutes(2));
            when(paymentRepository.findFirstByUserIdAndStatusAndPaymentTypeOrderByCreatedAtDesc(
                    USER, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT)).thenReturn(Optional.of(open));
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            CreateSubscriptionResponse response = service.createSubscription(USER, PLAN_ID, null);

            assertEquals("sub_OPEN", response.getRazorpaySubscriptionId());
            verify(razorpayClient, never()).createSubscription(anyString(), anyInt());
            verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        }

        @Test
        @DisplayName("a stale open attempt is replaced rather than reopened")
        void doesNotReuseAStaleAttempt() {
            // Long enough ago that the price or the plan may have moved on.
            Payment stale = openAttempt(PLAN_ID, "sub_STALE", LocalDateTime.now().minusHours(3));
            when(paymentRepository.findFirstByUserIdAndStatusAndPaymentTypeOrderByCreatedAtDesc(
                    USER, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT)).thenReturn(Optional.of(stale));
            when(paymentRepository.findByUserIdAndStatusAndPaymentType(
                    USER, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT)).thenReturn(List.of(stale));
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            CreateSubscriptionResponse response = service.createSubscription(USER, PLAN_ID, null);

            assertEquals(RZP_SUB, response.getRazorpaySubscriptionId());
            verify(razorpayClient).cancelSubscription("sub_STALE", true);
        }

        @Test
        @DisplayName("an open attempt for a different plan is replaced, not reopened")
        void doesNotReuseAnotherPlansAttempt() {
            Payment otherPlan = openAttempt(1L, "sub_BASIC", LocalDateTime.now().minusMinutes(1));
            when(paymentRepository.findFirstByUserIdAndStatusAndPaymentTypeOrderByCreatedAtDesc(
                    USER, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT)).thenReturn(Optional.of(otherPlan));
            when(paymentRepository.findByUserIdAndStatusAndPaymentType(
                    USER, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT)).thenReturn(List.of(otherPlan));
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            CreateSubscriptionResponse response = service.createSubscription(USER, PLAN_ID, null);

            assertEquals(RZP_SUB, response.getRazorpaySubscriptionId());
            verify(razorpayClient).cancelSubscription("sub_BASIC", true);
        }

        @Test
        @DisplayName("the request that loses the race cancels its mandate and returns the winner's")
        void losingTheRaceYieldsToTheWinner() {
            // The database refuses the second open attempt. Without this the loser would leave a
            // live mandate behind, and a customer who paid on both would be billed twice a month.
            Payment winner = openAttempt(PLAN_ID, "sub_WINNER", LocalDateTime.now());
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());
            when(paymentRepository.findFirstByUserIdAndStatusAndPaymentTypeOrderByCreatedAtDesc(
                    USER, PaymentStatus.CREATED, PaymentType.FIRST_PAYMENT))
                    .thenReturn(Optional.empty())      // nothing open when this request starts
                    .thenReturn(Optional.of(winner));  // the winner's row, once the insert is refused
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

            CreateSubscriptionResponse response = service.createSubscription(USER, PLAN_ID, null);

            assertEquals("sub_WINNER", response.getRazorpaySubscriptionId());
            verify(razorpayClient).cancelSubscription(RZP_SUB, true);
        }
    }

    @Nested
    @DisplayName("The nightly job")
    class Nightly {

        @Test
        @DisplayName("expires a subscription whose renewal never arrived")
        void expiresLapsedSubscriptions() {
            // Nothing covered this case: the row said ACTIVE for ever, so every count of paying
            // subscribers taken from this table drifted upward and a dead webhook pipeline looked
            // exactly like a healthy one.
            Subscription lapsed = activeSubscription("sub_LAPSED", LocalDateTime.now().minusDays(10));
            when(subscriptionRepository.findExpiredCancellations(any())).thenReturn(List.of());
            when(subscriptionRepository.findExpiredHalted(any())).thenReturn(List.of());
            when(subscriptionRepository.findLapsedActive(any())).thenReturn(List.of(lapsed));

            service.expireAndCancelSubscriptions();

            assertEquals(SubscriptionStatus.EXPIRED, lapsed.getStatus());
            verify(subscriptionRepository).save(lapsed);
        }

        @Test
        @DisplayName("asks only for subscriptions past the grace window")
        void honoursTheGracePeriod() {
            // A renewal that is merely late must not expire anybody: Razorpay retries for days,
            // and a webhook can arrive after a restart.
            when(subscriptionRepository.findExpiredCancellations(any())).thenReturn(List.of());
            when(subscriptionRepository.findExpiredHalted(any())).thenReturn(List.of());
            when(subscriptionRepository.findLapsedActive(any())).thenReturn(List.of());

            service.expireAndCancelSubscriptions();

            ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(subscriptionRepository).findLapsedActive(cutoff.capture());
            assertTrue(cutoff.getValue().isBefore(LocalDateTime.now().minusDays(2)),
                       "the cutoff must sit a grace period in the past");
            assertTrue(cutoff.getValue().isAfter(LocalDateTime.now().minusDays(4)));
        }
    }

    // ─── cancelling ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cancelling")
    class Cancel {

        @Test
        @DisplayName("is recorded even when Razorpay refuses the call")
        void recordsTheCancellationRegardless() {
            // Razorpay refuses in ordinary situations - a mandate never activated, one already
            // cancelled, or an outage. A customer who cannot cancel raises a chargeback instead.
            Subscription active = activeSubscription("sub_OLD", LocalDateTime.now().plusDays(11));
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.of(active));
            org.mockito.Mockito.doThrow(new RuntimeException("no billing cycle is going on"))
                    .when(razorpayClient).cancelSubscription(anyString());

            service.cancelSubscription(USER);

            assertTrue(active.isCancelAtPeriodEnd(), "the intent must be recorded locally");
            verify(subscriptionRepository).save(active);
        }

        @Test
        @DisplayName("keeps access to the end of the period already paid for")
        void keepsTheRemainingPeriod() {
            LocalDateTime end = LocalDateTime.now().plusDays(11);
            Subscription active = activeSubscription("sub_OLD", end);
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.of(active));

            service.cancelSubscription(USER);

            assertEquals(SubscriptionStatus.ACTIVE, active.getStatus());
            assertEquals(end, active.getCurrentPeriodEnd());
        }
    }

    // ─── the webhook safety net ──────────────────────────────────────────────

    @Nested
    @DisplayName("Activation from the webhook")
    class FromWebhook {

        @Test
        @DisplayName("grants the plan with period dates when the browser never confirmed")
        void grantsWithDates() {
            givenAttemptExists(attempt(USER, PaymentStatus.CREATED));
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());

            assertTrue(service.activateFromWebhook(RZP_SUB));

            ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionRepository).save(saved.capture());
            // The defect: the handler flipped the status to ACTIVE and left the dates null, which
            // read as access for ever and threw at the first renewal.
            assertNotNull(saved.getValue().getCurrentPeriodEnd());
        }

        @Test
        @DisplayName("does nothing when the browser already confirmed")
        void skipsWhenAlreadySettled() {
            givenAttemptExists(attempt(USER, PaymentStatus.PAID));

            assertFalse(service.activateFromWebhook(RZP_SUB));
            verify(subscriptionRepository, never()).save(any(Subscription.class));
        }

        @Test
        @DisplayName("does nothing for a subscription we never started")
        void skipsUnknownSubscription() {
            when(paymentRepository.findFirstByRazorpaySubscriptionIdAndPaymentTypeOrderByCreatedAtDesc(
                    RZP_SUB, PaymentType.FIRST_PAYMENT)).thenReturn(Optional.empty());

            assertFalse(service.activateFromWebhook(RZP_SUB));
        }
    }
}
