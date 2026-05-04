import React, { useEffect, useCallback, useState } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import SectorPerformanceGrid from '../components/market/SectorPerformanceGrid';
import DomesticIndicesGrid from '../components/market/DomesticIndicesGrid';
import { useMarket } from '../context/MarketContext';
import { fetchSectorIndices } from '../services/indicesService';

const SECTORS = [
  'All',
  'NIFTY AUTO',
  'NIFTY CHEMICALS',
  'NIFTY CONSUMER DURABLES',
  'NIFTY FINANCIAL SERVICES 25/50',
  'NIFTY FINANCIAL SERVICES EX-BANK',
  'NIFTY FMCG',
  'NIFTY HEALTHCARE INDEX',
  'NIFTY IT',
  'NIFTY MEDIA',
  'NIFTY METAL',
  'NIFTY MIDSMALL FINANCIAL SERVICES',
  'NIFTY MIDSMALL HEALTHCARE',
  'NIFTY MIDSMALL IT & TELECOM',
  'NIFTY OIL & GAS',
  'NIFTY PHARMA',
  'NIFTY PRIVATE BANK',
  'NIFTY PSU BANK',
  'NIFTY REALTY',
  'NIFTY500 HEALTHCARE',
];

const SECTOR_SHORT_NAMES = {
  'All': 'All',
  'NIFTY AUTO': 'Auto',
  'NIFTY CHEMICALS': 'Chemicals',
  'NIFTY CONSUMER DURABLES': 'Consumer Durables',
  'NIFTY FINANCIAL SERVICES 25/50': 'Fin Services 25/50',
  'NIFTY FINANCIAL SERVICES EX-BANK': 'Fin Services Ex-Bank',
  'NIFTY FMCG': 'FMCG',
  'NIFTY HEALTHCARE INDEX': 'Healthcare',
  'NIFTY IT': 'IT',
  'NIFTY MEDIA': 'Media',
  'NIFTY METAL': 'Metal',
  'NIFTY MIDSMALL FINANCIAL SERVICES': 'MidSmall FinServ',
  'NIFTY MIDSMALL HEALTHCARE': 'MidSmall Health',
  'NIFTY MIDSMALL IT & TELECOM': 'MidSmall IT',
  'NIFTY OIL & GAS': 'Oil & Gas',
  'NIFTY PHARMA': 'Pharma',
  'NIFTY PRIVATE BANK': 'Pvt Bank',
  'NIFTY PSU BANK': 'PSU Bank',
  'NIFTY REALTY': 'Realty',
  'NIFTY500 HEALTHCARE': 'Nifty500 Health',
};

/** Auto-refresh interval — 15 minutes. */
const AUTO_REFRESH_MS = 15 * 60 * 1000;

const DomesticMarketPage = () => {
  const {
    domestic, domesticLoading, domesticError,
    selectedSector, setSector, fetchDomestic, refreshDomestic,
    STALE_FOCUS_MS,
  } = useMarket();

  const [sectorPcts, setSectorPcts] = useState({});

  useEffect(() => {
    fetchSectorIndices()
      .then(data => {
        const map = {};
        data.forEach(s => { map[s.nseParam] = s.changePercent; });
        setSectorPcts(map);
      })
      .catch(() => {});
  }, []);

  useEffect(() => { fetchDomestic(selectedSector); }, [selectedSector, fetchDomestic]);

  /**
   * Auto-refresh: silently re-fetches every 15 minutes.
   * Skips if a fetch is already in progress to prevent concurrent calls.
   */
  useEffect(() => {
    const interval = setInterval(() => {
      if (!domesticLoading) {
        console.log(`[DomesticMarketPage] Auto-refresh triggered for sector '${selectedSector}'`);
        refreshDomestic(selectedSector);
      }
    }, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [selectedSector, domesticLoading, refreshDomestic]);

  /**
   * Tab-focus refresh: re-fetches when the user returns to the browser tab
   * if the cached domestic data is older than STALE_FOCUS_MS (5 min).
   */
  const handleVisibilityChange = useCallback(() => {
    if (document.visibilityState === 'visible') {
      const isStale = !domestic?.fetchedAt || (Date.now() - domestic.fetchedAt) > STALE_FOCUS_MS;
      if (isStale && !domesticLoading) {
        console.log(`[DomesticMarketPage] Tab focused — stale data, refreshing sector '${selectedSector}'`);
        refreshDomestic(selectedSector);
      }
    }
  }, [selectedSector, domestic, domesticLoading, refreshDomestic, STALE_FOCUS_MS]);

  useEffect(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [handleVisibilityChange]);

  const handleSectorChange = (sector) => {
    setSector(sector);
  };

  const sectorLabels = {};
  SECTORS.forEach(sector => {
    const shortName = SECTOR_SHORT_NAMES[sector] || sector;
    const pct = sectorPcts[sector];
    if (pct != null) {
      const n = Number(pct);
      const arrow = n > 0 ? '▲' : n < 0 ? '▼' : '';
      const cls = n > 0 ? 'sector-tab-pct gain' : n < 0 ? 'sector-tab-pct loss' : 'sector-tab-pct';
      sectorLabels[sector] = (
        <span>
          {shortName}
          <span className={cls}> {arrow}{Math.abs(n).toFixed(2)}%</span>
        </span>
      );
    } else {
      sectorLabels[sector] = shortName;
    }
  });

  /**
   * Formats the fetchedAt timestamp into a human-readable "X min ago" string.
   * Returns null if no timestamp is available yet.
   */
  const getLastUpdatedLabel = () => {
    if (!domestic?.fetchedAt) return null;
    const diffMs = Date.now() - domestic.fetchedAt;
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'Updated just now';
    return `Updated ${diffMin} min ago`;
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Domestic Market Insights</h1>
        <button className="btn btn-secondary" onClick={() => fetchDomestic(selectedSector)}>Refresh</button>
      </div>

      <SectorSelector options={SECTORS} selected={selectedSector} onSelect={handleSectorChange} labels={sectorLabels} />

      {domesticLoading && (
        <div className="page-loading"><p>Loading domestic insights...</p></div>
      )}

      {domesticError && (
        <div className="page-error">
          <p>{domesticError}</p>
          <button onClick={() => fetchDomestic(selectedSector)} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {selectedSector === 'All' && (
        <>
          <DomesticIndicesGrid />
          <SectorPerformanceGrid />
        </>
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
