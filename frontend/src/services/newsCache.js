// Module-level LRU cache for sector news pages.
// Persists across component mounts within the same browser session.
// Key: "<keyword>_p<page>" — Value: { data, cachedAt }
// Eviction: oldest entry when capacity is reached (Map preserves insertion order).

const MAX_ENTRIES  = 30; // 10 sectors × ~3 pages each
const CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutes

const cache = new Map();

export function getCachedSectorNews(keyword, page) {
  const key   = `${keyword}_p${page}`;
  const entry = cache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.cachedAt > CACHE_TTL_MS) {
    cache.delete(key);
    return null;
  }
  // Promote to most-recently-used position
  cache.delete(key);
  cache.set(key, entry);
  return entry.data;
}

export function setCachedSectorNews(keyword, page, data) {
  const key = `${keyword}_p${page}`;
  // Evict LRU entry if at capacity and this key is new
  if (cache.size >= MAX_ENTRIES && !cache.has(key)) {
    cache.delete(cache.keys().next().value);
  }
  cache.delete(key); // remove first to update insertion position
  cache.set(key, { data, cachedAt: Date.now() });
}

export function invalidateSectorNews(keyword) {
  for (const key of [...cache.keys()]) {
    if (key.startsWith(`${keyword}_p`)) cache.delete(key);
  }
}
