import React, { useEffect, useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import NewsList from '../components/market/NewsList';
import EventsList from '../components/market/EventsList';
import { useMarket } from '../context/MarketContext';
import { fetchSectorTabs } from '../services/sectorService';
import { fetchIndices, fetchGlobalIndices, fetchMergedNews } from '../services/indicesService';
import { DOMESTIC_MACRO_KEYWORDS } from '../services/marketService';
import { SECTOR_DESCRIPTIONS, INDEX_DESCRIPTIONS } from '../constants/marketDescriptions';

const AUTO_REFRESH_MS = 15 * 60 * 1000;
const NEWS_PAGE_SIZE  = 20;

const SECTOR_INDEX_NAME = {
  'Auto':              'Nifty Auto',
  'Energy':            'Nifty Energy',
  'FMCG':              'Nifty FMCG',
  'Financial Services':'Nifty Bank',
  'IT':                'Nifty IT',
  'Infra':             'Nifty Infra',
  'Media':             'Nifty Media',
  'Metals':            'Nifty Metal',
  'Pharma':            'Nifty Pharma',
  'Realty':            'Nifty Realty',
};


const DomesticMarketPage = () => {
  const navigate = useNavigate();
  const {
    domestic, domesticLoading,
    fetchDomestic, refreshDomestic,
    STALE_FOCUS_MS,
  } = useMarket();

  const [sectors, setSectors]               = useState([]);
  const [indices, setIndices]               = useState([]);
  const [sectorPriceMap, setSectorPriceMap] = useState({});

  const [newsItems, setNewsItems]           = useState([]);
  const [newsPage, setNewsPage]             = useState(0);
  const [newsTotalPages, setNewsTotalPages] = useState(1);
  const [newsLoading, setNewsLoading]       = useState(false);
  const [newsError, setNewsError]           = useState(null);
  const [retryKey, setRetryKey]             = useState(0);

  useEffect(() => {
    fetchSectorTabs().then(setSectors).catch(() => {});
    fetchIndices().then(setIndices).catch(() => {});
    fetchGlobalIndices().then(data => {
      const map = {};
      (data.indianMarkets || []).forEach(idx => { map[idx.name] = idx; });
      setSectorPriceMap(map);
    }).catch(() => {});
  }, []);

  // Always fetch all-sector merged news
  useEffect(() => {
    let cancelled = false;
    const keywords = [
      ...sectors.map(s => s.newsKeyword).filter(Boolean),
      ...DOMESTIC_MACRO_KEYWORDS,
    ];
    if (keywords.length === 0) return;
    setNewsLoading(true);
    setNewsError(null);
    fetchMergedNews(keywords, newsPage, NEWS_PAGE_SIZE)
      .then(data => {
        if (!cancelled) {
          setNewsItems(data.content ?? []);
          setNewsTotalPages(data.totalPages ?? 1);
        }
      })
      .catch(e => { if (!cancelled) setNewsError(e.message); })
      .finally(() => { if (!cancelled) setNewsLoading(false); });
    return () => { cancelled = true; };
  }, [newsPage, retryKey, sectors]);

  useEffect(() => {
    fetchDomestic('Nifty 50');
  }, [fetchDomestic]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (!domesticLoading) refreshDomestic('Nifty 50');
    }, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [domesticLoading, refreshDomestic]);

  const handleVisibilityChange = useCallback(() => {
    if (document.visibilityState === 'visible') {
      const isStale = !domestic?.fetchedAt || (Date.now() - domestic.fetchedAt) > STALE_FOCUS_MS;
      if (isStale && !domesticLoading) refreshDomestic('Nifty 50');
    }
  }, [domestic, domesticLoading, refreshDomestic, STALE_FOCUS_MS]);

  useEffect(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [handleVisibilityChange]);

  const handleRefresh = () => {
    refreshDomestic('Nifty 50');
    setNewsPage(0);
    setRetryKey(k => k + 1);
  };

  const getLastUpdatedLabel = () => {
    if (!domestic?.fetchedAt) return null;
    const diffMin = Math.floor((Date.now() - domestic.fetchedAt) / 60000);
    if (diffMin < 1) return 'Updated just now';
    return `Updated ${diffMin} min ago`;
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Domestic Market Insights</h1>
        <button className="btn btn-secondary" onClick={handleRefresh}>Refresh</button>
      </div>

      {/* ── Nifty Indices grid ─────────────────────────────────────────── */}
      {indices.length > 0 && (
        <div className="index-section">
          <h2 className="index-section-title">Nifty Indices</h2>
          <div className="index-grid">
            {indices.map(idx => (
              <button
                key={idx.indexKey}
                className="index-card"
                data-tooltip={INDEX_DESCRIPTIONS[idx.displayName]}
                onClick={() => navigate(
                  `/market/domestic/index/${encodeURIComponent(idx.indexKey)}`,
                  { state: { displayName: idx.displayName } }
                )}
              >
                <div className="index-card__name">{idx.displayName}</div>
                <div className="index-card__count">{idx.companyCount} companies</div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* ── Sectors grid ──────────────────────────────────────────────── */}
      {sectors.length > 0 && (
        <div className="index-section">
          <h2 className="index-section-title">Sectors</h2>
          <div className="index-grid">
            {sectors.map(s => {
              const idxName = SECTOR_INDEX_NAME[s.displayName];
              const price   = idxName ? sectorPriceMap[idxName] : null;
              const chgPct  = price?.changePercent;
              const isUp    = chgPct != null && Number(chgPct) > 0;
              const isDown  = chgPct != null && Number(chgPct) < 0;
              return (
                <button
                  key={s.displayName}
                  className="index-card"
                  data-tooltip={SECTOR_DESCRIPTIONS[s.displayName]}
                  onClick={() => navigate(
                    `/market/domestic/sector/${encodeURIComponent(s.displayName)}`,
                    { state: { newsKeyword: s.newsKeyword } }
                  )}
                >
                  <div className="index-card__name">{s.displayName}</div>
                  {price && (
                    <>
                      <div className="index-card__count">
                        {Number(price.ltp).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </div>
                      <div className={`index-card__chg ${isUp ? 'idx-gain' : isDown ? 'idx-loss' : 'idx-neutral'}`}>
                        {isUp ? '▲' : isDown ? '▼' : ''} {Math.abs(Number(chgPct)).toFixed(2)}%
                      </div>
                    </>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* ── Market News (all sectors merged) ──────────────────────────── */}
      <div className="index-section">
        <h2 className="index-section-title">Market News</h2>

        {newsLoading && (
          <div className="page-loading"><p>Loading market news...</p></div>
        )}

        {newsError && !newsLoading && (
          <div className="page-error">
            <p>{newsError}</p>
            <button onClick={() => setRetryKey(k => k + 1)} className="btn btn-primary" style={{ marginTop: 16 }}>
              Retry
            </button>
          </div>
        )}

        {!newsLoading && !newsError && (
          <div className="market-content">
            {getLastUpdatedLabel() && (
              <p className="last-updated-label">{getLastUpdatedLabel()}</p>
            )}
            <NewsList news={newsItems} />
            {newsTotalPages > 1 && (
              <div className="pagination">
                <button className="pagination-btn" disabled={newsPage === 0} onClick={() => setNewsPage(p => p - 1)}>
                  Prev
                </button>
                <span className="page-indicator">Page {newsPage + 1} of {newsTotalPages}</span>
                <button className="pagination-btn" disabled={newsPage >= newsTotalPages - 1} onClick={() => setNewsPage(p => p + 1)}>
                  Next
                </button>
              </div>
            )}
            <EventsList events={domestic?.events} />
          </div>
        )}
      </div>
    </div>
  );
};

export default DomesticMarketPage;
