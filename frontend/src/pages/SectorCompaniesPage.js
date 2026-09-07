import React, { useEffect, useState, useCallback } from 'react';
import SentimentBadge from '../components/shared/SentimentBadge';
import { useSentiments } from '../hooks/useSentiments';
import { WATCHLIST_DESCRIPTIONS as WD } from '../constants/marketDescriptions';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { fetchSectorCompanies } from '../services/sectorService';
import { fetchMergedNews } from '../services/indicesService';
import { getCachedSectorNews, setCachedSectorNews, invalidateSectorNews } from '../services/newsCache';
import NewsList from '../components/market/NewsList';
import './CompaniesPage.css';

const NEWS_PAGE_SIZE = 20;

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
  const location      = useLocation();

  const displayName = decodeURIComponent(sectorKey);
  const newsKeyword = location.state?.newsKeyword || displayName;
  const description = SECTOR_DESCRIPTIONS[displayName];

  const [activeTab, setActiveTab] = useState('news');

  const [newsItems, setNewsItems]           = useState([]);
  const [newsPage, setNewsPage]             = useState(0);
  const [newsTotalPages, setNewsTotalPages] = useState(1);
  const [newsLoading, setNewsLoading]       = useState(false);
  const [newsError, setNewsError]           = useState(null);
  const [newsRefreshKey, setNewsRefreshKey] = useState(0);

  const [companies, setCompanies]               = useState([]);

  // One batched request for the whole table rather than one per row.
  const { sentiments } = useSentiments(companies.map(c => c.symbol));

  /**
   * Opens a company's Sentiments tab from a sentiment badge.
   *
   * stopPropagation is what makes this work at all. The whole row already carries an onClick that
   * navigates to the same company's default tab; without stopping the bubble both handlers run,
   * the row's runs second and wins, and the badge appears to do nothing.
   *
   * The `sector` router state is preserved so the company page still knows which sector the reader
   * came from, exactly as the row click does.
   */
  const openSentiments = (e, symbol) => {
    e.stopPropagation();
    navigate(
      `/company/${encodeURIComponent(symbol)}?tab=sentiments`,
      { state: { sector: displayName } }
    );
  };

  const [companiesLoading, setCompaniesLoading] = useState(false);
  const [companiesError, setCompaniesError]     = useState(null);

  // Fetch news — checks LRU cache first, falls back to API
  useEffect(() => {
    let cancelled = false;

    const cached = getCachedSectorNews(newsKeyword, newsPage);
    if (cached) {
      setNewsItems(cached.content ?? []);
      setNewsTotalPages(cached.totalPages ?? 1);
      setNewsLoading(false);
      setNewsError(null);
      return;
    }

    setNewsLoading(true);
    setNewsError(null);
    fetchMergedNews([newsKeyword], newsPage, NEWS_PAGE_SIZE)
      .then(data => {
        if (!cancelled) {
          setCachedSectorNews(newsKeyword, newsPage, data);
          setNewsItems(data.content ?? []);
          setNewsTotalPages(data.totalPages ?? 1);
        }
      })
      .catch(e => { if (!cancelled) setNewsError(e.message); })
      .finally(() => { if (!cancelled) setNewsLoading(false); });

    return () => { cancelled = true; };
  }, [newsKeyword, newsPage, newsRefreshKey]);

  const loadCompanies = useCallback(async () => {
    setCompaniesLoading(true); setCompaniesError(null);
    try {
      setCompanies(await fetchSectorCompanies(sectorKey));
    } catch (e) {
      setCompaniesError(e.message);
    } finally {
      setCompaniesLoading(false);
    }
  }, [sectorKey]);

  useEffect(() => {
    loadCompanies();
  }, [loadCompanies]);

  const handleRefresh = () => {
    if (activeTab === 'news') {
      invalidateSectorNews(newsKeyword);
      setNewsPage(0);
      setNewsRefreshKey(k => k + 1);
    } else {
      loadCompanies();
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>← Back</button>
          <h1 className="page-title">{displayName}</h1>
        </div>
        <button className="btn btn-secondary" onClick={handleRefresh}>Refresh</button>
      </div>

      {description && <p className="cp-description">{description}</p>}

      {/* ── Tab bar ─────────────────────────────────────────────────────── */}
      <div className="sec-tabs">
        <button
          className={`sec-tab ${activeTab === 'news' ? 'active' : ''}`}
          onClick={() => setActiveTab('news')}
        >
          News
        </button>
        <button
          className={`sec-tab ${activeTab === 'companies' ? 'active' : ''}`}
          onClick={() => setActiveTab('companies')}
        >
          Companies{companies.length > 0 ? ` (${companies.length})` : ''}
        </button>
      </div>

      {/* ── News tab ────────────────────────────────────────────────────── */}
      {activeTab === 'news' && (
        <div>
          {newsLoading && <div className="page-loading"><p>Loading news…</p></div>}

          {newsError && !newsLoading && (
            <div className="page-error">
              <p>{newsError}</p>
              <button
                onClick={() => { invalidateSectorNews(newsKeyword); setNewsRefreshKey(k => k + 1); }}
                className="btn btn-primary"
                style={{ marginTop: 16 }}
              >
                Retry
              </button>
            </div>
          )}

          {!newsLoading && !newsError && (
            <>
              <NewsList news={newsItems} />
              {newsTotalPages > 1 && (
                <div className="pagination">
                  <button
                    className="pagination-btn"
                    disabled={newsPage === 0}
                    onClick={() => setNewsPage(p => p - 1)}
                  >
                    Prev
                  </button>
                  <span className="page-indicator">Page {newsPage + 1} of {newsTotalPages}</span>
                  <button
                    className="pagination-btn"
                    disabled={newsPage >= newsTotalPages - 1}
                    onClick={() => setNewsPage(p => p + 1)}
                  >
                    Next
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {/* ── Companies tab ───────────────────────────────────────────────── */}
      {activeTab === 'companies' && (
        <div>
          {companiesLoading && <div className="page-loading"><p>Loading companies…</p></div>}
          {companiesError && (
            <div className="page-error">
              <p>{companiesError}</p>
              <button onClick={loadCompanies} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
            </div>
          )}
          {!companiesLoading && !companiesError && (
            <div className="nifty-table-wrap">
              <table className="nifty-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Symbol</th>
                    <th>Company</th>
                    <th title={WD['col.sentimentLatest']}>Latest News</th>
                    <th title={WD['col.sentimentQuarter']}>Last 1 Quarter</th>
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
                        onClick={() => navigate(
                          `/company/${encodeURIComponent(c.symbol)}`,
                          { state: { sector: displayName } }
                        )}
                      >
                        <td>{i + 1}</td>
                        <td><strong className="nifty-symbol">{c.symbol}</strong></td>
                        <td>{c.companyName || '—'}</td>
                        <td>
                          <SentimentBadge
                            sentiment={sentiments[c.symbol]?.latest}
                            variant="latest"
                            compact
                            onClick={(e) => openSentiments(e, c.symbol)}
                          />
                        </td>
                        <td>
                          <SentimentBadge
                            sentiment={sentiments[c.symbol]?.quarter}
                            compact
                            onClick={(e) => openSentiments(e, c.symbol)}
                          />
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default SectorCompaniesPage;
