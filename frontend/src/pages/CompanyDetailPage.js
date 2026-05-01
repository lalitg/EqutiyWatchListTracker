import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchCompanyDetail } from '../services/indicesService';
import './CompanyDetailPage.css';

function fmt(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtVol(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('en-IN');
}

const CompanyDetailPage = () => {
  const { symbol } = useParams();
  const navigate   = useNavigate();
  const [data, setData]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    fetchCompanyDetail(symbol)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, [symbol]);

  if (loading) return <div className="page-container"><div className="page-loading"><p>Loading…</p></div></div>;
  if (error)   return (
    <div className="page-container">
      <div className="page-error">
        <p>{error}</p>
        <button className="btn btn-secondary" onClick={() => navigate(-1)} style={{ marginTop: 16 }}>← Back</button>
      </div>
    </div>
  );

  const pct  = data.percentChange != null ? Number(data.percentChange) : null;
  const chg  = data.changeValue   != null ? Number(data.changeValue)   : null;
  const cls  = pct == null ? 'neutral' : pct > 0 ? 'gain' : pct < 0 ? 'loss' : 'neutral';
  const arrow = pct > 0 ? '▲' : pct < 0 ? '▼' : '';

  const stats = [
    { label: '52W High',      value: fmt(data.week52High) },
    { label: '52W Low',       value: fmt(data.week52Low) },
    { label: 'All Time High', value: fmt(data.allTimeHigh) },
    { label: 'All Time Low',  value: fmt(data.allTimeLow) },
    { label: 'Prev Close',    value: fmt(data.previousClose) },
    { label: 'Change',        value: chg != null ? `${chg >= 0 ? '+' : ''}${fmt(chg)}` : '—' },
    { label: 'Traded Volume', value: fmtVol(data.tradedVolume) },
  ];

  return (
    <div className="page-container">
      <div className="page-header">
        <button className="btn btn-secondary" onClick={() => navigate(-1)}>← Back</button>
      </div>

      <div className="company-detail-header">
        <div className="company-symbol">{data.companyCode}</div>
        <div className="company-price-row">
          <span className="company-ltp">₹{fmt(data.currentValue)}</span>
          {pct != null && (
            <span className={`company-change ${cls}`}>
              {arrow} {fmt(Math.abs(chg))} ({pct >= 0 ? '+' : ''}{pct.toFixed(2)}%)
            </span>
          )}
        </div>
        {data.lastUpdated && (
          <div className="company-last-updated">
            Last updated: {new Date(data.lastUpdated).toLocaleString('en-IN')}
          </div>
        )}
      </div>

      <div className="company-stats-grid">
        {stats.map(s => (
          <div key={s.label} className="stat-card">
            <div className="stat-label">{s.label}</div>
            <div className="stat-value">{s.value}</div>
          </div>
        ))}
      </div>

      <div className="company-future-section">
        Balance sheet, quarterly results, and financials — coming soon.
      </div>
    </div>
  );
};

export default CompanyDetailPage;
