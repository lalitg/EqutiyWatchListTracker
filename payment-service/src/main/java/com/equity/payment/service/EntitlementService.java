package com.equity.payment.service;

import com.equity.payment.dto.EntitlementsResponse;
import com.equity.payment.entity.Plan;
import com.equity.payment.entity.Subscription;
import com.equity.payment.enums.PlanTier;
import com.equity.payment.enums.SubscriptionStatus;
import com.equity.payment.repository.PlanRepository;
import com.equity.payment.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decides what a user is allowed to see, and is the only place that decides it.
 *
 * <h2>The single source of truth</h2>
 * Every other service and the browser ask this question rather than answering it. The rules live
 * here because they are product rules, not data: "Advanced includes sentiment" belongs in code
 * that can be read and tested, while the price of Advanced belongs in a row that marketing can
 * edit without a deployment.
 *
 * <h2>How a tier is reached</h2>
 * <ol>
 *   <li>No subscription row, or one that is not ACTIVE → {@link PlanTier#FREE}.</li>
 *   <li>ACTIVE but past its period end → FREE. The nightly expiry job will tidy the row, and until
 *       it runs the user must not keep what they stopped paying for.</li>
 *   <li>Otherwise the plan's tier, with an unreadable tier resolving to FREE rather than to
 *       everything.</li>
 * </ol>
 *
 * <p>A cancellation that has not yet reached its period end still grants the paid tier: the user
 * paid for that month and cancelling only stops the renewal.
 *
 * <h2>The switch that matters when things go wrong</h2>
 * {@code billing.enabled=false} grants everyone ADVANCED. If this service, the database or Razorpay
 * has a bad day, the failure mode becomes "everything is unlocked for a while" rather than "paying
 * customers are locked out of what they bought". The same applies to an unexpected error: the
 * fallback is generous, deliberately, because a false lock costs more than a false unlock.
 */
@Service
public class EntitlementService {

    private static final Logger logger = LoggerFactory.getLogger(EntitlementService.class);

    // ── Feature keys. The browser and the other services use these exact strings. ──────────────
    public static final String F_SENTIMENTS         = "sentiments";
    public static final String F_EXTREMES           = "extremes";
    public static final String F_ALERTS             = "alerts";
    public static final String F_MUTUAL_FUNDS       = "mutualFunds";
    public static final String F_EVENTS             = "events";
    public static final String F_CALENDAR           = "calendar";
    public static final String F_COMPANY_NEWS_TABS  = "companyNewsTabs";
    public static final String F_GLOBAL_ALL_REGIONS = "globalAllRegions";
    public static final String F_DOMESTIC_DRILLDOWN = "domesticDrilldown";

    // ── Limit keys ────────────────────────────────────────────────────────────────────────────
    public static final String L_WATCHLISTS             = "watchlists";
    public static final String L_COMPANIES_PER_WATCHLIST = "companiesPerWatchlist";
    public static final String L_DOMESTIC_NEWS          = "domesticNewsItems";

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final boolean billingEnabled;

    public EntitlementService(SubscriptionRepository subscriptionRepository,
                              PlanRepository planRepository,
                              @Value("${billing.enabled:true}") boolean billingEnabled) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository         = planRepository;
        this.billingEnabled         = billingEnabled;
    }

    /**
     * Everything the caller is entitled to.
     *
     * @param userId the user, from their token
     */
    @Transactional(readOnly = true)
    public EntitlementsResponse forUser(Long userId) {
        if (!billingEnabled) {
            logger.debug("billing.enabled=false - granting ADVANCED to userId={}", userId);
            return response(PlanTier.ADVANCED, null, null);
        }

        // No session, no subscription. The gate in main-api asks this question for anonymous
        // visitors too, and they get exactly what a signed-in user without a plan gets.
        if (userId == null) return response(PlanTier.FREE, null, null);

        try {
            Optional<Subscription> found = subscriptionRepository.findByUserId(userId);
            if (found.isEmpty()) return response(PlanTier.FREE, null, null);

            Subscription sub = found.get();
            PlanTier tier = tierOf(sub);
            return response(tier,
                            tier == PlanTier.FREE ? null : sub.getCurrentPeriodEnd(),
                            sub.getStatus() == null ? null : sub.getStatus().name());
        } catch (Exception e) {
            // Never lock a paying user out because a lookup failed.
            logger.error("Could not resolve entitlements for userId={} - granting ADVANCED: {}",
                         userId, e.getMessage());
            return response(PlanTier.ADVANCED, null, null);
        }
    }

    /** The tier alone, for callers that only need to branch on it. */
    @Transactional(readOnly = true)
    public PlanTier tierForUser(Long userId) {
        return forUser(userId).tier();
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private PlanTier tierOf(Subscription sub) {
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) return PlanTier.FREE;

        LocalDateTime end = sub.getCurrentPeriodEnd();
        if (end != null && end.isBefore(LocalDateTime.now())) {
            // Paid period is over and the nightly job has not caught it yet.
            return PlanTier.FREE;
        }

        return planRepository.findById(sub.getPlanId())
                .map(Plan::getTier)
                .map(PlanTier::fromName)
                .orElse(PlanTier.FREE);
    }

    /**
     * Turns a tier into the concrete answers the UI and the other services need.
     *
     * <p>Written as one table rather than scattered {@code if (tier == ...)} checks, so the whole
     * product's gating can be read at a glance and compared against the plan document.
     */
    private EntitlementsResponse response(PlanTier tier, LocalDateTime expiresAt, String status) {
        boolean basic    = tier.atLeast(PlanTier.BASIC);
        boolean advanced = tier.atLeast(PlanTier.ADVANCED);

        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put(F_SENTIMENTS,         advanced);   // watchlist columns, company tab, scores
        features.put(F_EXTREMES,           advanced);
        features.put(F_ALERTS,             advanced);   // "Alert me", the bell, the panel
        features.put(F_MUTUAL_FUNDS,       basic);
        features.put(F_EVENTS,             basic);
        features.put(F_CALENDAR,           basic);
        features.put(F_COMPANY_NEWS_TABS,  basic);      // important / events / regulatory tabs
        features.put(F_GLOBAL_ALL_REGIONS, basic);      // US, Asia, Europe, Commodities news
        features.put(F_DOMESTIC_DRILLDOWN, basic);      // opening an index or sector

        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put(L_WATCHLISTS,              advanced ? 20 : basic ? 10 : 1);
        limits.put(L_COMPANIES_PER_WATCHLIST, advanced ? 25 : basic ? 10 : 5);
        // Free sees a teaser of the domestic news list; paid tiers see all of it. -1 means no cap.
        limits.put(L_DOMESTIC_NEWS,           basic ? -1 : 5);

        return new EntitlementsResponse(tier, features, limits, expiresAt, status);
    }
}
