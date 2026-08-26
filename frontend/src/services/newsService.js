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

/**
 * Fetches current sentiment for many symbols in a single request.
 *
 * Table views (watchlist, index companies, sector companies) render dozens of
 * companies at once. Calling fetchNews per row would mean one request and one full
 * news payload per company; this endpoint returns only a score, a label and a
 * contributing-article count, resolved server-side in one database query.
 *
 * Every requested symbol comes back in the response — ones with no scored news
 * arrive as { score: null, label: 'NO_DATA', articleCount: 0 } rather than being
 * omitted, so callers never have to distinguish a missing key from a real neutral.
 *
 * Failures resolve to an empty object rather than rejecting: sentiment is a
 * supplementary column, and losing it must never blank out a company table.
 *
 * @param {string[]} symbols company symbols to look up
 * @returns {Promise<Object>} map of symbol -> { score, label, articleCount }
 */
export async function fetchSentiments(symbols) {
  if (!symbols || symbols.length === 0) return {};

  try {
    const keys = [...new Set(symbols.filter(Boolean))].join(',');
    const response = await fetch(`/api/news/sentiment?keys=${encodeURIComponent(keys)}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } catch {
    return {};
  }
}
