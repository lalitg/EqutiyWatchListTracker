import { fetchNews } from './newsService';
import { fetchEvents } from './eventsService';

/**
 * Maps each domestic sector tab to its corresponding news service keyword.
 *
 * 'All' maps to 'Nifty 50' since that represents the broad Indian market.
 * Other sectors map to their own name as stored in the sector_companies table
 * in the news service database.
 */
const SECTOR_KEYWORD_MAP = {
  All:     'Nifty 50',
  IT:      'IT',
  Banking: 'Banking',
  Pharma:  'Pharma',
  Auto:    'Auto',
  FMCG:   'FMCG',
};

/**
 * Maps each global region tab to the list of keywords the news scheduler
 * fetches and caches. Multiple keywords per region are fetched in parallel,
 * merged, deduplicated by article link, and sorted newest-first.
 *
 * Keywords present in the news service's keywords.txt are served from the
 * scheduler's pre-fetched cache (fast). Keywords not in keywords.txt
 * (e.g. 'Japan economy', 'India election results') are fetched on-demand
 * by the news service and cached in its DB for subsequent requests.
 */
const REGION_KEYWORD_MAP = {
  US: [
    'US economy',
    'Fed rate hike or cut',
    'FOMC meeting',
    'US inflation (CPI, PPI)',
    'US job data (Nonfarm payrolls)',
    'US Federal Reserve',
    'Dollar index (DXY)',
  ],
  Europe: [
    'Europe economy',
  ],
  Asia: [
    'Asia markets',
    'China economy',
    'Japan economy',
    'Singapore economy',
  ],
  India: [
    'RBI monetary policy',
    'RBI policy / repo rate',
    'Budget',
    'Union Budget',
    'Budget 2026',
    'Fiscal deficit',
    'GST changes',
    'inflation India',
    'Inflation (CPI/WPI India)',
    'IIP data',
    'Trade deficit',
    'Economic survey',
    'FII investment',
    'Rupee vs Dollar',
    'Nifty 50',
    'Sensex',
    'Bank Nifty',
    'crude oil India',
    'India election results',
  ],
  Global: [
    'GDP growth / recession',
    'Interest rates',
    'Bond yields (US 10Y yield)',
    'Quantitative tightening (QT)',
    'Quantitative easing (QE)',
    'crude oil',
    'gold price',
    'silver price',
    'OPEC oil',
    'war commodity prices',
    'War',
    'Trade war',
    'Military strike',
    'Border tension',
    'Sanctions',
    'Terrorist attack',
    'Political crisis',
  ],
};

/**
 * Fetches global market news for the given region by calling the news service
 * for each keyword in the region's keyword list in parallel.
 *
 * Results from all keywords are merged, deduplicated by article link,
 * and sorted by date descending (newest first). Individual keyword failures
 * are silently skipped so a single bad keyword does not break the entire tab.
 *
 * @param {string} region - The selected region tab ('US', 'Europe', 'Asia', 'India', 'Global').
 * @returns {Promise<{ news: Array }>} Combined and sorted news array for the region.
 */
export async function fetchGlobalInsights(region = 'US') {
  const keywords = REGION_KEYWORD_MAP[region] || [];
  console.log(`[marketService] Fetching global insights for region '${region}' — ${keywords.length} keywords`);

  const results = await Promise.all(
    keywords.map(kw =>
      fetchNews(kw).catch(() => {
        console.warn(`[marketService] Failed to fetch news for keyword '${kw}' — skipping`);
        return { news: [] };
      })
    )
  );

  const merged       = results.flatMap(r => r.news ?? []);
  const deduplicated = [...new Map(merged.filter(n => n.link).map(n => [n.link, n])).values()];
  deduplicated.sort((a, b) => new Date(b.date) - new Date(a.date));

  console.log(`[marketService] Region '${region}' — ${merged.length} raw articles, ${deduplicated.length} after dedup`);
  return { news: deduplicated };
}

/**
 * Fetches domestic market news and events for the given sector.
 *
 * The sector label is mapped to its corresponding news service keyword via
 * SECTOR_KEYWORD_MAP. If the sector is not in the map, it is used as-is
 * (fallback for any dynamically added sectors).
 *
 * @param {string} sector - The selected sector tab ('All', 'IT', 'Banking', etc.).
 * @returns {Promise<{ news: Array, events: Array }>} News and events for the sector.
 */
export async function fetchDomesticInsights(sector = 'All') {
  const keyword = SECTOR_KEYWORD_MAP[sector] || sector;
  console.log(`[marketService] Fetching domestic insights for sector '${sector}' (keyword: '${keyword}')`);

  const [newsData, eventsData] = await Promise.all([
    fetchNews(keyword).catch(() => {
      console.warn(`[marketService] Failed to fetch news for sector '${sector}'`);
      return { news: [] };
    }),
    fetchEvents(keyword).catch(() => {
      console.warn(`[marketService] Failed to fetch events for sector '${sector}'`);
      return { events: [] };
    }),
  ]);

  return {
    news:   newsData.news   ?? [],
    events: eventsData.events ?? [],
  };
}
