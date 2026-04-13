import React, { useState, useEffect, useCallback } from 'react';
import SectorSelector from '../components/market/SectorSelector';
import FastMoversTable from '../components/market/FastMoversTable';
import { fetchFastMovers } from '../services/fastMoversService';

const RANGES = ['TODAY', '1D', '2D', '3D', '4D', '1W'];

const RANGE_LABELS = {
  'TODAY': 'Today',
  '1D':    '1 Day',
  '2D':    '2 Day',
  '3D':    '3 Day',
  '4D':    '4 Day',
  '1W':    '1 Week',
};

function formatLastUpdated(generatedAt) {
  if (!generatedAt) return null;
  const diffMs = Date.now() - new Date(generatedAt).getTime();
  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return 'just now';
  if (mins === 1) return '1 min ago';
  return `${mins} min ago`;
}

const FastMoversPage = () => {
  const [selectedRange, setSelectedRange] = useState('TODAY');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const loadData = useCallback(async (range) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchFastMovers(range);
      setData(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(selectedRange); }, [selectedRange, loadData]);

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Fast Movers</h1>
        <button className="btn btn-secondary" onClick={() => loadData(selectedRange)}>Refresh</button>
      </div>

      <SectorSelector
        options={RANGES}
        labels={RANGE_LABELS}
        selected={selectedRange}
        onSelect={setSelectedRange}
      />

      {selectedRange === 'TODAY' && data && data.generatedAt && (
        <p style={{ fontSize: 12, color: '#94a3b8', margin: '8px 0 16px' }}>
          Last updated: {formatLastUpdated(data.generatedAt)}
        </p>
      )}

      {loading && (
        <div className="page-loading"><p>Loading fast movers...</p></div>
      )}

      {error && (
        <div className="page-error">
          <p>{error}</p>
          <button onClick={() => loadData(selectedRange)} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
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
