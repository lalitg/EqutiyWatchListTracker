import React, { useEffect, useCallback, useState } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import GlobalIndicesTable from '../components/market/GlobalIndicesTable';
import { useMarket } from '../context/MarketContext';

/**
 * Available region tabs on the Global Market page.
 *
 * Each tab maps to a list of curated keywords in marketService.js.
 * - US, Europe, Asia — regional economy and market news
 * - India — macro-level India news (RBI, Budget, FII, indices, etc.)
 * - Global — worldwide impact news (macro, commodities, geopolitical)
 *
 * Note: the Domestic page covers India at sector level (IT, Banking, etc.).
 * The India tab here covers the macro/economy level.
 */
const REGIONS = ['US', 'Europe', 'Asia', 'India', 'Global'];

/**
 * GlobalMarketPage displays aggregated news for global market regions.
 *
 * Each tab fetches news from multiple curated keywords in parallel (defined
 * in marketService.js), merges them, deduplicates by article link, and sorts
 * newest-first. Data per tab is cached in MarketContext for the session —
 * switching tabs does not re-fetch if data is already loaded.
 *
 * The Refresh button force-fetches fresh data for the active tab only,
 * bypassing the cache.
 */
const AUTO_REFRESH_MS = 15 * 60 * 1000;
const PRICE_REFRESH_MS = 5 * 60 * 1000;

const GlobalMarketPage = () => {
  const {
    globalCache, globalLoading, globalError,
    selectedRegion, setRegion, fetchGlobal, refreshGlobal,
    STALE_FOCUS_MS,
  } = useMarket();

  const [priceRefreshKey, setPriceRefreshKey] = useState(0);

  /** Data for the currently active region tab, or null if not yet loaded. */
  const currentData = globalCache[selectedRegion];

  /**
   * Triggers a fetch whenever the selected region changes.
   * fetchGlobal checks cache freshness (TTL 15 min) — re-fetches if stale.
   */
  useEffect(() => {
    fetchGlobal(selectedRegion);
  }, [selectedRegion, fetchGlobal]);

  /**
   * Auto-refresh: silently re-fetches every 15 minutes.
   * Skips if a fetch is already in progress to prevent concurrent calls.
   */
  // News/events auto-refresh every 15 min
  useEffect(() => {
    const interval = setInterval(() => {
      if (!globalLoading) refreshGlobal(selectedRegion);
    }, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [selectedRegion, globalLoading, refreshGlobal]);

  // Price auto-refresh every 5 min
  useEffect(() => {
    const interval = setInterval(() => setPriceRefreshKey(k => k + 1), PRICE_REFRESH_MS);
    return () => clearInterval(interval);
  }, []);

  /**
   * Tab-focus refresh: re-fetches when the user returns to the browser tab
   * if the cached data for the active region is older than STALE_FOCUS_MS (5 min).
   */
  const handleVisibilityChange = useCallback(() => {
    if (document.visibilityState === 'visible') {
      const cached = globalCache[selectedRegion];
      const isStale = !cached || (Date.now() - cached.fetchedAt) > STALE_FOCUS_MS;
      if (isStale && !globalLoading) {
        console.log(`[GlobalMarketPage] Tab focused — stale data, refreshing region '${selectedRegion}'`);
        refreshGlobal(selectedRegion);
      }
    }
  }, [selectedRegion, globalCache, globalLoading, refreshGlobal, STALE_FOCUS_MS]);

  useEffect(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [handleVisibilityChange]);

  const handleRegionChange = (region) => {
    setRegion(region);
  };

  /**
   * Formats the fetchedAt timestamp into a human-readable "X min ago" string.
   * Returns null if no timestamp is available yet.
   */
  const getLastUpdatedLabel = () => {
    if (!currentData?.fetchedAt) return null;
    const diffMs = Date.now() - currentData.fetchedAt;
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'Updated just now';
    return `Updated ${diffMin} min ago`;
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Global Market Insights</h1>
        <button className="btn btn-secondary" onClick={() => {
          refreshGlobal(selectedRegion);
          setPriceRefreshKey(k => k + 1);
        }}>
          Refresh
        </button>
      </div>

      <SectorSelector options={REGIONS} selected={selectedRegion} onSelect={handleRegionChange} />

      {globalLoading && (
        <div className="page-loading"><p>Loading global insights...</p></div>
      )}

      {globalError && (
        <div className="page-error">
          <p>{globalError}</p>
          <button
            onClick={() => refreshGlobal(selectedRegion)}
            className="btn btn-primary"
            style={{ marginTop: 16 }}
          >
            Retry
          </button>
        </div>
      )}

      <GlobalIndicesTable refreshKey={priceRefreshKey} />

      {!globalLoading && !globalError && currentData && (
        <div className="market-content">
          {getLastUpdatedLabel() && (
            <p className="last-updated-label">{getLastUpdatedLabel()}</p>
          )}
          <NewsList news={currentData.news?.slice(0, 5)} />
          <EventsList events={currentData.events ?? []} />
        </div>
      )}
    </div>
  );
};

export default GlobalMarketPage;
