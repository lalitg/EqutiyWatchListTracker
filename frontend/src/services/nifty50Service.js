const GLOBAL_WATCHLIST_BASE = '/api/global-watchlist';

export async function fetchNifty50() {
  const response = await fetch(`${GLOBAL_WATCHLIST_BASE}/nifty50`);
  if (!response.ok) {
    throw new Error(`Failed to fetch Nifty 50 data: HTTP ${response.status}`);
  }
  return response.json();
}
