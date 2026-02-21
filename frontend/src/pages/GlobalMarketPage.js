import React, { useEffect } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import TrendPanel from '../components/market/TrendPanel';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import { useMarket } from '../context/MarketContext';

const REGIONS = ['US', 'Europe', 'Asia'];

const GlobalMarketPage = () => {
  const {
    global, globalLoading, globalError,
    selectedRegion, setRegion, fetchGlobal,
  } = useMarket();

  useEffect(() => { fetchGlobal(selectedRegion); }, [selectedRegion, fetchGlobal]);

  const handleRegionChange = (region) => {
    setRegion(region);
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Global Market Insights</h1>
        <button className="btn btn-secondary" onClick={() => fetchGlobal(selectedRegion)}>Refresh</button>
      </div>

      <SectorSelector options={REGIONS} selected={selectedRegion} onSelect={handleRegionChange} />

      {globalLoading && (
        <div className="page-loading"><p>Loading global insights...</p></div>
      )}

      {globalError && (
        <div className="page-error">
          <p>{globalError}</p>
          <button onClick={() => fetchGlobal(selectedRegion)} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {!globalLoading && !globalError && global && (
        <div className="market-content">
          <TrendPanel trend={global.trend} />
          <NewsList news={global.news} />
          <EventsList events={global.events} />
        </div>
      )}
    </div>
  );
};

export default GlobalMarketPage;
