const BASE = '/api/nse-code';

export async function fetchSectorTabs() {
  const res = await fetch(`${BASE}/sectors`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json(); // [{ displayName, newsKeyword }, ...]
}

export async function fetchCompanySectors(symbol) {
  const res = await fetch(`${BASE}/sectors/company/${encodeURIComponent(symbol)}`);
  if (!res.ok) return [];
  return res.json(); // [{ symbol, displayName, industry, newsKeyword }, ...]
}
