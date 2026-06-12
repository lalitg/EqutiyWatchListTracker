import React, { useEffect, useCallback } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import { useMarket } from '../context/MarketContext';

const REGIONS = ['India', 'Global', 'US', 'Asia', 'Europe'];
const AUTO_REFRESH_MS = 15 * 60 * 1000;

const GlobalMarketPage = () => {
  const {
    globalCache, globalLoading, globalError,
    selectedRegion, setRegion, fetchGlobal, refreshGlobal,
    STALE_FOCUS_MS,
  } = useMarket();

  const currentData = globalCache[selectedRegion];

  useEffect(() => {
    fetchGlobal(selectedRegion);
  }, [selectedRegion, fetchGlobal]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (!globalLoading) refreshGlobal(selectedRegion);
    }, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [selectedRegion, globalLoading, refreshGlobal]);

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
        <button className="btn btn-secondary" onClick={() => refreshGlobal(selectedRegion)}>
          Refresh
        </button>
      </div>

      <SectorSelector options={REGIONS} selected={selectedRegion} onSelect={setRegion} />

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
