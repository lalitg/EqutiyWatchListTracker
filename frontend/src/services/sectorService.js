import { apiFetch } from './apiClient';

const BASE = '/api/nse-code';

export async function fetchSectorTabs() {
  // [{ displayName, newsKeyword }, ...]
  return apiFetch(`${BASE}/sectors`);
}

export async function fetchCompanySectors(symbol) {
  try {
    // [{ symbol, displayName, industry, newsKeyword }, ...]
    return await apiFetch(`${BASE}/sectors/company/${encodeURIComponent(symbol)}`);
  } catch {
    // The sector chip under a company name is decoration; losing it must not fail the page.
    return [];
  }
}

/**
 * The companies inside one sector.
 *
 * Part of the Basic plan — the sector grid itself is free, the drill-down is not — so this call
 * has to carry the reader's token.
 */
export async function fetchSectorCompanies(displayName) {
  // [{ symbol, companyName, isin }, ...]
  return apiFetch(`${BASE}/sectors/${encodeURIComponent(displayName)}/companies`);
}
