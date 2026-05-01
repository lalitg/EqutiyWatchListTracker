const BASE = '/api/global-watchlist';

export async function fetchGlobalIndices() {
  const res = await fetch(`${BASE}/global-indices`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function fetchDomesticIndices() {
  const res = await fetch(`${BASE}/domestic-indices`);
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
