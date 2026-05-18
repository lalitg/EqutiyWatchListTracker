import React, { useEffect, useState, useCallback } from 'react';
import { fetchNifty50 } from '../services/nifty50Service';
import CompanyInsightsModal from '../components/watchlist/CompanyInsightsModal';
import { useWatchlist } from '../context/WatchlistContext';
import './Nifty50Page.css';

const PriceCell = ({ value, pct }) => {
  const formatted = value != null ? Number(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—';
  if (pct == null) return <span>{formatted}</span>;
  const up = Number(pct) > 0;
  const down = Number(pct) < 0;
  const cls = up ? 'nifty-gain' : down ? 'nifty-loss' : '';
  const arrow = up ? '▲' : down ? '▼' : '';
  const sign = up ? '+' : '';
  return <span className={cls}>{arrow} {formatted} ({sign}{Number(pct).toFixed(2)}%)</span>;
};

const Nifty50Page = () => {
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedEntry, setSelectedEntry] = useState(null);

  const { entries, addCompany, isActionLoading } = useWatchlist();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchNifty50();
      setCompanies(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 5 * 60 * 1000);
    return () => clearInterval(t);
  }, [load]);

  const fmtVol = (val) => (val != null ? Number(val).toLocaleString('en-IN') : '—');

  const isInWatchlist = (companyCode) =>
    entries.some(e => e.companyCode === companyCode);

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Nifty 50</h1>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {loading && <div className="page-loading"><p>Loading Nifty 50 data...</p></div>}

      {error && (
        <div className="page-error">
          <p>{error}</p>
          <button onClick={load} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {!loading && !error && (
        <div className="nifty-table-wrap">
          <table className="nifty-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Symbol</th>
                <th>Price (₹)</th>
                <th>52W High</th>
                <th>52W Low</th>
                <th>All Time High</th>
                <th>All Time Low</th>
                <th>Volume (Lakhs)</th>
              </tr>
            </thead>
            <tbody>
              {companies.length === 0 ? (
                <tr>
                  <td colSpan={8} className="nifty-empty">No data available</td>
                </tr>
              ) : (
                companies.map((c, i) => (
                  <tr
                    key={c.companyCode}
                    onClick={() => setSelectedEntry({ companyCode: c.companyCode, companyName: c.companyCode })}
                    className="nifty-clickable-row"
                  >
                    <td>{i + 1}</td>
                    <td><strong>{c.companyCode}</strong></td>
                    <td><PriceCell value={c.currentValue} pct={c.percentChange} /></td>
                    <td>{c.week52High != null ? Number(c.week52High).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'}</td>
                    <td>{c.week52Low != null ? Number(c.week52Low).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'}</td>
                    <td>{c.allTimeHigh != null ? Number(c.allTimeHigh).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'}</td>
                    <td>{c.allTimeLow != null ? Number(c.allTimeLow).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'}</td>
                    <td>{fmtVol(c.tradedVolume)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      <CompanyInsightsModal
        isOpen={!!selectedEntry}
        onClose={() => setSelectedEntry(null)}
        entry={selectedEntry}
        onAddToWatchlist={addCompany}
        isInWatchlist={selectedEntry ? isInWatchlist(selectedEntry.companyCode) : false}
        isAdding={isActionLoading}
      />
    </div>
  );
};

export default Nifty50Page;
