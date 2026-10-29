package com.equity.payment.enums;

/**
 * What a plan actually grants, as opposed to what it costs.
 *
 * <h2>Why a tier and not a feature list per plan</h2>
 * Prices and billing cycles change often; the shape of the product changes rarely. Keeping the tier
 * on the plan row and the features in code means marketing can add "Advanced Annual" or change
 * ₹299 to ₹349 by editing a row, with no deployment — while the question "what does Advanced
 * include?" has exactly one answer, in one place.
 *
 * <h2>FREE is not sold</h2>
 * It is what a user has when no subscription is active: before they ever buy, after they cancel,
 * and after one expires. There is deliberately no FREE row in {@code plans} — a free plan that had
 * to be purchased, renewed and cancelled would be ceremony around nothing.
 */
public enum PlanTier {

    /** No active subscription. The default for everybody. */
    FREE,

    /** Paid entry tier: the market pages open up, sentiment and alerts stay closed. */
    BASIC,

    /** Everything, including sentiment, the Extremes boards and alerts. */
    ADVANCED;

    /**
     * Reads a tier stored on a plan row, tolerating junk.
     *
     * <p>An unrecognised or missing value resolves to {@link #FREE}: a row typed wrongly should
     * grant nothing, never everything.
     */
    public static PlanTier fromName(String value) {
        if (value == null) return FREE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }

    /** @return true when this tier includes everything {@code other} includes */
    public boolean atLeast(PlanTier other) {
        return ordinal() >= other.ordinal();
    }
}
