import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchSectorCompanies } from '../services/indicesService';
import { useWatchlist } from '../context/WatchlistContext';
import CompanyInsightsModal from '../components/watchlist/CompanyInsightsModal';
import '../pages/Nifty50Page.css';

function fmt(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function PriceCell({ value, pct }) {
  const formatted = fmt(value);
  if (pct == null) return <span>{formatted}</span>;
  const n = Number(pct);
  const cls = n > 0 ? 'nifty-gain' : n < 0 ? 'nifty-loss' : '';
  const arrow = n > 0 ? '▲' : n < 0 ? '▼' : '';
  const sign  = n > 0 ? '+' : '';
  return <span className={cls}>{arrow} {formatted} ({sign}{n.toFixed(2)}%)</span>;
}

const SectorCompaniesPage = () => {
  const { sectorKey } = useParams();
  const navigate      = useNavigate();
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState(null);
  const [selectedEntry, setSelectedEntry] = useState(null);
  const { entries, addCompany, isActionLoading } = useWatchlist();

  const displayName = decodeURIComponent(sectorKey);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setCompanies(await fetchSectorCompanies(sectorKey));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [sectorKey]);

  useEffect(() => { load(); }, [load]);

  const isInWatchlist = (code) => entries.some(e => e.companyCode === code);

  return (
    <div className="page-container">
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>← Back</button>
          <h1 className="page-title">{displayName}</h1>
        </div>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {loading && <div className="page-loading"><p>Loading companies…</p></div>}
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
                <th>Prev Close</th>
                <th>Volume</th>
              </tr>
            </thead>
            <tbody>
              {companies.length === 0 ? (
                <tr><td colSpan={7} className="nifty-empty">No data available</td></tr>
              ) : (
                companies.map((c, i) => (
                  <tr
                    key={c.symbol}
                    className="nifty-clickable-row"
                    onClick={() => navigate(`/company/${encodeURIComponent(c.symbol)}`)}
                  >
                    <td>{i + 1}</td>
                    <td><strong className="nifty-symbol">{c.symbol}</strong></td>
                    <td><PriceCell value={c.ltp} pct={c.changePercent} /></td>
                    <td>{fmt(c.week52High)}</td>
                    <td>{fmt(c.week52Low)}</td>
                    <td>{fmt(c.previousClose)}</td>
                    <td>{c.tradedVolume != null ? Number(c.tradedVolume).toLocaleString('en-IN') : '—'}</td>
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

export default SectorCompaniesPage;
