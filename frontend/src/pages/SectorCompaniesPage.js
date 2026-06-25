import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { fetchSectorCompanies } from '../services/sectorService';
import { fetchNews } from '../services/newsService';
import NewsList from '../components/market/NewsList';
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
  const location      = useLocation();

  const displayName  = decodeURIComponent(sectorKey);
  const newsKeyword  = location.state?.newsKeyword || displayName;
  const description  = SECTOR_DESCRIPTIONS[displayName];

  const [activeTab, setActiveTab] = useState('news');

  const [news, setNews]               = useState([]);
  const [newsLoading, setNewsLoading] = useState(false);
  const [newsError, setNewsError]     = useState(null);

  const [companies, setCompanies]               = useState([]);
  const [companiesLoading, setCompaniesLoading] = useState(false);
  const [companiesError, setCompaniesError]     = useState(null);

  const loadNews = useCallback(async () => {
    setNewsLoading(true); setNewsError(null);
    try {
      const data = await fetchNews(newsKeyword);
      setNews(data.news ?? []);
    } catch (e) {
      setNewsError(e.message);
    } finally {
      setNewsLoading(false);
    }
  }, [newsKeyword]);

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
    loadNews();
    loadCompanies();
  }, [loadNews, loadCompanies]);

  const handleRefresh = () => {
    if (activeTab === 'news') loadNews();
    else loadCompanies();
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button className="btn btn-secondary" onClick={() => navigate(-1)}>← Back</button>
          <h1 className="page-title">{displayName}</h1>
        </div>
        <button className="btn btn-secondary" onClick={handleRefresh}>
          Refresh
        </button>
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
          {newsError && (
            <div className="page-error">
              <p>{newsError}</p>
              <button onClick={loadNews} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
            </div>
          )}
          {!newsLoading && !newsError && <NewsList news={news} />}
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
                        onClick={() => navigate(
                          `/company/${encodeURIComponent(c.symbol)}`,
                          { state: { sector: displayName } }
                        )}
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
      )}
    </div>
  );
};

export default SectorCompaniesPage;
