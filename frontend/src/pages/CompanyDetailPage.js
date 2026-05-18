import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  fetchCompanyDetail,
  fetchQuarterlyResults,
  fetchBalanceSheet,
  fetchPeSnapshot,
  fetchCompanyMemberships,
} from '../services/indicesService';
import { fetchNews } from '../services/newsService';
import { fetchEvents } from '../services/eventsService';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
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

function fmtCr(val) {
  if (val == null) return '—';
  const n = Number(val);
  if (Math.abs(n) >= 1e7) return '₹' + (n / 1e7).toFixed(2) + ' Cr';
  if (Math.abs(n) >= 1e5) return '₹' + (n / 1e5).toFixed(2) + ' L';
  return '₹' + n.toLocaleString('en-IN');
}

function fmtPe(val) {
  if (val == null) return null;
  const n = Number(val);
  if (n <= 0) return null;
  return n.toFixed(2) + 'x';
}

const CompanyDetailPage = () => {
  const { symbol } = useParams();
  const navigate   = useNavigate();

  const [data,        setData]        = useState(null);
  const [loading,     setLoading]     = useState(true);
  const [error,       setError]       = useState(null);

  const [quarterly,   setQuarterly]   = useState([]);
  const [balSheet,    setBalSheet]    = useState([]);
  const [pe,          setPe]          = useState(null);
  const [memberships, setMemberships] = useState(null);
  const [fundLoading, setFundLoading] = useState(true);
  const [activeTab,   setActiveTab]   = useState('quarterly');

  const [news,        setNews]        = useState([]);
  const [events,      setEvents]      = useState([]);
  const [insightsTab, setInsightsTab] = useState('news');
  const [insightsLoading, setInsightsLoading] = useState(true);

  useEffect(() => {
    fetchCompanyDetail(symbol)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));

    Promise.allSettled([
      fetchQuarterlyResults(symbol),
      fetchBalanceSheet(symbol),
      fetchPeSnapshot(symbol),
      fetchCompanyMemberships(symbol),
    ]).then(([qRes, bRes, pRes, mRes]) => {
      if (qRes.status === 'fulfilled') setQuarterly(qRes.value || []);
      if (bRes.status === 'fulfilled') setBalSheet(bRes.value || []);
      if (pRes.status === 'fulfilled') setPe(pRes.value);
      if (mRes.status === 'fulfilled') setMemberships(mRes.value);
    }).finally(() => setFundLoading(false));

    Promise.allSettled([
      fetchNews(symbol),
      fetchEvents(symbol),
    ]).then(([nRes, eRes]) => {
      if (nRes.status === 'fulfilled') setNews(nRes.value?.news ?? []);
      if (eRes.status === 'fulfilled') setEvents(eRes.value?.events ?? []);
    }).finally(() => setInsightsLoading(false));
  }, [symbol]);

  if (loading) return (
    <div className="cdp-container">
      <div className="cdp-loading">Loading…</div>
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

  const peLabel = pe ? fmtPe(pe.trailingPe) : null;

  const companyName    = memberships?.companyName || null;
  const sectorBadges   = (memberships?.memberships || []).filter(m => m.type === 'SECTOR');
  const domesticBadges = (memberships?.memberships || []).filter(m => m.type === 'DOMESTIC');

  return (
    <div className="cdp-container">
      <button className="cdp-back-btn" onClick={() => navigate(-1)}>← Back</button>

      {/* ── Hero ──────────────────────────────────────────────────────────── */}
      <div className={`cdp-hero ${positive ? 'cdp-hero--gain' : negative ? 'cdp-hero--loss' : ''}`}>
        <div className="cdp-hero-top">
          <div className="cdp-identity">
            <div className="cdp-symbol">{data.companyCode}</div>
            {companyName && <div className="cdp-company-name">{companyName}</div>}
          </div>
          {peLabel && <div className="cdp-pe-pill">P/E {peLabel}</div>}
        </div>

        <div className="cdp-price-row">
          <div className="cdp-ltp">₹{fmt(ltp)}</div>
          {pct != null && (
            <div className={`cdp-change ${positive ? 'gain' : negative ? 'loss' : ''}`}>
              {arrow} {fmt(Math.abs(chg))} ({sign}{pct.toFixed(2)}%)
            </div>
          )}
        </div>

        {data.lastUpdated && (
          <div className="cdp-updated">
            {new Date(data.lastUpdated).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
          </div>
        )}

        {/* Sector + index membership badges — all of them, not just the one navigated from */}
        {(sectorBadges.length > 0 || domesticBadges.length > 0 || data.nifty50) && (
          <div className="cdp-badge-section">
            {domesticBadges.map(m => (
              <span key={m.nseKey} className="cdp-badge cdp-badge--index">{m.displayName}</span>
            ))}
            {sectorBadges.map(m => (
              <span key={m.nseKey} className="cdp-badge cdp-badge--sector">{m.displayName}</span>
            ))}
          </div>
        )}
      </div>

      {/* ── 52-week range ─────────────────────────────────────────────────── */}
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

      {/* ── Stats grid ────────────────────────────────────────────────────── */}
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
          <div className="cdp-stat-label">Volume</div>
          <div className="cdp-stat-value">{fmtVolume(data.tradedVolume)}</div>
        </div>
      </div>

      {/* ── News & Events ─────────────────────────────────────────────────── */}
      <div className="cdp-insights">
        <div className="cdp-fund-tabs">
          <button
            className={`cdp-fund-tab ${insightsTab === 'news' ? 'active' : ''}`}
            onClick={() => setInsightsTab('news')}
          >
            News
          </button>
          <button
            className={`cdp-fund-tab ${insightsTab === 'events' ? 'active' : ''}`}
            onClick={() => setInsightsTab('events')}
          >
            Events
          </button>
        </div>
        {insightsLoading ? (
          <div className="cdp-fund-loading">Loading…</div>
        ) : insightsTab === 'news' ? (
          news.length === 0
            ? <div className="cdp-fund-empty">No news found for {symbol}.</div>
            : <NewsList news={news} />
        ) : (
          events.length === 0
            ? <div className="cdp-fund-empty">No events found for {symbol}.</div>
            : <EventsList events={events} />
        )}
      </div>

      {/* ── Fundamentals ──────────────────────────────────────────────────── */}
      <div className="cdp-fundamentals">
        <div className="cdp-fund-tabs">
          <button
            className={`cdp-fund-tab ${activeTab === 'quarterly' ? 'active' : ''}`}
            onClick={() => setActiveTab('quarterly')}
          >
            Quarterly Results
          </button>
          <button
            className={`cdp-fund-tab ${activeTab === 'balsheet' ? 'active' : ''}`}
            onClick={() => setActiveTab('balsheet')}
          >
            Balance Sheet
          </button>
        </div>

        {fundLoading && <div className="cdp-fund-loading">Loading financials…</div>}

        {!fundLoading && activeTab === 'quarterly' && (
          quarterly.length === 0
            ? <div className="cdp-fund-empty">No quarterly data yet — will populate overnight.</div>
            : (
              <div className="cdp-fund-table-wrap">
                <table className="cdp-fund-table">
                  <thead>
                    <tr>
                      <th>Quarter</th>
                      <th>Revenue</th>
                      <th>Gross Profit</th>
                      <th>Op. Profit</th>
                      <th>Net Profit</th>
                      <th>EPS (₹)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {quarterly.map((q, i) => {
                      const npPositive = q.netProfit != null && Number(q.netProfit) >= 0;
                      return (
                        <tr key={i}>
                          <td className="cdp-fund-quarter">{q.quarter || q.periodEndDate}</td>
                          <td>{fmtCr(q.revenue)}</td>
                          <td>{fmtCr(q.grossProfit)}</td>
                          <td>{fmtCr(q.operatingProfit)}</td>
                          <td className={npPositive ? 'cdp-fund-gain' : 'cdp-fund-loss'}>
                            {fmtCr(q.netProfit)}
                          </td>
                          <td>{q.eps != null ? Number(q.eps).toFixed(2) : '—'}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
        )}

        {!fundLoading && activeTab === 'balsheet' && (
          balSheet.length === 0
            ? <div className="cdp-fund-empty">No balance sheet data yet — will populate overnight.</div>
            : (
              <div className="cdp-fund-table-wrap">
                <table className="cdp-fund-table">
                  <thead>
                    <tr>
                      <th>Year</th>
                      <th>Total Assets</th>
                      <th>Total Debt</th>
                      <th>Equity</th>
                      <th>Cash</th>
                      <th>Liabilities</th>
                    </tr>
                  </thead>
                  <tbody>
                    {balSheet.map((b, i) => (
                      <tr key={i}>
                        <td className="cdp-fund-quarter">{b.fiscalYear || b.periodEndDate}</td>
                        <td>{fmtCr(b.totalAssets)}</td>
                        <td className="cdp-fund-loss">{fmtCr(b.totalDebt)}</td>
                        <td className="cdp-fund-gain">{fmtCr(b.shareholdersEquity)}</td>
                        <td>{fmtCr(b.cashAndEquivalents)}</td>
                        <td>{fmtCr(b.totalLiabilities)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {balSheet[0] && (
                  <div className="cdp-bs-breakdown">
                    <div className="cdp-bs-title">{balSheet[0].fiscalYear} — Key Figures</div>
                    <div className="cdp-bs-grid">
                      <div className="cdp-bs-item"><span>Current Assets</span><strong>{fmtCr(balSheet[0].currentAssets)}</strong></div>
                      <div className="cdp-bs-item"><span>Fixed Assets</span><strong>{fmtCr(balSheet[0].fixedAssets)}</strong></div>
                      <div className="cdp-bs-item"><span>Investments</span><strong>{fmtCr(balSheet[0].totalInvestments)}</strong></div>
                      <div className="cdp-bs-item"><span>Current Liab.</span><strong>{fmtCr(balSheet[0].currentLiabilities)}</strong></div>
                      <div className="cdp-bs-item"><span>Long-Term Debt</span><strong>{fmtCr(balSheet[0].longTermDebt)}</strong></div>
                      <div className="cdp-bs-item"><span>Retained Earnings</span><strong>{fmtCr(balSheet[0].retainedEarnings)}</strong></div>
                    </div>
                  </div>
                )}
              </div>
            )
        )}
      </div>
    </div>
  );
};

export default CompanyDetailPage;
