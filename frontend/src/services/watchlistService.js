import apiClient from './apiClient';

const BASE = '/watchlist';

// ── Named watchlist (list) management ────────────────────────────────────────

export function getLists() {
  return apiClient(`${BASE}/lists`);
}

export function createList(name) {
  return apiClient(`${BASE}/lists`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export function deleteList(id) {
  return apiClient(`${BASE}/lists/${id}`, { method: 'DELETE' });
}

// ── Company entries ───────────────────────────────────────────────────────────

export function fetchEntries(watchlistId) {
  const qs = watchlistId ? `?watchlistId=${watchlistId}` : '';
  return apiClient(`${BASE}/getAllCompanies${qs}`);
}

export function addCompany(companyCode, watchlistId) {
  return apiClient(`${BASE}/addCompany`, {
    method: 'POST',
    body: JSON.stringify({ companyCode, userWatchlistId: watchlistId ?? null }),
  });
}

export function updateCompany(oldCode, newCode, watchlistId) {
  const qs = watchlistId ? `?watchlistId=${watchlistId}` : '';
  return apiClient(`${BASE}/updateCompany/${encodeURIComponent(oldCode)}${qs}`, {
    method: 'PUT',
    body: JSON.stringify({ companyCode: newCode }),
  });
}

export function deleteCompany(companyCode, watchlistId) {
  const qs = watchlistId ? `?watchlistId=${watchlistId}` : '';
  return apiClient(`${BASE}/deleteCompany/${encodeURIComponent(companyCode)}${qs}`, {
    method: 'DELETE',
  });
}

export function importCompanies(companyCodes, mode = 'SYMBOL', watchlistId) {
  return apiClient(`${BASE}/import`, {
    method: 'POST',
    body: JSON.stringify({ companyCodes, mode, userWatchlistId: watchlistId ?? null }),
  });
}
