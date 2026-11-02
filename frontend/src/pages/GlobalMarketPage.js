import React, { useEffect, useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import GlobalIndicesTable from '../components/market/GlobalIndicesTable';
import { useMarket } from '../context/MarketContext';
import { fetchMergedNews } from '../services/indicesService';
import { REGION_KEYWORD_MAP } from '../services/marketService';
import { REGION_DESCRIPTIONS } from '../constants/marketDescriptions';
import Locked from '../components/shared/Locked';
import { useEntitlements, FEATURES } from '../context/EntitlementsContext';

const REGIONS = ['Global', 'US', 'Asia', 'Europe', 'Commodities'];
const AUTO_REFRESH_MS  = 15 * 60 * 1000;
const PRICE_REFRESH_MS = 30 * 60 * 1000;
const NEWS_PAGE_SIZE   = 20;

const GlobalMarketPage = () => {
  const { can } = useEntitlements();

  // The Global tab stays open to everyone - a market page with nothing on it sells nothing. The
  // four regional breakdowns are what Basic adds.
  const canAllRegions = can(FEATURES.GLOBAL_ALL_REGIONS);

  const {
    globalCache, globalLoading,
    selectedRegion, setRegion, fetchGlobal, refreshGlobal,
    STALE_FOCUS_MS,
  } = useMarket();

  const [priceRefreshKey, setPriceRefreshKey] = useState(0);
  const currentData = globalCache[selectedRegion];

  // Paginated news local state
  const [newsItems, setNewsItems]           = useState([]);
  const [newsPage, setNewsPage]             = useState(0);
  const [newsTotalPages, setNewsTotalPages] = useState(1);
  const [newsLoading, setNewsLoading]       = useState(false);
  const [newsError, setNewsError]           = useState(null);
  const [retryKey, setRetryKey]             = useState(0);

  // Fetch events via context whenever region changes
  useEffect(() => {
    fetchGlobal(selectedRegion);
  }, [selectedRegion, fetchGlobal]);

  // Reset news page when region changes
  useEffect(() => {
    setNewsPage(0);
  }, [selectedRegion]);

  // Fetch paginated news whenever region, page, or retryKey changes
  useEffect(() => {
    let cancelled = false;
    const keywords = REGION_KEYWORD_MAP[selectedRegion] || [];
    if (keywords.length === 0) return;
    setNewsLoading(true);
    setNewsError(null);
    fetchMergedNews(keywords, newsPage, NEWS_PAGE_SIZE)
      .then(data => {
        if (!cancelled) {
          setNewsItems(data.content ?? []);
          setNewsTotalPages(data.totalPages ?? 1);
        }
      })
      .catch(e => { if (!cancelled) setNewsError(e.message); })
      .finally(() => { if (!cancelled) setNewsLoading(false); });
    return () => { cancelled = true; };
  }, [selectedRegion, newsPage, retryKey]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (!globalLoading) refreshGlobal(selectedRegion);
    }, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [selectedRegion, globalLoading, refreshGlobal]);

  useEffect(() => {
    const interval = setInterval(() => setPriceRefreshKey(k => k + 1), PRICE_REFRESH_MS);
    return () => clearInterval(interval);
  }, []);

  const handleVisibilityChange = useCallback(() => {
    if (document.visibilityState === 'visible') {
      const cached = globalCache[selectedRegion];
      const isStale = !cached || (Date.now() - cached.fetchedAt) > STALE_FOCUS_MS;
      if (isStale && !globalLoading) refreshGlobal(selectedRegion);
    }
  }, [selectedRegion, globalCache, globalLoading, refreshGlobal, STALE_FOCUS_MS]);

  useEffect(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [handleVisibilityChange]);

  const handleRefresh = () => {
    refreshGlobal(selectedRegion);
    setPriceRefreshKey(k => k + 1);
    setNewsPage(0);
    setRetryKey(k => k + 1);
  };

  const getLastUpdatedLabel = () => {
    if (!currentData?.fetchedAt) return null;
    const diffMin = Math.floor((Date.now() - currentData.fetchedAt) / 60000);
    if (diffMin < 1) return 'Updated just now';
    return `Updated ${diffMin} min ago`;
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Global Market Insights</h1>
        <button className="btn btn-secondary" onClick={handleRefresh}>Refresh</button>
      </div>

      <SectorSelector options={REGIONS} selected={selectedRegion} onSelect={setRegion} tooltips={REGION_DESCRIPTIONS} />

      {!canAllRegions && selectedRegion === 'Global' && (
        // Said once, on the open tab, so a free reader learns what the other four tabs are before
        // clicking one and meeting a blur with no explanation of why it is there.
        <p className="nf-locked-inline" role="note">
          <span aria-hidden="true">&#128274; </span>
          US, Asia, Europe and Commodities are part of the paid plans.{' '}
          <Link to="/subscribe?feature=globalAllRegions">See plans</Link>
        </p>
      )}

      <GlobalIndicesTable refreshKey={priceRefreshKey} region={selectedRegion} />

      {newsLoading && (
        <div className="page-loading"><p>Loading market news...</p></div>
      )}

      {newsError && !newsLoading && (
        <div className="page-error">
          <p>{newsError}</p>
          <button
            onClick={() => setRetryKey(k => k + 1)}
            className="btn btn-primary"
            style={{ marginTop: 16 }}
          >
            Retry
          </button>
        </div>
      )}

      {!newsLoading && !newsError && (
        <Locked
          /* Null on the Global tab, which is free: see the note above the region selector. */
          feature={selectedRegion === 'Global' ? null : FEATURES.GLOBAL_ALL_REGIONS}
          title={`${selectedRegion} market news`}
          note="US, Asian, European and commodity news, each with its own indices and events.
                Included from the Basic plan upwards - the Global view stays free."
        >
        <div className="market-content">
          {getLastUpdatedLabel() && (
            <p className="last-updated-label">{getLastUpdatedLabel()}</p>
          )}
          <NewsList news={newsItems} />
          {newsTotalPages > 1 && (
            <div className="pagination">
              <button
                className="pagination-btn"
                disabled={newsPage === 0}
                onClick={() => setNewsPage(p => p - 1)}
              >
                Prev
              </button>
              <span className="page-indicator">Page {newsPage + 1} of {newsTotalPages}</span>
              <button
                className="pagination-btn"
                disabled={newsPage >= newsTotalPages - 1}
                onClick={() => setNewsPage(p => p + 1)}
              >
                Next
              </button>
            </div>
          )}
          <EventsList events={currentData?.events ?? []} />
        </div>
        </Locked>
      )}
    </div>
  );
};

export default GlobalMarketPage;
