import React, { useEffect } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
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
const GlobalMarketPage = () => {
  const {
    globalCache, globalLoading, globalError,
    selectedRegion, setRegion, fetchGlobal, refreshGlobal,
  } = useMarket();

  /** Data for the currently active region tab, or null if not yet loaded. */
  const currentData = globalCache[selectedRegion];

  /**
   * Triggers a fetch whenever the selected region changes.
   * fetchGlobal is a no-op if data for this region is already cached.
   */
  useEffect(() => {
    fetchGlobal(selectedRegion);
  }, [selectedRegion, fetchGlobal]);

  const handleRegionChange = (region) => {
    setRegion(region);
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Global Market Insights</h1>
        <button className="btn btn-secondary" onClick={() => refreshGlobal(selectedRegion)}>
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

      {!globalLoading && !globalError && currentData && (
        <div className="market-content">
          <NewsList news={currentData.news?.slice(0, 5)} />
          <EventsList events={currentData.events ?? []} />
        </div>
      )}
    </div>
  );
};

export default GlobalMarketPage;
