import apiClient from './apiClient';

const USERS_BASE = '/api/v1/users';

/**
 * Redeems an email-verification link.
 *
 * Deliberately a plain fetch rather than apiClient: this call arrives from a mail client, where
 * there is usually no session at all. apiClient would try to refresh an absent token and send the
 * reader to the login page, turning a working link into an apparent failure. The random token in
 * the URL is the only credential the server needs.
 *
 * @param {string} token the value from the emailed link
 * @returns {Promise<{verified: boolean, email: string}>}
 */
export async function verifyEmail(token) {
  const res = await fetch(`${USERS_BASE}/verify-email?token=${encodeURIComponent(token)}`);
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'This verification link could not be used.');
  }
  return res.json();
}

/**
 * Asks for a fresh verification email for the signed-in user.
 *
 * `sent` comes back false — with no error — when the address is already verified, or when email is
 * switched off in this environment. The caller should report what happened rather than assuming a
 * message is on its way.
 *
 * @returns {Promise<{sent: boolean, message: string}>}
 */
export async function sendVerificationEmail() {
  return apiClient('/users/me/verify-email', { method: 'POST' });
}

/** The signed-in user's profile, including `emailVerified`. */
export async function getProfile() {
  return apiClient('/users/me');
}
