const BASE = '/api/nse-code';

export async function fetchSectorTabs() {
  const res = await fetch(`${BASE}/sectors`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json(); // [{ displayName, newsKeyword }, ...]
}

export async function fetchCompanySector(symbol) {
  const res = await fetch(`${BASE}/sectors/company/${encodeURIComponent(symbol)}`);
  if (!res.ok) return null;
  return res.json(); // { symbol, displayName, industry, newsKeyword }
}
