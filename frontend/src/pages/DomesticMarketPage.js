import React, { useEffect, useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import DomesticIndicesGrid from '../components/market/DomesticIndicesGrid';
import { useMarket } from '../context/MarketContext';
import { fetchSectorIndices } from '../services/indicesService';

const AUTO_REFRESH_MS = 15 * 60 * 1000;
const PRICE_REFRESH_MS = 5 * 60 * 1000;

const DomesticMarketPage = () => {
  const {
    domestic, domesticLoading, domesticError,
    fetchDomestic, refreshDomestic,
    STALE_FOCUS_MS,
  } = useMarket();

  const navigate = useNavigate();
  const [sectorTabs, setSectorTabs] = useState([]);
  const [priceRefreshKey, setPriceRefreshKey] = useState(0);

  useEffect(() => { fetchDomestic('All'); }, [fetchDomestic]);

  useEffect(() => {
    fetchSectorIndices().then(setSectorTabs).catch(() => {});
  }, []);

  // News/events auto-refresh every 15 min
  useEffect(() => {
    const interval = setInterval(() => {
      if (!domesticLoading) refreshDomestic('All');
    }, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [domesticLoading, refreshDomestic]);

  // Price auto-refresh every 5 min
  useEffect(() => {
    const interval = setInterval(() => {
      setPriceRefreshKey(k => k + 1);
      fetchSectorIndices().then(setSectorTabs).catch(() => {});
    }, PRICE_REFRESH_MS);
    return () => clearInterval(interval);
  }, []);

  const handleVisibilityChange = useCallback(() => {
    if (document.visibilityState === 'visible') {
      const isStale = !domestic?.fetchedAt || (Date.now() - domestic.fetchedAt) > STALE_FOCUS_MS;
      if (isStale && !domesticLoading) refreshDomestic('All');
    }
  }, [domestic, domesticLoading, refreshDomestic, STALE_FOCUS_MS]);

  useEffect(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [handleVisibilityChange]);

  const getLastUpdatedLabel = () => {
    if (!domestic?.fetchedAt) return null;
    const diffMin = Math.floor((Date.now() - domestic.fetchedAt) / 60000);
    if (diffMin < 1) return 'Updated just now';
    return `Updated ${diffMin} min ago`;
  };

  const sectorLabels = {};
  sectorTabs.forEach(s => {
    const pct = s.changePercent != null ? Number(s.changePercent) : null;
    if (pct != null) {
      const arrow = pct > 0 ? '▲' : pct < 0 ? '▼' : '';
      const cls = pct > 0 ? 'sector-tab-pct gain' : pct < 0 ? 'sector-tab-pct loss' : 'sector-tab-pct';
      sectorLabels[s.nseParam] = (
        <span>
          {s.displayName}
          <span className={cls}> {arrow}{Math.abs(pct).toFixed(2)}%</span>
        </span>
      );
    } else {
      sectorLabels[s.nseParam] = s.displayName;
    }
  });

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Domestic Market Insights</h1>
        <button className="btn btn-secondary" onClick={() => {
          fetchDomestic('All');
          setPriceRefreshKey(k => k + 1);
          fetchSectorIndices().then(setSectorTabs).catch(() => {});
        }}>Refresh</button>
      </div>

      {sectorTabs.length > 0 && (
        <SectorSelector
          options={sectorTabs.map(s => s.nseParam)}
          selected={null}
          onSelect={(nseParam) => navigate(`/market/domestic/sector/${encodeURIComponent(nseParam)}`)}
          labels={sectorLabels}
        />
      )}

      <DomesticIndicesGrid refreshKey={priceRefreshKey} />

      {domesticLoading && (
        <div className="page-loading"><p>Loading domestic insights...</p></div>
      )}

      {domesticError && (
        <div className="page-error">
          <p>{domesticError}</p>
          <button onClick={() => fetchDomestic('All')} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {!domesticLoading && !domesticError && domestic && (
        <div className="market-content">
          {getLastUpdatedLabel() && (
            <p className="last-updated-label">{getLastUpdatedLabel()}</p>
          )}
          <NewsList news={domestic.news?.slice(0, 5)} />
          <EventsList events={domestic.events} />
        </div>
      )}
    </div>
  );
};

export default DomesticMarketPage;
