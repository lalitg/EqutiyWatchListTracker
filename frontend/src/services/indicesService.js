import { apiFetch } from './apiClient';

const NSE_CODE_BASE = '/api/nse-code';
const GLOBAL_BASE   = '/api/global-watchlist';
const FUND_BASE     = '/api/fundamentals';

export async function fetchGlobalIndices() {
  return apiFetch(`${GLOBAL_BASE}/global-indices`);
}

export async function fetchIndices() {
  // [{ indexKey, displayName, companyCount }, ...]
  return apiFetch(`${NSE_CODE_BASE}/indices`);
}

/**
 * The companies inside one index.
 *
 * Part of the Basic plan — the index grid itself is free, the drill-down is not — so this call
 * has to carry the reader's token.
 */
export async function fetchIndexCompanies(indexKey) {
  // [{ symbol, companyName, isin, industry }, ...]
  return apiFetch(`${NSE_CODE_BASE}/indices/${encodeURIComponent(indexKey)}/companies`);
}

export async function fetchCompanyIndices(symbol) {
  try {
    // [{ indexKey, displayName }, ...]
    return await apiFetch(`${NSE_CODE_BASE}/indices/company/${encodeURIComponent(symbol)}`);
  } catch {
    // The chips under a company name are decoration; a company page without them is still a
    // company page.
    return [];
  }
}

export async function fetchCompanyDetail(symbol) {
  return apiFetch(`${GLOBAL_BASE}/company/${encodeURIComponent(symbol)}`);
}

export async function fetchQuarterlyResults(symbol) {
  return apiFetch(`${FUND_BASE}/${encodeURIComponent(symbol)}/quarterly-results`);
}

export async function fetchBalanceSheet(symbol) {
  return apiFetch(`${FUND_BASE}/${encodeURIComponent(symbol)}/balance-sheet`);
}

export async function fetchPeSnapshot(symbol) {
  try {
    return await apiFetch(`${FUND_BASE}/${encodeURIComponent(symbol)}/pe`);
  } catch {
    // No P/E on record is the common case, not a fault.
    return null;
  }
}

const NEWS_BASE = '/api/news';

export async function fetchMergedNews(keys, page = 0, size = 20) {
  const keysParam = Array.isArray(keys) ? keys.join(',') : keys;
  return apiFetch(
    `${NEWS_BASE}/merged?keys=${encodeURIComponent(keysParam)}&page=${page}&size=${size}`
  );
}
