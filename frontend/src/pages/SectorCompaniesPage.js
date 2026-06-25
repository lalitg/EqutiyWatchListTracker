import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchSectorCompanies } from '../services/sectorService';
import './CompaniesPage.css';

const SECTOR_DESCRIPTIONS = {
  'Auto':                 'Leading automobile manufacturers and auto-component makers listed on NSE.',
  'Capital Goods':        'Engineering, defence, and industrial equipment companies that power India\'s manufacturing sector.',
  'Chemicals':            'Specialty and commodity chemical companies — a key beneficiary of China+1 supply-chain shifts.',
  'Consumer Durables':    'Makers of long-lasting household products — electronics, appliances, and home goods.',
  'Consumer Services':    'Hotels, restaurants, education, and leisure companies serving Indian consumers.',
  'Energy':               'Oil & gas, refining, and power companies that form the backbone of India\'s energy sector.',
  'FMCG':                 'Fast-moving consumer goods companies selling everyday products like food, hygiene, and beverages.',
  'Financial Services':   'Banks, NBFCs, insurance, and housing finance companies that drive India\'s credit economy.',
  'IT':                   'Top IT & software services companies — India\'s globally-competitive tech exporters.',
  'Infra':                'Infrastructure developers spanning roads, ports, airports, and urban construction.',
  'Media':                'Broadcasting, publishing, OTT, and entertainment companies listed on NSE.',
  'Metals':               'Steel, aluminium, copper, and mining companies — tied closely to global commodity cycles.',
  'Pharma':               'Major pharmaceutical and drug companies — covers both domestic and export-focused firms.',
  'Realty':               'Real estate developers and construction companies listed on NSE.',
  'Services':             'Diversified services sector including logistics, staffing, and business process firms.',
  'Telecom':              'Mobile network operators and telecom infrastructure companies shaping India\'s digital connectivity.',
};

const SectorCompaniesPage = () => {
  const { sectorKey } = useParams();
  const navigate      = useNavigate();
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState(null);
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
        <p className="cp-description">{description}</p>
      )}

      {companies.length > 0 && (
        <div className="cp-sort-bar">
          <span className="cp-count">{companies.length} companies</span>
        </div>
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
                <th>Company</th>
              </tr>
            </thead>
            <tbody>
              {companies.length === 0 ? (
                <tr><td colSpan={3} className="nifty-empty">No data available</td></tr>
              ) : (
                companies.map((c, i) => (
                  <tr
                    key={c.symbol}
                    className="nifty-clickable-row"
                    onClick={() => navigate(`/company/${encodeURIComponent(c.symbol)}`, { state: { sector: displayName } })}
                  >
                    <td>{i + 1}</td>
                    <td><strong className="nifty-symbol">{c.symbol}</strong></td>
                    <td>{c.companyName || '—'}</td>
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

export default SectorCompaniesPage;
