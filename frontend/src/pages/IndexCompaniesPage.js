import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchDomesticIndexCompanies } from '../services/indicesService';
import './CompaniesPage.css';

const INDEX_DESCRIPTIONS = {
  'NIFTY 50':              'The 50 largest and most traded companies on NSE, representing ~66% of India\'s total market cap.',
  'NIFTY 100':             'Top 100 large-cap companies on NSE — the Nifty 50 plus the next 50 most liquid stocks.',
  'NIFTY 200':             'Top 200 companies by market cap, covering both the large-cap and upper mid-cap segments.',
  'NIFTY 500':             'The 500 largest companies on NSE, together representing about 96% of total market capitalisation.',
  'NIFTY MIDCAP 100':      '100 mid-sized companies ranked 101–200 by market cap — the growth engine between large and small caps.',
  'NIFTY LARGEMIDCAP 250': 'A combined index of the top 100 large-cap and top 150 mid-cap companies on NSE.',
  'NIFTY SMLCAP 100':      '100 small-cap companies ranked outside the top 250 — higher risk, higher potential growth.',
};

const IndexCompaniesPage = () => {
  const { indexKey }  = useParams();
  const navigate      = useNavigate();
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState(null);
  const displayName = decodeURIComponent(indexKey);
  const description = INDEX_DESCRIPTIONS[displayName];

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setCompanies(await fetchDomesticIndexCompanies(indexKey));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [indexKey]);

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
                    onClick={() => navigate(`/company/${encodeURIComponent(c.symbol)}`, { state: { index: displayName } })}
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

export default IndexCompaniesPage;
