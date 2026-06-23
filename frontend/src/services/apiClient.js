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
  const config = {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
    ...options,
  };
  return fetch(url, config);
}

async function apiClient(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;

  let response = await doFetch(url, options);

  // Silent refresh on 401
  if (response.status === 401) {
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

  if (!response.ok) {
    let errorMessage = `HTTP ${response.status}`;
    try {
      const errorData = await response.json();
      errorMessage = errorData.error || errorData.message || errorMessage;
    } catch {
      // response body wasn't JSON
    }
    throw new Error(errorMessage);
  }

  if (response.status === 204) return null;
  return response.json();
}

export default apiClient;
