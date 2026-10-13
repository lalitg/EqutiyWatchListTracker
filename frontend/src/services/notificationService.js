import apiClient from './apiClient';

/**
 * In-app notifications — the bell and its panel.
 *
 * This is the live delivery channel for news alerts. The email path exists in the backend and is
 * switched off: AWS refused SES production access, so it cannot reach real users.
 */

/** @returns {Promise<Array>} newest first, dismissed ones excluded */
export async function listNotifications(limit = 20) {
  return apiClient(`/notifications?limit=${limit}`);
}

/**
 * The number on the bell.
 *
 * Polled, so it is deliberately the narrowest call in the app — one indexed count, nothing else.
 *
 * @returns {Promise<number>}
 */
export async function getUnreadCount() {
  const result = await apiClient('/notifications/unread-count');
  return result?.count ?? 0;
}

/** Opening an item: clears it from the badge, keeps it in the list. */
export async function markRead(id) {
  return apiClient(`/notifications/${id}/read`, { method: 'POST' });
}

/** Clears the badge in one call. */
export async function markAllRead() {
  return apiClient('/notifications/read-all', { method: 'POST' });
}

/** The × on a row: hides it for good. */
export async function dismissNotification(id) {
  return apiClient(`/notifications/${id}`, { method: 'DELETE' });
}
