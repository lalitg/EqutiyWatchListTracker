package com.equity.watchlist.exception;

/**
 * The caller's plan does not stretch to what they just asked for.
 *
 * <h2>Why this is not an IllegalArgumentException</h2>
 * Nothing is wrong with the request. The company exists, the watchlist is theirs, the symbol is
 * spelled correctly — they simply have not paid for it. That is a 403, not a 400, and the
 * difference matters to the browser: a 400 reads as "fix your input", a 403 as "upgrade".
 *
 * <p>Carries the tier that would have worked and the feature name the pricing page knows, so the
 * UI can send the reader to the right row rather than to a generic price list.
 */
public class EntitlementRequiredException extends RuntimeException {

    private final String requiredTier;
    private final String feature;

    /**
     * @param message      written for the person on screen, not for a log
     * @param requiredTier FREE, BASIC or ADVANCED — the cheapest tier that allows this
     * @param feature      the feature key the frontend uses, e.g. "alerts" or "watchlists"
     */
    public EntitlementRequiredException(String message, String requiredTier, String feature) {
        super(message);
        this.requiredTier = requiredTier;
        this.feature      = feature;
    }

    public String getRequiredTier() { return requiredTier; }
    public String getFeature()      { return feature; }
}
