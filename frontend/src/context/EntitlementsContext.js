import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useAuth } from './AuthContext';
import { fetchEntitlements } from '../services/billingService';

/**
 * What this reader is allowed to see, fetched once per session and read by every gated surface.
 *
 * The rules themselves are not here. The server answers "may this user see sentiment?" and this
 * context carries the answer; nothing in the browser maps a plan name to a feature list. That is
 * what stops the paywall meaning two different things in two places the first time a tier changes.
 */

/** Feature keys. These strings must match EntitlementService in payment-service. */
export const FEATURES = {
  SENTIMENTS:         'sentiments',
  EXTREMES:           'extremes',
  ALERTS:             'alerts',
  MUTUAL_FUNDS:       'mutualFunds',
  EVENTS:             'events',
  CALENDAR:           'calendar',
  COMPANY_NEWS_TABS:  'companyNewsTabs',
  GLOBAL_ALL_REGIONS: 'globalAllRegions',
  DOMESTIC_DRILLDOWN: 'domesticDrilldown',
};

/** Limit keys, same contract. */
export const LIMITS = {
  WATCHLISTS:              'watchlists',
  COMPANIES_PER_WATCHLIST: 'companiesPerWatchlist',
  DOMESTIC_NEWS:           'domesticNewsItems',
};

/**
 * What a visitor with no session gets.
 *
 * Expressible here without duplicating the tier table because free means "nothing is unlocked":
 * every feature is absent, so {@link can} says no, and only the three numbers need stating.
 */
const SIGNED_OUT = {
  tier: 'FREE',
  features: {},
  limits: { watchlists: 1, companiesPerWatchlist: 5, domesticNewsItems: 5 },
  expiresAt: null,
  status: null,
  cancelAtPeriodEnd: false,
};

/**
 * What a reader gets when the question cannot be answered.
 *
 * Unlocked, on purpose, and matching the server's own fallback: if entitlements are unreachable the
 * failure should show everyone everything for a few minutes, not lock paying customers out of what
 * they have bought. A false unlock costs a little revenue; a false lock costs a customer.
 */
const UNREACHABLE = {
  tier: 'ADVANCED',
  features: Object.fromEntries(Object.values(FEATURES).map(key => [key, true])),
  limits: { watchlists: 20, companiesPerWatchlist: 25, domesticNewsItems: -1 },
  expiresAt: null,
  status: null,
  // Never claim a plan is ending on the strength of a failed lookup: that reads as a threat to
  // somebody whose subscription is perfectly healthy.
  cancelAtPeriodEnd: false,
};

const EntitlementsContext = createContext(null);

export function EntitlementsProvider({ children }) {
  const { isLoggedIn } = useAuth();

  const [state, setState] = useState(() => ({ ...SIGNED_OUT, ready: !isLoggedIn, failed: false }));

  const load = useCallback(() => {
    if (!isLoggedIn) {
      setState({ ...SIGNED_OUT, ready: true, failed: false });
      return Promise.resolve();
    }

    setState(prev => ({ ...prev, ready: false }));

    return fetchEntitlements()
      .then(data => setState({
        tier:      data.tier || 'FREE',
        features:  data.features || {},
        limits:    data.limits || SIGNED_OUT.limits,
        expiresAt: data.expiresAt || null,
        status:    data.status || null,
        cancelAtPeriodEnd: data.cancelAtPeriodEnd === true,
        ready:     true,
        failed:    false,
      }))
      .catch(() => setState({ ...UNREACHABLE, ready: true, failed: true }));
  }, [isLoggedIn]);

  // Runs on sign-in and on sign-out, which is exactly when the answer changes.
  useEffect(() => { load(); }, [load]);

  const value = useMemo(() => ({
    tier:      state.tier,
    features:  state.features,
    limits:    state.limits,
    expiresAt: state.expiresAt,
    status:    state.status,
    ready:     state.ready,
    failed:    state.failed,

    /**
     * True when the plan is still live but will not renew.
     *
     * The server decides this, like everything else here. It is the difference between "renews on
     * 27 Nov" and "ends on 27 Nov" - two opposite sentences that {@code expiresAt} alone cannot
     * tell apart, and getting it backwards is the kind of billing message people screenshot.
     */
    cancelAtPeriodEnd: state.cancelAtPeriodEnd === true,

    /**
     * Whether a feature is open to this reader.
     *
     * While the answer is still in flight nothing is locked. The alternative is a lock appearing on
     * a paying customer's page for a moment on every load, which reads as the product breaking; a
     * free reader instead sees a fraction of a second of content before the blur lands, and the
     * entitlement call is a two-key lookup that almost always answers before the data it gates.
     */
    can: (feature) => (state.ready ? state.features[feature] === true : true),

    /**
     * A numeric allowance. -1 means no cap.
     *
     * @param {string} key one of {@link LIMITS}
     * @param {number} fallback used until the answer arrives
     */
    limit: (key, fallback = -1) => (state.ready
      ? (typeof state.limits?.[key] === 'number' ? state.limits[key] : fallback)
      : fallback),

    /** Re-reads entitlements — after a purchase, or after a plan change elsewhere. */
    refresh: load,
  }), [state, load]);

  return (
    <EntitlementsContext.Provider value={value}>
      {children}
    </EntitlementsContext.Provider>
  );
}

export function useEntitlements() {
  const ctx = useContext(EntitlementsContext);
  if (!ctx) throw new Error('useEntitlements must be used within EntitlementsProvider');
  return ctx;
}
