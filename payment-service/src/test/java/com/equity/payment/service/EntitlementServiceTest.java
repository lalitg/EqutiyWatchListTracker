package com.equity.payment.service;

import com.equity.payment.dto.EntitlementsResponse;
import com.equity.payment.entity.Plan;
import com.equity.payment.entity.Subscription;
import com.equity.payment.enums.PlanTier;
import com.equity.payment.enums.SubscriptionStatus;
import com.equity.payment.repository.PlanRepository;
import com.equity.payment.repository.SubscriptionRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The paywall's rules, tier by tier.
 *
 * <p>These assertions are the specification: if the product decides that Basic should include
 * sentiment, a test here fails first and says so, rather than a page quietly unlocking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntitlementServiceTest {

    private static final Long USER = 42L;

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;

    private EntitlementService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementService(subscriptionRepository, planRepository, true);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Subscription subscription(SubscriptionStatus status, LocalDateTime periodEnd) {
        Subscription sub = new Subscription();
        sub.setUserId(USER);
        sub.setPlanId(7L);
        sub.setStatus(status);
        sub.setCurrentPeriodEnd(periodEnd);
        return sub;
    }

    /** Puts an ACTIVE subscription on a plan of the given tier in front of the service. */
    private void given(String planTier) {
        Plan plan = new Plan();
        plan.setTier(planTier);
        when(subscriptionRepository.findByUserId(USER))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE,
                                                     LocalDateTime.now().plusDays(10))));
        when(planRepository.findById(7L)).thenReturn(Optional.of(plan));
    }

    private static boolean feature(EntitlementsResponse r, String key) {
        return Boolean.TRUE.equals(r.features().get(key));
    }

    // ─── the three tiers ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Free (no subscription)")
    class Free {

        @BeforeEach
        void noSubscription() {
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.empty());
        }

        @Test
        void isTheDefault() {
            EntitlementsResponse r = service.forUser(USER);

            assertEquals(PlanTier.FREE, r.tier());
            assertNull(r.expiresAt(), "nothing to expire when nothing was bought");
            assertNull(r.status());
        }

        @Test
        void locksEverythingThatIsSold() {
            EntitlementsResponse r = service.forUser(USER);

            assertFalse(feature(r, EntitlementService.F_SENTIMENTS));
            assertFalse(feature(r, EntitlementService.F_EXTREMES));
            assertFalse(feature(r, EntitlementService.F_ALERTS));
            assertFalse(feature(r, EntitlementService.F_MUTUAL_FUNDS));
            assertFalse(feature(r, EntitlementService.F_EVENTS));
            assertFalse(feature(r, EntitlementService.F_CALENDAR));
            assertFalse(feature(r, EntitlementService.F_COMPANY_NEWS_TABS));
            assertFalse(feature(r, EntitlementService.F_GLOBAL_ALL_REGIONS));
            assertFalse(feature(r, EntitlementService.F_DOMESTIC_DRILLDOWN));
        }

        @Test
        void getsTheSmallestLimitsAndANewsTeaser() {
            EntitlementsResponse r = service.forUser(USER);

            assertEquals(1, r.limits().get(EntitlementService.L_WATCHLISTS));
            assertEquals(5, r.limits().get(EntitlementService.L_COMPANIES_PER_WATCHLIST));
            assertEquals(5, r.limits().get(EntitlementService.L_DOMESTIC_NEWS));
        }

        @Test
        void neverLooksUpAPlan() {
            service.forUser(USER);
            verify(planRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("Basic")
    class Basic {

        @BeforeEach
        void onBasic() {
            given("BASIC");
        }

        @Test
        void opensTheMarketPages() {
            EntitlementsResponse r = service.forUser(USER);

            assertEquals(PlanTier.BASIC, r.tier());
            assertTrue(feature(r, EntitlementService.F_MUTUAL_FUNDS));
            assertTrue(feature(r, EntitlementService.F_EVENTS));
            assertTrue(feature(r, EntitlementService.F_CALENDAR));
            assertTrue(feature(r, EntitlementService.F_COMPANY_NEWS_TABS));
            assertTrue(feature(r, EntitlementService.F_GLOBAL_ALL_REGIONS));
            assertTrue(feature(r, EntitlementService.F_DOMESTIC_DRILLDOWN));
        }

        @Test
        void stillHoldsBackSentimentExtremesAndAlerts() {
            EntitlementsResponse r = service.forUser(USER);

            assertFalse(feature(r, EntitlementService.F_SENTIMENTS));
            assertFalse(feature(r, EntitlementService.F_EXTREMES));
            assertFalse(feature(r, EntitlementService.F_ALERTS));
        }

        @Test
        void raisesTheLimitsAndRemovesTheNewsCap() {
            EntitlementsResponse r = service.forUser(USER);

            assertEquals(10, r.limits().get(EntitlementService.L_WATCHLISTS));
            assertEquals(10, r.limits().get(EntitlementService.L_COMPANIES_PER_WATCHLIST));
            assertEquals(-1, r.limits().get(EntitlementService.L_DOMESTIC_NEWS));
        }

        @Test
        void reportsWhenThePeriodEnds() {
            EntitlementsResponse r = service.forUser(USER);

            assertEquals("ACTIVE", r.status());
            assertTrue(r.expiresAt().isAfter(LocalDateTime.now()));
        }
    }

    @Nested
    @DisplayName("Advanced")
    class Advanced {

        @BeforeEach
        void onAdvanced() {
            given("ADVANCED");
        }

        @Test
        void unlocksEverything() {
            EntitlementsResponse r = service.forUser(USER);

            assertEquals(PlanTier.ADVANCED, r.tier());
            r.features().forEach((key, allowed) ->
                    assertTrue(allowed, key + " should be open on Advanced"));
        }

        @Test
        void getsTheLargestLimits() {
            EntitlementsResponse r = service.forUser(USER);

            assertEquals(20, r.limits().get(EntitlementService.L_WATCHLISTS));
            assertEquals(25, r.limits().get(EntitlementService.L_COMPANIES_PER_WATCHLIST));
            assertEquals(-1, r.limits().get(EntitlementService.L_DOMESTIC_NEWS));
        }
    }

    // ─── the states a subscription can be in ─────────────────────────────────

    @Nested
    @DisplayName("Subscription state")
    class State {

        @Test
        void cancelledButStillInsideThePaidPeriodKeepsTheTier() {
            // Cancelling stops the renewal. The month already paid for is still theirs.
            Subscription sub = subscription(SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(3));
            sub.setCancelAtPeriodEnd(true);
            Plan plan = new Plan();
            plan.setTier("ADVANCED");
            when(subscriptionRepository.findByUserId(USER)).thenReturn(Optional.of(sub));
            when(planRepository.findById(7L)).thenReturn(Optional.of(plan));

            assertEquals(PlanTier.ADVANCED, service.forUser(USER).tier());
        }

        @Test
        void activeButPastThePeriodEndDropsToFree() {
            // The nightly expiry job has not run yet; access must not outlive the payment.
            when(subscriptionRepository.findByUserId(USER))
                    .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE,
                                                         LocalDateTime.now().minusMinutes(1))));

            EntitlementsResponse r = service.forUser(USER);

            assertEquals(PlanTier.FREE, r.tier());
            assertNull(r.expiresAt(), "an expired period is not an expiry date worth showing");
            verify(planRepository, never()).findById(any());
        }

        @Test
        void pendingGrantsNothing() {
            // Razorpay subscription created, first payment not through yet.
            when(subscriptionRepository.findByUserId(USER))
                    .thenReturn(Optional.of(subscription(SubscriptionStatus.PENDING,
                                                         LocalDateTime.now().plusDays(30))));

            assertEquals(PlanTier.FREE, service.forUser(USER).tier());
        }

        @Test
        void haltedCancelledAndExpiredAllGrantNothing() {
            for (SubscriptionStatus status : new SubscriptionStatus[]{
                    SubscriptionStatus.HALTED, SubscriptionStatus.CANCELLED, SubscriptionStatus.EXPIRED}) {

                when(subscriptionRepository.findByUserId(USER))
                        .thenReturn(Optional.of(subscription(status, LocalDateTime.now().plusDays(10))));

                assertEquals(PlanTier.FREE, service.forUser(USER).tier(),
                             status + " should not grant access");
            }
        }

        @Test
        void anOpenEndedActivePeriodStillGrantsTheTier() {
            // A null period end means "no known end" - a lifetime or manually granted row.
            Plan plan = new Plan();
            plan.setTier("BASIC");
            when(subscriptionRepository.findByUserId(USER))
                    .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE, null)));
            when(planRepository.findById(7L)).thenReturn(Optional.of(plan));

            assertEquals(PlanTier.BASIC, service.forUser(USER).tier());
        }
    }

    // ─── bad data must lock, not crash ───────────────────────────────────────

    @Nested
    @DisplayName("Unreadable plan data")
    class BadData {

        @Test
        void aTierThatIsNotATierReadsAsFree() {
            given("PREMIUM_PLUS");
            assertEquals(PlanTier.FREE, service.forUser(USER).tier());
        }

        @Test
        void aMissingTierReadsAsFree() {
            given(null);
            assertEquals(PlanTier.FREE, service.forUser(USER).tier());
        }

        @Test
        void caseAndStrayWhitespaceAreTolerated() {
            given("  advanced ");
            assertEquals(PlanTier.ADVANCED, service.forUser(USER).tier());
        }

        @Test
        void aSubscriptionPointingAtNoPlanReadsAsFree() {
            when(subscriptionRepository.findByUserId(USER))
                    .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE,
                                                         LocalDateTime.now().plusDays(10))));
            when(planRepository.findById(7L)).thenReturn(Optional.empty());

            assertEquals(PlanTier.FREE, service.forUser(USER).tier());
        }
    }

    // ─── failure must unlock, never lock out ─────────────────────────────────

    @Nested
    @DisplayName("When things go wrong")
    class Failure {

        @Test
        void theKillSwitchGrantsAdvancedToEverybody() {
            service = new EntitlementService(subscriptionRepository, planRepository, false);

            EntitlementsResponse r = service.forUser(USER);

            assertEquals(PlanTier.ADVANCED, r.tier());
            r.features().forEach((key, allowed) -> assertTrue(allowed, key + " should be open"));
            verify(subscriptionRepository, never()).findByUserId(any());
        }

        @Test
        void aDatabaseFailureUnlocksRatherThanLocksOut() {
            when(subscriptionRepository.findByUserId(USER))
                    .thenThrow(new RuntimeException("connection refused"));

            assertEquals(PlanTier.ADVANCED, service.forUser(USER).tier());
        }
    }

    @Test
    void anAnonymousVisitorIsFreeAndIsNotLookedUp() {
        // The gate in main-api asks this for requests that carry no session at all.
        EntitlementsResponse r = service.forUser(null);

        assertEquals(PlanTier.FREE, r.tier());
        assertFalse(feature(r, EntitlementService.F_SENTIMENTS));
        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @Test
    void tierForUserAgreesWithTheFullAnswer() {
        given("BASIC");
        assertEquals(service.forUser(USER).tier(), service.tierForUser(USER));
    }
}
