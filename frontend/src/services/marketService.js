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
  India: [
    'RBI monetary policy',
    'repo rate',
    'India Budget',
    'Union Budget',
    'Budget 2026',
    'India fiscal deficit',
    'GST changes',
    'inflation India',
    'CPI WPI India',
    'IIP data',
    'India trade deficit',
    'India economic survey',
    'FII investment',
    'Rupee vs Dollar',
    'Nifty 50',
    'Sensex',
    'Bank Nifty',
    'crude oil India',
    'India election results',
    'India war',
    'India recession',
    'Indian natural disaster',
    'Indian terrorist attack',
    'India GDP',
  ],
  Global: [
    'GDP growth recession',
    'Interest rates',
    'Bond yields US 10Y yield',
    'Quantitative tightening',
    'Quantitative easing',
    'crude oil',
    'gold price',
    'silver price',
    'OPEC oil',
    'war commodity prices',
    'Trade war',
    'Military strike',
    'Border tension',
    'global sanctions',
    'Terrorist attack',
    'Political crisis',
    'IMF',
    'world bank',
    'global trade',
    'Forex Reserves',
    'China tariffs',
  ],
  US: [
    'Nasdaq',
    'Dow Jones',
    'Global liquidity',
    'Federal Reserve Economic Data',
    'US economy',
    'Fed rate decision',
    'FOMC meeting',
    'US inflation CPI PPI',
    'US job data Nonfarm payrolls',
    'US Federal Reserve',
    'Dollar index DXY',
    'US recession',
    'US war',
    'US technology sector',
    'US innovation',
  ],
  Asia: [
    'BOJ Policy',
    'Taiwan economy',
    'Hong Kong economy',
    'Asia markets',
    'China economy',
    'Japan economy',
    'Singapore economy',
  ],
  Europe: [
    'European Central Bank Policy',
    'European innovation',
    'European technology',
    'euro currency',
    'Europe Economy',
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
export async function fetchGlobalInsights(region = 'India') {
  const keywords = REGION_KEYWORD_MAP[region] || [];
  console.log(`[marketService] Fetching global insights for region '${region}' — ${keywords.length} keywords`);

  const [newsResults, eventsResults] = await Promise.all([
    Promise.all(
      keywords.map(kw =>
        fetchNews(kw).catch(() => ({ news: [] }))
      )
    ),
    Promise.all(
      keywords.map(kw =>
        fetchEvents(kw).catch(() => ({ events: [] }))
      )
    ),
  ]);

  const mergedNews = newsResults.flatMap(r => r.news ?? []);
  const dedupNews  = [...new Map(mergedNews.filter(n => n.link).map(n => [n.link, n])).values()];
  dedupNews.sort((a, b) => new Date(b.date) - new Date(a.date));

  const mergedEvents = eventsResults.flatMap(r => r.events ?? []);
  const dedupEvents  = [...new Map(mergedEvents.filter(e => e.symbol).map(e => [e.symbol + e.date, e])).values()];
  dedupEvents.sort((a, b) => new Date(b.date) - new Date(a.date));

  console.log(`[marketService] Region '${region}' — news: ${dedupNews.length}, events: ${dedupEvents.length}`);
  return { news: dedupNews, events: dedupEvents };
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
