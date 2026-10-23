import apiClient from './apiClient';

/**
 * Plans and entitlements.
 *
 * Two questions, deliberately separate: what is for sale (public, the same for everybody) and what
 * this particular reader is allowed to see (private, from their token). Mixing them would mean the
 * pricing page could not be rendered for a visitor who has not signed in.
 */

/**
 * What the signed-in reader may see.
 *
 * Only ever called with a session. Called without one, apiClient would meet a 401, fail to refresh
 * and send the reader to the login page — a visitor browsing the market pages must not be thrown
 * out of the app because a paywall wanted to know their tier.
 *
 * @returns {Promise<{tier: string, features: Object, limits: Object, expiresAt: ?string, status: ?string}>}
 */
export async function fetchEntitlements() {
  return apiClient('/entitlements/me');
}

/**
 * The plans on sale, cheapest first is not guaranteed — the pricing page orders them by tier.
 *
 * Public: the endpoint needs no token, because a price nobody can read sells nothing.
 *
 * @returns {Promise<Array<{id: number, name: string, displayName: string, description: string,
 *                          pricePaise: number, priceDisplay: string, billingCycle: string,
 *                          durationDays: number, tier: string}>>}
 */
export async function fetchPlans() {
  return apiClient('/plans');
}
