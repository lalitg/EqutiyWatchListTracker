package com.equity.payment.dto;

import com.equity.payment.enums.PlanTier;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * What one user is allowed to see and do, resolved server-side.
 *
 * <h2>Why the browser is told the answers, not the rules</h2>
 * If React held "Advanced includes sentiment", the rule would exist twice — here and there — and
 * would drift the first time a tier changed. The browser asks what this user gets and draws
 * accordingly; it never maps a plan name to a feature set.
 *
 * <h2>Shape</h2>
 * {@code features} are booleans keyed by feature name, {@code limits} are numbers. Both are maps
 * rather than fixed fields so a new feature is one line in the resolver and one usage in the UI,
 * with no DTO change and no version mismatch between a deployed browser and a deployed server — an
 * older page simply does not ask about a key it has never heard of.
 *
 * @param tier      the tier this user is on right now
 * @param features  feature name → allowed
 * @param limits    limit name → value
 * @param expiresAt when the current paid period ends, null on FREE
 * @param status    the underlying subscription status, null on FREE
 * @param cancelAtPeriodEnd whether the renewal has been stopped - the difference between "renews
 *                  on 27 Nov" and "ends on 27 Nov", which {@code expiresAt} alone cannot express.
 *                  Carried here rather than left to a second call against /subscriptions/me,
 *                  because the navbar needs the answer on every page and a billing lookup per
 *                  page load is a price not worth paying for one boolean.
 */
public record EntitlementsResponse(PlanTier tier,
                                   Map<String, Boolean> features,
                                   Map<String, Integer> limits,
                                   LocalDateTime expiresAt,
                                   String status,
                                   boolean cancelAtPeriodEnd) {
}
