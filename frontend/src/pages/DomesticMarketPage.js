import React, { useEffect } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import TrendPanel from '../components/market/TrendPanel';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import { useMarket } from '../context/MarketContext';

const DomesticMarketPage = () => {
  const {
    domestic, domesticLoading, domesticError,
    selectedSector, setSector, fetchDomestic,
  } = useMarket();

  useEffect(() => { fetchDomestic(selectedSector); }, [selectedSector, fetchDomestic]);

  const handleSectorChange = (sector) => {
    setSector(sector);
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Domestic Market Insights</h1>
        <button className="btn btn-secondary" onClick={() => fetchDomestic(selectedSector)}>Refresh</button>
      </div>

      <SectorSelector selected={selectedSector} onSelect={handleSectorChange} />

      {domesticLoading && (
        <div className="page-loading"><p>Loading domestic insights...</p></div>
      )}

      {domesticError && (
        <div className="page-error">
          <p>{domesticError}</p>
          <button onClick={() => fetchDomestic(selectedSector)} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {!domesticLoading && !domesticError && domestic && (
        <div className="market-content">
          <TrendPanel trend={domestic.trend} />
          <NewsList news={domestic.news} />
          <EventsList events={domestic.events} />
        </div>
      )}
    </div>
  );
};

export default DomesticMarketPage;
