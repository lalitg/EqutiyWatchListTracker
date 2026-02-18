import React, { useState, useEffect, useCallback } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import FastMoversTable from '../components/market/FastMoversTable';
import { fetchFastMovers } from '../services/fastMoversService';

const PERIODS = ['1D', '3D', '1W', '2W', '1M'];

const PERIOD_LABELS = {
  '1D': '1 Day',
  '3D': '3 Days',
  '1W': '1 Week',
  '2W': '2 Weeks',
  '1M': '1 Month',
};

const FastMoversPage = () => {
  const [selectedPeriod, setSelectedPeriod] = useState('1D');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const loadData = useCallback(async (period) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchFastMovers(period);
      setData(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(selectedPeriod); }, [selectedPeriod, loadData]);

  const handlePeriodChange = (period) => {
    setSelectedPeriod(period);
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Fast Movers</h1>
        <button className="btn btn-secondary" onClick={() => loadData(selectedPeriod)}>Refresh</button>
      </div>

      <SectorSelector
        options={PERIODS.map(p => p)}
        labels={PERIOD_LABELS}
        selected={selectedPeriod}
        onSelect={handlePeriodChange}
      />

      {loading && (
        <div className="page-loading"><p>Loading fast movers...</p></div>
      )}

      {error && (
        <div className="page-error">
          <p>{error}</p>
          <button onClick={() => loadData(selectedPeriod)} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {!loading && !error && data && (
        <div className="market-content">
          <FastMoversTable title="Top Gainers" data={data.gainers} type="gainers" />
          <FastMoversTable title="Top Losers" data={data.losers} type="losers" />
        </div>
      )}
    </div>
  );
};

export default FastMoversPage;
