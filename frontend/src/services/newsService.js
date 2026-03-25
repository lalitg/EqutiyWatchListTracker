export async function fetchNews(symbol) {
  const response = await fetch(`/api/news?key=${encodeURIComponent(symbol)}`);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

export async function notifySymbolAdded(symbol) {
  try {
    await fetch('/api/internal/watchlist/added', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ symbol }),
    });
  } catch {
    // fire-and-forget — non-critical
  }
}
