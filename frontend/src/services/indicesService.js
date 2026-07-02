const NSE_CODE_BASE = '/api/nse-code';
const GLOBAL_BASE   = '/api/global-watchlist';
const FUND_BASE     = '/api/fundamentals';

export async function fetchGlobalIndices() {
  const res = await fetch(`${GLOBAL_BASE}/global-indices`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchIndices() {
  const res = await fetch(`${NSE_CODE_BASE}/indices`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json(); // [{ indexKey, displayName, companyCount }, ...]
}

export async function fetchIndexCompanies(indexKey) {
  const res = await fetch(`${NSE_CODE_BASE}/indices/${encodeURIComponent(indexKey)}/companies`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json(); // [{ symbol, companyName, isin, industry }, ...]
}

export async function fetchCompanyIndices(symbol) {
  const res = await fetch(`${NSE_CODE_BASE}/indices/company/${encodeURIComponent(symbol)}`);
  if (!res.ok) return [];
  return res.json(); // [{ indexKey, displayName }, ...]
}

export async function fetchCompanyDetail(symbol) {
  const res = await fetch(`${GLOBAL_BASE}/company/${encodeURIComponent(symbol)}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchQuarterlyResults(symbol) {
  const res = await fetch(`${FUND_BASE}/${encodeURIComponent(symbol)}/quarterly-results`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchBalanceSheet(symbol) {
  const res = await fetch(`${FUND_BASE}/${encodeURIComponent(symbol)}/balance-sheet`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchPeSnapshot(symbol) {
  const res = await fetch(`${FUND_BASE}/${encodeURIComponent(symbol)}/pe`);
  if (!res.ok) return null;
  return res.json();
}

const NEWS_BASE = '/api/news';

export async function fetchMergedNews(keys, page = 0, size = 20) {
  const keysParam = Array.isArray(keys) ? keys.join(',') : keys;
  const res = await fetch(
    `${NEWS_BASE}/merged?keys=${encodeURIComponent(keysParam)}&page=${page}&size=${size}`
  );
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
