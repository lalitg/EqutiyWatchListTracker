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

const SECTOR_DESCRIPTIONS = {
  'NIFTY BANK':                        'Tracks the 12 most liquid and large-cap Indian banking stocks listed on NSE.',
  'NIFTY PRIVATE BANK':                'Top private-sector banks — faster-growing but more market-sensitive than their PSU peers.',
  'NIFTY PSU BANK':                    'Government-owned banks — larger loan books, but credit cycles tied to policy and elections.',
  'NIFTY IT':                          'Top IT & software services companies — India\'s globally-competitive tech exporters.',
  'NIFTY AUTO':                        'Leading automobile manufacturers and auto-component makers listed on NSE.',
  'NIFTY PHARMA':                      'Major pharmaceutical and drug companies — covers both domestic and export-focused firms.',
  'NIFTY HEALTHCARE INDEX':            'Broader than Pharma — includes hospitals, diagnostics, and health-tech alongside drug makers.',
  'NIFTY FMCG':                        'Fast-moving consumer goods companies selling everyday products like food, hygiene, and beverages.',
  'NIFTY FINANCIAL SERVICES':          'Broad financial sector index covering banks, NBFCs, insurance, and housing finance companies.',
  'NIFTY FINANCIAL SERVICES 25 50':    'A capped variant of the Financial Services index, limiting any single stock to 25% weight.',
  'NIFTY FINANCIAL SERVICES EX-BANK':  'Financial services companies excluding banks — NBFCs, insurance, brokers, and housing finance.',
  'NIFTY METAL':                       'Steel, aluminium, copper, and mining companies — tied closely to global commodity cycles.',
  'NIFTY REALTY':                      'Real estate developers and construction companies listed on NSE.',
  'NIFTY ENERGY':                      'Oil & gas, refining, and power companies that form the backbone of India\'s energy sector.',
  'NIFTY OIL AND GAS':                 'Focused on oil exploration, refining, and gas distribution companies listed on NSE.',
  'NIFTY CONSUMER DURABLES':           'Makers of long-lasting household products — electronics, appliances, and home goods.',
  'NIFTY CHEMICALS':                   'Specialty and commodity chemical companies — a key beneficiary of China+1 supply-chain shifts.',
  'NIFTY MEDIA':                       'Broadcasting, publishing, OTT, and entertainment companies listed on NSE.',
  'NIFTY MIDSMALL HEALTHCARE':         'Mid and small-cap healthcare companies — higher growth potential with smaller scale.',
  'NIFTY MIDSMALL IT & TELECOM':       'Emerging IT services and telecom players outside the large-cap tier.',
  'NIFTY MIDSMALL FINANCIAL SERVICES': 'Mid and small-cap financial services firms — faster-growing but higher-risk than large-cap peers.',
};

const SectorCompaniesPage = () => {
  const { sectorKey } = useParams();
  const navigate      = useNavigate();
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState(null);
  const [selectedEntry, setSelectedEntry] = useState(null);
  const { entries, addCompany, isActionLoading } = useWatchlist();

  const displayName = decodeURIComponent(sectorKey);
  const description = SECTOR_DESCRIPTIONS[displayName];

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
      {description && (
        <p style={{ color: '#64748b', fontSize: '0.9rem', marginBottom: 20, marginTop: -8 }}>
          {description}
        </p>
      )}

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
