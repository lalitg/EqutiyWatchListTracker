import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  fetchQuarterlyResults,
  fetchBalanceSheet,
  fetchPeSnapshot,
  fetchCompanyIndices,
} from '../services/indicesService';
import { fetchNews } from '../services/newsService';
import { fetchEvents } from '../services/eventsService';
import { fetchSebiActivity } from '../services/sebiService';
import { fetchCompanySectors } from '../services/sectorService';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import { useWatchlist } from '../context/WatchlistContext';
import { SECTOR_DESCRIPTIONS, INDEX_DESCRIPTIONS, FINANCIALS_DESCRIPTIONS } from '../constants/marketDescriptions';
import './CompanyDetailPage.css';

function fmt(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
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
  const { entries, activeId, addCompany, isActionLoading } = useWatchlist();
  const isInWatchlist = entries.some(e => e.companyCode === symbol?.toUpperCase());

  const [loading,     setLoading]     = useState(true);
  const [error]                       = useState(null);

  const [quarterly,     setQuarterly]     = useState([]);
  const [balSheet,      setBalSheet]      = useState([]);
  const [pe,            setPe]            = useState(null);
  const [fundLoading,   setFundLoading]   = useState(true);
  const [activeTab,     setActiveTab]     = useState('quarterly');

  const [news,          setNews]          = useState([]);
  const [importantNews, setImportantNews] = useState([]);
  const [events,        setEvents]        = useState([]);
  const [sebi,          setSebi]          = useState(null);
  const [insightsTab,   setInsightsTab]   = useState('news');
  const [insightsLoading, setInsightsLoading] = useState(true);
  const [nse500Sectors, setNse500Sectors] = useState([]);
  const [companyIndices, setCompanyIndices] = useState([]);

  useEffect(() => {
    fetchCompanySectors(symbol).then(setNse500Sectors).catch(() => {});
    fetchCompanyIndices(symbol).then(setCompanyIndices).catch(() => {});

    Promise.allSettled([
      fetchQuarterlyResults(symbol),
      fetchBalanceSheet(symbol),
      fetchPeSnapshot(symbol),
    ]).then(([qRes, bRes, pRes]) => {
      if (qRes.status === 'fulfilled') setQuarterly(qRes.value || []);
      if (bRes.status === 'fulfilled') setBalSheet(bRes.value || []);
      if (pRes.status === 'fulfilled') setPe(pRes.value);
    }).finally(() => setFundLoading(false));

    Promise.allSettled([
      fetchNews(symbol),
      fetchEvents(symbol),
      fetchSebiActivity(symbol),
    ]).then(([nRes, eRes, sRes]) => {
      if (nRes.status === 'fulfilled') {
        setNews(nRes.value?.news ?? []);
        setImportantNews(nRes.value?.importantNews ?? []);
      }
      if (eRes.status === 'fulfilled') setEvents(eRes.value?.events ?? []);
      if (sRes.status === 'fulfilled') setSebi(sRes.value);
    }).finally(() => setInsightsLoading(false));

    setLoading(false);
  }, [symbol]);

  if (loading) return (
    <div className="cdp-container">
      <div className="cdp-loading">Loading…</div>
    </div>
  );

  if (error) return (
    <div className="cdp-container">
      <div className="cdp-top-bar">
        <button className="cdp-back-btn" onClick={() => navigate(-1)}>← Back</button>
      </div>
      <div className="cdp-error">{error}</div>
    </div>
  );

  const peLabel = pe ? fmtPe(pe.trailingPe) : null;

  return (
    <div className="cdp-container">
      <div className="cdp-top-bar">
        <button className="cdp-back-btn" onClick={() => navigate(-1)}>← Back</button>
        <button
          className={`cdp-watchlist-btn ${isInWatchlist ? 'cdp-watchlist-btn--added' : ''}`}
          onClick={() => !isInWatchlist && addCompany(symbol?.toUpperCase(), activeId)}
          disabled={isActionLoading || isInWatchlist}
        >
          {isInWatchlist ? '✓ In Watchlist' : isActionLoading ? 'Adding...' : '+ Add to Watchlist'}
        </button>
      </div>

      {/* ── Hero ──────────────────────────────────────────────────────────── */}
      <div className="cdp-hero">
        <div className="cdp-hero-top">
          <div className="cdp-identity">
            <div className="cdp-symbol">{symbol?.toUpperCase()}</div>
          </div>
          {peLabel && <div className="cdp-pe-pill" data-tooltip="Price-to-Earnings ratio — what you pay per ₹1 of company earnings. Lower = cheaper relative to peers.">P/E {peLabel}</div>}
        </div>

        {(nse500Sectors.length > 0 || companyIndices.length > 0) && (
          <div className="cdp-badge-section">
            {nse500Sectors.map(s => (
              <span
                key={s.displayName}
                className="cdp-badge cdp-badge--nifty cdp-badge--clickable"
                data-tooltip={SECTOR_DESCRIPTIONS[s.displayName]}
                onClick={() => navigate(
                  `/market/domestic/sector/${encodeURIComponent(s.displayName)}`,
                  { state: { newsKeyword: s.newsKeyword } }
                )}
              >
                {s.displayName}
              </span>
            ))}
            {companyIndices.map(idx => (
              <span
                key={idx.indexKey}
                className="cdp-badge cdp-badge--index cdp-badge--clickable"
                data-tooltip={INDEX_DESCRIPTIONS[idx.displayName]}
                onClick={() => navigate(
                  `/market/domestic/index/${encodeURIComponent(idx.indexKey)}`,
                  { state: { displayName: idx.displayName } }
                )}
              >
                {idx.displayName}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* ── News & Events ─────────────────────────────────────────────────── */}
      <div className="cdp-insights">
        <div className="cdp-fund-tabs">
          <button
            className={`cdp-fund-tab ${insightsTab === 'news' ? 'active' : ''}`}
            onClick={() => setInsightsTab('news')}
          >
            Latest News
          </button>
          <button
            className={`cdp-fund-tab ${insightsTab === 'important' ? 'active' : ''}`}
            onClick={() => setInsightsTab('important')}
          >
            Important News
            {importantNews.length > 0 && (
              <span className="cdp-tab-badge">{importantNews.length}</span>
            )}
          </button>
          <button
            className={`cdp-fund-tab ${insightsTab === 'events' ? 'active' : ''}`}
            onClick={() => setInsightsTab('events')}
          >
            Events
          </button>
          <button
            className={`cdp-fund-tab ${insightsTab === 'regulatory' ? 'active' : ''}`}
            onClick={() => setInsightsTab('regulatory')}
          >
            Regulatory
          </button>
        </div>
        {insightsLoading ? (
          <div className="cdp-fund-loading">Loading…</div>
        ) : insightsTab === 'news' ? (
          news.length === 0
            ? <div className="cdp-fund-empty">No news found for {symbol}.</div>
            : <NewsList news={news} />
        ) : insightsTab === 'important' ? (
          importantNews.length === 0
            ? <div className="cdp-fund-empty">No important news found for {symbol}.</div>
            : <NewsList news={importantNews} />
        ) : insightsTab === 'events' ? (
          events.length === 0
            ? <div className="cdp-fund-empty">No events found for {symbol}.</div>
            : <EventsList events={events} />
        ) : (
          <SebiActivityPanel sebi={sebi} symbol={symbol} />
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
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Revenue']}>Revenue</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Gross Profit']}>Gross Profit</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Op. Profit']}>Op. Profit</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Net Profit']}>Net Profit</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['EPS (₹)']}>EPS (₹)</th>
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
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Total Assets']}>Total Assets</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Total Debt']}>Total Debt</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Equity']}>Equity</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Cash']}>Cash</th>
                      <th data-tooltip={FINANCIALS_DESCRIPTIONS['Liabilities']}>Liabilities</th>
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

