import apiClient from './apiClient';

/**
 * News-alert subscriptions.
 *
 * Subscribing is separate from the watchlist on purpose: a watchlist is a reading list, while an
 * alert is a push into someone's inbox. Keeping them apart is what stops "I added a company to
 * look at it" turning into email the reader never asked for.
 */

/** @returns {Promise<string[]>} symbols this user receives alerts for */
export async function listSubscriptions() {
  return apiClient('/subscriptions');
}

/** @returns {Promise<{symbol: string, subscribed: boolean}>} */
export async function getSubscriptionStatus(symbol) {
  return apiClient(`/subscriptions/${encodeURIComponent(symbol)}`);
}

/** Idempotent — subscribing to a company you already follow is not an error. */
export async function subscribe(symbol) {
  return apiClient(`/subscriptions/${encodeURIComponent(symbol)}`, { method: 'POST' });
}

export async function unsubscribe(symbol) {
  return apiClient(`/subscriptions/${encodeURIComponent(symbol)}`, { method: 'DELETE' });
}

/**
 * Honours an unsubscribe link from an email.
 *
 * A plain fetch rather than apiClient, and for the same reason as email verification: the link is
 * opened from a mail client where there is no session. apiClient would try to refresh an absent
 * token and send the reader to the login page — asking someone to sign in before they may stop
 * unwanted email, when the alternative they reach for is the spam button.
 *
 * @param {string} token the signed value from the link
 */
export async function unsubscribeByToken(token) {
  const res = await fetch(`/api/alerts/unsubscribe?token=${encodeURIComponent(token)}`);
  const data = await res.json().catch(() => ({}));
  if (!res.ok || data.unsubscribed === false) {
    throw new Error(data.message || 'This unsubscribe link is not valid.');
  }
  return data;
}
