const BASE = '/api/global-watchlist';

export async function fetchGlobalIndices() {
  const res = await fetch(`${BASE}/global-indices`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchDomesticIndexCompanies(indexKey) {
  const res = await fetch(`${BASE}/domestic-indices/${encodeURIComponent(indexKey)}/companies`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchSectorIndices() {
  const res = await fetch(`${BASE}/sector-indices`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchSectorCompanies(sectorKey) {
  const res = await fetch(`${BASE}/sector-indices/${encodeURIComponent(sectorKey)}/companies`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchCompanyDetail(symbol) {
  const res = await fetch(`${BASE}/company/${encodeURIComponent(symbol)}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

const FUND_BASE = '/api/fundamentals';

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

export async function fetchCompanyMemberships(symbol) {
  const res = await fetch(`/api/global-watchlist/company/${encodeURIComponent(symbol)}/memberships`);
  if (!res.ok) return null;
  return res.json();
}