function SebiActivityPanel({ sebi, symbol }) {
  if (!sebi) {
    return <div className="cdp-fund-empty">No SEBI activity data available for {symbol}.</div>;
  }

  const enforcement = sebi.enforcementActions || [];
  const buybacks    = sebi.buybacks || [];

  if (enforcement.length === 0 && buybacks.length === 0) {
    return <div className="cdp-fund-empty">No SEBI regulatory activity found for {symbol}.</div>;
  }

  const eventTypeLabel = (t) => {
    const map = {
      ADJUDICATION_ORDER: 'Adjudication Order',
      DIRECTION: 'Direction',
      RECOVERY_NOTICE: 'Recovery Notice',
      CIRCULAR: 'Circular',
      FILING: 'Filing',
      BUYBACK: 'Buyback Filing',
      OTHER: 'Other',
    };
    return map[t] || t;
  };

  return (
    <div className="cdp-sebi-panel">
      {enforcement.length > 0 && (
        <div className="cdp-sebi-section">
          <div className="cdp-sebi-section-title">Enforcement Actions ({enforcement.length})</div>
          <div className="cdp-sebi-list">
            {enforcement.map((item, i) => (
              <div key={i} className="cdp-sebi-item">
                <div className="cdp-sebi-item-header">
                  <span className="cdp-sebi-badge cdp-sebi-badge--enforcement">
                    {eventTypeLabel(item.eventType)}
                  </span>
                  <span className="cdp-sebi-date">{item.pubDate}</span>
                </div>
                <div className="cdp-sebi-title">
                  {item.sebiUrl ? (
                    <a href={item.sebiUrl} target="_blank" rel="noopener noreferrer" className="cdp-sebi-link">
                      {item.fullTitle}
                    </a>
                  ) : (
                    item.fullTitle
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {buybacks.length > 0 && (
        <div className="cdp-sebi-section">
          <div className="cdp-sebi-section-title">Buyback Filings ({buybacks.length})</div>
          <div className="cdp-sebi-list">
            {buybacks.map((item, i) => (
              <div key={i} className="cdp-sebi-item">
                <div className="cdp-sebi-item-header">
                  <span className="cdp-sebi-badge cdp-sebi-badge--buyback">Buyback</span>
                  <span className="cdp-sebi-date">{item.buybackDate}</span>
                </div>
                <div className="cdp-sebi-title">
                  {item.sebiCompanyName}
                  {item.sebiUrl && (
                    <a href={item.sebiUrl} target="_blank" rel="noopener noreferrer" className="cdp-sebi-link"> — View Filing</a>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default CompanyDetailPage;
