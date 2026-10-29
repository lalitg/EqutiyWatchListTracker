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

/**
 * Starts a purchase: creates the Razorpay subscription and returns what the checkout window needs.
 *
 * The key id comes back with it rather than being baked into the bundle at build time, so the same
 * build works against test keys locally and live keys on the server, and rotating a key never
 * means rebuilding the frontend. The key secret stays on the server and is never part of this
 * exchange.
 *
 * @param {number} planId     the plan being bought
 * @param {string} couponCode optional
 * @returns {Promise<{razorpaySubscriptionId: string, razorpayKeyId: string,
 *                    firstPaymentAmountPaise: number, renewalAmountPaise: number,
 *                    discountPaise: number, currency: string}>}
 */
export async function createSubscription(planId, couponCode) {
  return apiClient('/subscriptions/create', {
    method: 'POST',
    body: JSON.stringify({ planId, couponCode: couponCode || null }),
  });
}

/**
 * Confirms a completed payment.
 *
 * The three values come straight from the checkout window and mean nothing on their own: the
 * server recomputes the HMAC signature with its own key secret and only then marks the
 * subscription active. A failure here is a 400 and the plan stays unpaid, which is the only safe
 * direction for this call to fail in.
 *
 * @param {{razorpayPaymentId: string, razorpaySubscriptionId: string, razorpaySignature: string}} payment
 * @returns {Promise<{planName: string, status: string, currentPeriodEnd: string}>}
 */
export async function verifySubscription(payment) {
  return apiClient('/subscriptions/verify', {
    method: 'POST',
    body: JSON.stringify(payment),
  });
}

/** The caller's current subscription, or null when they have never bought one. */
export async function fetchMySubscription() {
  return apiClient('/subscriptions/me');
}

/** Stops the renewal. Access continues to the end of the period already paid for. */
export async function cancelSubscription() {
  return apiClient('/subscriptions/cancel', { method: 'POST' });
}
