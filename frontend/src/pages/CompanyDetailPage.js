import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { fetchCompanyDetail } from '../services/indicesService';
import './CompanyDetailPage.css';

function fmt(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtVolume(val) {
  if (val == null) return '—';
  const n = Number(val);
  if (n >= 1e7) return (n / 1e7).toFixed(2) + ' Cr';
  if (n >= 1e5) return (n / 1e5).toFixed(2) + ' L';
  return n.toLocaleString('en-IN');
}

const CompanyDetailPage = () => {
  const { symbol }   = useParams();
  const navigate     = useNavigate();
  const location     = useLocation();
  const [data, setData]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  const fromSector = location.state?.sector;
  const fromIndex  = location.state?.index;

  useEffect(() => {
    fetchCompanyDetail(symbol)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, [symbol]);

  if (loading) return (
    <div className="cdp-container">
      <div className="cdp-loading">Loading company data…</div>
    </div>
  );

  if (error) return (
    <div className="cdp-container">
      <button className="cdp-back-btn" onClick={() => navigate(-1)}>← Back</button>
      <div className="cdp-error">{error}</div>
    </div>
  );

  const pct      = data.percentChange != null ? Number(data.percentChange) : null;
  const chg      = data.changeValue   != null ? Number(data.changeValue)   : null;
  const ltp      = data.currentValue  != null ? Number(data.currentValue)  : null;
  const hi52     = data.week52High    != null ? Number(data.week52High)    : null;
  const lo52     = data.week52Low     != null ? Number(data.week52Low)     : null;
  const positive = pct != null && pct > 0;
  const negative = pct != null && pct < 0;
  const arrow    = positive ? '▲' : negative ? '▼' : '';
  const sign     = positive ? '+' : '';

  const rangePos = (hi52 && lo52 && ltp && hi52 > lo52)
    ? Math.min(100, Math.max(0, ((ltp - lo52) / (hi52 - lo52)) * 100))
    : null;

  return (
    <div className="cdp-container">
      <button className="cdp-back-btn" onClick={() => navigate(-1)}>← Back</button>

      {/* Hero */}
      <div className={`cdp-hero ${positive ? 'cdp-hero--gain' : negative ? 'cdp-hero--loss' : ''}`}>
        <div className="cdp-hero-top">
          <div className="cdp-symbol">{data.companyCode}</div>
          <div className="cdp-badges">
            {fromSector && <span className="cdp-badge cdp-badge--sector">{fromSector}</span>}
            {fromIndex  && <span className="cdp-badge cdp-badge--index">{fromIndex}</span>}
            {data.nifty50 && <span className="cdp-badge cdp-badge--nifty">Nifty 50</span>}
          </div>
        </div>
        <div className="cdp-ltp">₹{fmt(ltp)}</div>
        {pct != null && (
          <div className={`cdp-change ${positive ? 'gain' : negative ? 'loss' : ''}`}>
            {arrow} {fmt(Math.abs(chg))}&nbsp;({sign}{pct.toFixed(2)}%)
          </div>
        )}
        {data.lastUpdated && (
          <div className="cdp-updated">
            {new Date(data.lastUpdated).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
          </div>
        )}
      </div>

      {/* 52-week range bar */}
      {rangePos !== null && (
        <div className="cdp-range-section">
          <div className="cdp-range-header">52-Week Range</div>
          <div className="cdp-range-label-row">
            <span className="cdp-range-low">₹{fmt(lo52)}</span>
            <span className="cdp-range-high">₹{fmt(hi52)}</span>
          </div>
          <div className="cdp-range-track">
            <div className="cdp-range-fill" style={{ width: `${rangePos}%` }} />
            <div className="cdp-range-dot" style={{ left: `calc(${rangePos}% - 7px)` }} />
          </div>
        </div>
      )}

      {/* Stats grid */}
      <div className="cdp-stats-grid">
        <div className="cdp-stat">
          <div className="cdp-stat-label">Prev Close</div>
          <div className="cdp-stat-value">₹{fmt(data.previousClose)}</div>
        </div>
        <div className="cdp-stat">
          <div className="cdp-stat-label">52W High</div>
          <div className="cdp-stat-value cdp-stat--gain">₹{fmt(hi52)}</div>
        </div>
        <div className="cdp-stat">
          <div className="cdp-stat-label">52W Low</div>
          <div className="cdp-stat-value cdp-stat--loss">₹{fmt(lo52)}</div>
        </div>
        <div className="cdp-stat">
          <div className="cdp-stat-label">All-Time High</div>
          <div className="cdp-stat-value">₹{fmt(data.allTimeHigh)}</div>
        </div>
        <div className="cdp-stat">
          <div className="cdp-stat-label">All-Time Low</div>
          <div className="cdp-stat-value">₹{fmt(data.allTimeLow)}</div>
        </div>
        <div className="cdp-stat">
          <div className="cdp-stat-label">Traded Volume</div>
          <div className="cdp-stat-value">{fmtVolume(data.tradedVolume)}</div>
        </div>
      </div>

      <div className="cdp-coming-soon">
        Balance sheet, quarterly results &amp; financials — coming soon.
      </div>
    </div>
  );
};

export default CompanyDetailPage;
