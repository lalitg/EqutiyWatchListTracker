import { refreshTokens } from './authService';

const API_BASE = '/api/v1';

const STORAGE_KEYS = {
  ACCESS_TOKEN: 'nf_access_token',
  REFRESH_TOKEN: 'nf_refresh_token',
  USER: 'nf_user',
};

function getAccessToken() {
  return localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
}

function getRefreshToken() {
  return localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
}

function saveTokens(accessToken, refreshToken) {
  localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
  localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
}

function clearAuth() {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.USER);
  window.location.href = '/login';
}

async function doFetch(url, options) {
  const token = getAccessToken();
  // headers is pulled out of options before the spread. Spreading options over a config that
  // already held a headers key replaced the whole merged object, so a caller passing a header of
  // its own silently dropped the Authorization header along with it.
  const { headers, ...rest } = options;
  return fetch(url, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });
}

/**
 * One request, with the bearer token attached and a 401 retried once against a fresh token.
 *
 * Returns the raw Response. Callers wanting JSON go through {@link toJson}; the one caller that
 * wants bytes — the AMFI documents — reads the body itself.
 */
async function send(url, options = {}) {
  let response = await doFetch(url, options);

  // A 401 is only ours to act on if we actually sent a token. A visitor browsing the free pages
  // has nothing to refresh and nothing to clear, and bouncing them to /login would turn a public
  // page into a login wall.
  if (response.status === 401 && getAccessToken()) {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        const data = await refreshTokens(refreshToken);
        saveTokens(data.accessToken, data.refreshToken);
        // Retry original request with new token
        response = await doFetch(url, options);
      } catch {
        clearAuth();
        throw new Error('Session expired. Please log in again.');
      }
    } else {
      clearAuth();
      throw new Error('Session expired. Please log in again.');
    }
  }

  return response;
}

async function toJson(response) {
  if (!response.ok) {
    let errorMessage = `HTTP ${response.status}`;
    try {
      const errorData = await response.json();
      errorMessage = errorData.error || errorData.message || errorMessage;
    } catch {
      // response body wasn't JSON
    }
    const error = new Error(errorMessage);
    // Carried so a caller can tell "you have not paid for this" (403) apart from a service that
    // is down. Without it every failure reads alike and the paywall looks like an outage.
    error.status = response.status;
    throw error;
  }

  if (response.status === 204) return null;
  return response.json();
}

async function apiClient(endpoint, options = {}) {
  return toJson(await send(`${API_BASE}${endpoint}`, options));
}

/**
 * The same authenticated request, for an endpoint that does not live under /api/v1.
 *
 * News, sentiment, extremes, macro events, SEBI filings, AMFI documents and the index and sector
 * constituent lists are served on their own prefixes by the schedulers behind the gateway. They
 * were public when they were written, so they were called with a bare fetch and no headers. Once
 * the paywall went in front of them the gateway read those calls as anonymous and refused them
 * with 403 — however much the signed-in reader had actually paid for.
 *
 * @param {string} path absolute API path, e.g. '/api/news/extremes/days'
 */
export async function apiFetch(path, options = {}) {
  return toJson(await send(path, options));
}

/**
 * As {@link apiFetch}, but hands back the Response untouched for a body that is not JSON.
 */
export async function apiFetchRaw(path, options = {}) {
  return send(path, options);
}

export default apiClient;
