const CACHE_PREFIX = 'nse_holidays_';
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;

function cacheKey(year) {
  return `${CACHE_PREFIX}${year}`;
}

function loadFromCache(year) {
  try {
    const raw = localStorage.getItem(cacheKey(year));
    if (!raw) return null;
    const { data, savedAt } = JSON.parse(raw);
    if (Date.now() - savedAt > CACHE_TTL_MS) return null;
    return data;
  } catch {
    return null;
  }
}

function saveToCache(year, data) {
  if (!data || (Array.isArray(data) && data.length === 0)) return; // never cache empty results
  try {
    localStorage.setItem(cacheKey(year), JSON.stringify({ data, savedAt: Date.now() }));
  } catch {
    // localStorage unavailable (private mode, quota exceeded) — ignore
  }
}

export async function fetchHolidays(year) {
  const cached = loadFromCache(year);
  if (cached) return cached;
  const res = await fetch(`/api/v1/nse/holidays?year=${year}`);
  if (!res.ok) throw new Error(`Failed to fetch holidays: ${res.status}`);
  const data = await res.json();
  saveToCache(year, data);
  return data;
}

/** Synchronous read from localStorage — returns null if not cached or expired. */
export function getCachedHolidays(year) {
  return loadFromCache(year);
}
