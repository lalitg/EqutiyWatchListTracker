import React, { useEffect, useState, useCallback } from 'react';
import SentimentBadge from '../components/shared/SentimentBadge';
import { useSentiments } from '../hooks/useSentiments';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { fetchIndexCompanies, fetchIndices } from '../services/indicesService';
import './CompaniesPage.css';

const INDEX_DESCRIPTIONS = {
  nifty50:            "The 50 largest and most traded companies on NSE, representing ~66% of India's total market cap.",
  niftynext50:        'The next 50 large-cap companies after Nifty 50 — strong candidates for index promotion.',
  nifty100:           'Top 100 large-cap companies on NSE — the Nifty 50 plus the Nifty Next 50.',
  nifty200:           'Top 200 companies by market cap, covering both the large-cap and upper mid-cap segments.',
  nifty500:           'The 500 largest companies on NSE, representing about 96% of total market capitalisation.',
  niftymidcap50:      '50 mid-cap companies — the core of India\'s mid-sized growth engine.',
  niftymidcap100:     '100 mid-sized companies ranked 101–200 by market cap.',
  niftymidcap150:     '150 mid-cap companies offering broader mid-cap market exposure.',
  niftysmallcap50:    'Top 50 small-cap companies — higher risk, higher growth potential.',
  niftysmallcap100:   '100 small-cap companies ranked outside the top 250 by market cap.',
  niftysmallcap250:   'Broad small-cap exposure across 250 companies outside the large/mid-cap universe.',
  niftylargemidcap250:'A combined index of the top 100 large-cap and top 150 mid-cap companies on NSE.',
  niftymicrocap250:   '250 micro-cap companies beyond the Nifty 500 universe.',
};

const IndexCompaniesPage = () => {
  const { indexKey } = useParams();
  const navigate     = useNavigate();
  const location     = useLocation();

  const [companies, setCompanies]     = useState([]);

  // One batched request for the whole table rather than one per row.
  const { sentiments } = useSentiments(companies.map(c => c.symbol));
  const [displayName, setDisplayName] = useState(location.state?.displayName || '');
  const [loading, setLoading]         = useState(false);
  const [error, setError]             = useState(null);

  // If displayName wasn't passed via router state, fetch it from the indices list
  useEffect(() => {
    if (!displayName) {
      fetchIndices()
        .then(list => {
          const match = list.find(i => i.indexKey === indexKey);
          if (match) setDisplayName(match.displayName);
        })
        .catch(() => {});
    }
  }, [indexKey, displayName]);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setCompanies(await fetchIndexCompanies(indexKey));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [indexKey]);

  useEffect(() => { load(); }, [load]);

  const description = INDEX_DESCRIPTIONS[indexKey];
  const title = displayName || indexKey;

  return (
    <div className="page-container">
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>← Back</button>
          <h1 className="page-title">{title}</h1>
        </div>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {description && <p className="cp-description">{description}</p>}

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
                <th>Industry</th>
                <th>News Sentiment</th>
              </tr>
            </thead>
            <tbody>
              {companies.length === 0 ? (
                <tr><td colSpan={5} className="nifty-empty">No data available</td></tr>
              ) : (
                companies.map((c, i) => (
                  <tr
                    key={c.symbol}
                    className="nifty-clickable-row"
                    onClick={() => navigate(`/company/${encodeURIComponent(c.symbol)}`)}
                  >
                    <td>{i + 1}</td>
                    <td><strong className="nifty-symbol">{c.symbol}</strong></td>
                    <td>{c.companyName || '—'}</td>
                    <td>{c.industry || '—'}</td>
                    <td><SentimentBadge sentiment={sentiments[c.symbol]} compact /></td>
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
