import { fetchNews } from './newsService';
import { fetchEvents } from './eventsService';

const SECTOR_KEYWORD_MAP = {
  All: 'Nifty 50',
  IT: 'IT',
  Banking: 'Banking',
  Pharma: 'Pharma',
  Auto: 'Auto',
  FMCG: 'FMCG',
};

export async function fetchGlobalInsights(region = 'US') {
  const [newsData, eventsData] = await Promise.all([
    fetchNews(region).catch(() => ({ news: [] })),
    fetchEvents(region).catch(() => ({ events: [] })),
  ]);
  return {
    news: newsData.news ?? [],
    events: eventsData.events ?? [],
  };
}

export async function fetchDomesticInsights(sector = 'All') {
  const keyword = SECTOR_KEYWORD_MAP[sector] || sector;
  const [newsData, eventsData] = await Promise.all([
    fetchNews(keyword).catch(() => ({ news: [] })),
    fetchEvents(keyword).catch(() => ({ events: [] })),
  ]);
  return {
    news: newsData.news ?? [],
    events: eventsData.events ?? [],
  };
}
