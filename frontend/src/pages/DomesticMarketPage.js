import React, { useEffect, useCallback, useState } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import { useMarket } from '../context/MarketContext';
import { fetchSectorTabs } from '../services/sectorService';

const AUTO_REFRESH_MS = 15 * 60 * 1000;

const ALL_TAB = { displayName: 'All', newsKeyword: 'Nifty 50' };

const DomesticMarketPage = () => {
  const {
    domestic, domesticLoading, domesticError,
    fetchDomestic, refreshDomestic,
    STALE_FOCUS_MS,
  } = useMarket();

  const [sectors, setSectors] = useState([ALL_TAB]);
  const [selectedSector, setSelectedSector] = useState(ALL_TAB);

  useEffect(() => {
    fetchSectorTabs()
      .then(tabs => setSectors([ALL_TAB, ...tabs]))
      .catch(() => {});
  }, []);

  useEffect(() => {
    fetchDomestic(selectedSector.newsKeyword);
  }, [selectedSector, fetchDomestic]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (!domesticLoading) refreshDomestic(selectedSector.newsKeyword);
    }, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [domesticLoading, refreshDomestic, selectedSector]);

  const handleVisibilityChange = useCallback(() => {
    if (document.visibilityState === 'visible') {
      const isStale = !domestic?.fetchedAt || (Date.now() - domestic.fetchedAt) > STALE_FOCUS_MS;
      if (isStale && !domesticLoading) refreshDomestic(selectedSector.newsKeyword);
    }
  }, [domestic, domesticLoading, refreshDomestic, STALE_FOCUS_MS, selectedSector]);

  useEffect(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [handleVisibilityChange]);

  const handleSectorChange = (displayName) => {
    const sector = sectors.find(s => s.displayName === displayName) || ALL_TAB;
    setSelectedSector(sector);
  };

  const getLastUpdatedLabel = () => {
    if (!domestic?.fetchedAt) return null;
    const diffMin = Math.floor((Date.now() - domestic.fetchedAt) / 60000);
    if (diffMin < 1) return 'Updated just now';
    return `Updated ${diffMin} min ago`;
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Domestic Market Insights</h1>
        <button
          className="btn btn-secondary"
          onClick={() => refreshDomestic(selectedSector.newsKeyword)}
        >
          Refresh
        </button>
      </div>

      <SectorSelector
        options={sectors.map(s => s.displayName)}
        selected={selectedSector.displayName}
        onSelect={handleSectorChange}
      />

      {domesticLoading && (
        <div className="page-loading"><p>Loading domestic insights...</p></div>
      )}

      {domesticError && (
        <div className="page-error">
          <p>{domesticError}</p>
          <button
            onClick={() => refreshDomestic(selectedSector.newsKeyword)}
            className="btn btn-primary"
            style={{ marginTop: 16 }}
          >
            Retry
          </button>
        </div>
      )}

      {!domesticLoading && !domesticError && domestic && (
        <div className="market-content">
          {getLastUpdatedLabel() && (
            <p className="last-updated-label">{getLastUpdatedLabel()}</p>
          )}
          <NewsList news={domestic.news ?? []} />
          <EventsList events={domestic.events} />
        </div>
      )}
    </div>
  );
};

export default DomesticMarketPage;
