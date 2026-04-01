import React, { useEffect } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import { useMarket } from '../context/MarketContext';

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

      <SectorSelector options={SECTORS} selected={selectedSector} onSelect={handleSectorChange} />

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
          <NewsList news={domestic.news?.slice(0, 5)} />
          <EventsList events={domestic.events} />
        </div>
      )}
    </div>
  );
};

export default DomesticMarketPage;
