import React, { useEffect, useState, useCallback } from 'react';
import { fetchNifty50 } from '../services/nifty50Service';
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

  useEffect(() => { load(); }, [load]);

  const fmt = (val) => (val != null ? Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—');
  const fmtVol = (val) => (val != null ? Number(val).toLocaleString('en-IN') : '—');

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
                  <tr key={c.companyCode}>
                    <td>{i + 1}</td>
                    <td><strong>{c.companyCode}</strong></td>
                    <td><PriceCell value={c.currentValue} pct={c.percentChange} /></td>
                    <td>{fmt(c.week52High)}</td>
                    <td>{fmt(c.week52Low)}</td>
                    <td>{fmt(c.allTimeHigh)}</td>
                    <td>{fmt(c.allTimeLow)}</td>
                    <td>{fmtVol(c.tradedVolume)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default Nifty50Page;
