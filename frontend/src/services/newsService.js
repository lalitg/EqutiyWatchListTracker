import { apiFetch } from './apiClient';

export async function fetchNews(symbol) {
  return apiFetch(`/api/news?key=${encodeURIComponent(symbol)}`);
}

export async function notifySymbolAdded(symbol) {
  try {
    await apiFetch('/api/internal/watchlist/added', {
      method: 'POST',
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
 * supplementary column, and losing it must never blank out a company table. That
 * covers the paywall too — a reader on the free plan is refused here and simply
 * sees a table without the sentiment column, which is the intended free view.
 *
 * @param {string[]} symbols company symbols to look up
 * @returns {Promise<Object>} map of symbol -> { score, label, articleCount }
 */
export async function fetchSentiments(symbols) {
  if (!symbols || symbols.length === 0) return {};

  try {
    const keys = [...new Set(symbols.filter(Boolean))].join(',');
    return await apiFetch(`/api/news/sentiment?keys=${encodeURIComponent(keys)}`);
  } catch {
    return {};
  }
}

/**
 * Days the Extremes Single Day tab can show, newest first.
 *
 * Served by the backend rather than generated in the browser so both sides agree on which day is
 * "today": the boards are bucketed in Indian market time, and a viewer in another timezone would
 * otherwise ask for a day the server does not consider current.
 *
 * Rejects rather than resolving to an empty list. Swallowing the failure left the page with no
 * day to select, so it never asked for a board and sat on "Loading…" for ever: a refusal and a
 * quiet weekend looked exactly alike.
 *
 * @returns {Promise<Array<{day: string, label: string, weekday: string}>>}
 */
export async function fetchExtremesDays() {
  return apiFetch('/api/news/extremes/days');
}

/**
 * Top positives and top negatives for one calendar day.
 *
 * Rejects on failure, for the same reason as the day list: a null board renders as "no news
 * published", which is a claim about the market rather than about the request.
 *
 * @param {string} day ISO date; omit for today
 */
export async function fetchDailyExtremes(day) {
  const qs = day ? `?day=${encodeURIComponent(day)}` : '';
  return apiFetch(`/api/news/extremes/daily${qs}`);
}

/**
 * Top positives and top negatives for one cumulative range.
 *
 * @param {string} range WEEK_1 | WEEK_2 | MONTH_1 | QUARTER_1
 */
export async function fetchCumulativeExtremes(range) {
  return apiFetch(`/api/news/extremes/cumulative?range=${encodeURIComponent(range)}`);
}
