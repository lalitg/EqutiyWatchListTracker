import { useEffect, useState } from 'react';
import { fetchSentiments } from '../services/newsService';

/**
 * Loads news sentiment for a list of company symbols.
 *
 * Each entry is `{ latest, quarter }`:
 *   latest  — `{ score, label, publishedAt }` for the single most recent article
 *   quarter — `{ score, label, articleCount }` averaged over the last 90 days
 *
 * Two readings rather than one because they answer different questions and routinely disagree:
 * the newest headline against the backdrop it landed against. Both arrive in the same request, so
 * the second column costs nothing extra.
 *
 * Used by every table that shows many companies at once — the watchlist, the Nifty
 * index company table and the sector company table. All three need identical
 * behaviour, so the batching and failure handling live here rather than being
 * repeated three times.
 *
 * The symbol list is joined into a string for the dependency array. Passing the array
 * itself would re-run this effect on every render, because a new array identity is
 * created each time the parent re-renders even when the contents are unchanged — which
 * would mean a fresh HTTP request per render.
 *
 * Sentiment is supplementary: a failed request resolves to an empty map, so the
 * sentiment column simply reads "No news" while prices and volumes render normally.
 *
 * @param {string[]} symbols company symbols to look up
 * @returns {{ sentiments: Object, loading: boolean }}
 */
export function useSentiments(symbols) {
  const [sentiments, setSentiments] = useState({});
  const [loading, setLoading] = useState(false);

  const key = (symbols || []).filter(Boolean).join(',');

  useEffect(() => {
    if (!key) {
      setSentiments({});
      return;
    }

    let cancelled = false;
    setLoading(true);

    fetchSentiments(key.split(','))
      .then(result => {
        // Guard against a slow response for a previous symbol set landing after the
        // user has already navigated to a different index or sector.
        if (!cancelled) setSentiments(result || {});
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [key]);

  return { sentiments, loading };
}

export default useSentiments;
