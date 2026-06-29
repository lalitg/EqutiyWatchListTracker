import React, { useEffect, useState } from 'react';
import { fetchGlobalIndices } from '../../services/indicesService';
import './GlobalIndicesTable.css';

function fmt(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function ChangeCell({ val }) {
  if (val == null) return <span className="idx-neutral">—</span>;
  const n = Number(val);
  const cls = n > 0 ? 'idx-gain' : n < 0 ? 'idx-loss' : 'idx-neutral';
  const arrow = n > 0 ? '▲' : n < 0 ? '▼' : '';
  return <span className={cls}>{arrow} {fmt(Math.abs(val))}</span>;
}

function IndexTable({ title, rows }) {
  if (!rows || rows.length === 0) return null;
  return (
    <div className="indices-section">
      <div className="indices-section-title">{title}</div>
      <div className="indices-table-wrap">
        <table className="indices-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>LTP</th>
              <th>Change</th>
              <th>Chg %</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.symbol}>
                <td>
                  <div className="idx-name-cell">
                    <span className="idx-flag">{r.flagEmoji}</span>
                    <span className="idx-name">{r.name}</span>
                  </div>
                </td>
                <td><span className="idx-ltp">{fmt(r.ltp)}</span></td>
                <td><ChangeCell val={r.change} /></td>
                <td><ChangeCell val={r.changePercent} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const INDIAN_BROAD = new Set([
  'Sensex', 'Nifty 50', 'Nifty Midcap 50', 'Nifty Midcap 150',
  'Nifty Midcap Select', 'Nifty Smallcap', 'Nifty Microcap 250',
]);

const REGION_SECTIONS = {
  US:     (data) => [{ title: 'US Markets',       rows: data.usMarkets }],
  Europe: (data) => [{ title: 'European Markets', rows: data.europeanMarkets }],
  Asia:   (data) => [{ title: 'Asian Markets',    rows: data.asianMarkets }],
  India:  (data) => {
    const indian = data.indianMarkets || [];
    return [
      { title: 'Broad Market',     rows: indian.filter(r => INDIAN_BROAD.has(r.name)) },
      { title: 'Sectoral Indices', rows: indian.filter(r => !INDIAN_BROAD.has(r.name)) },
    ];
  },
  Global: (data) => [
    { title: 'US Markets',       rows: data.usMarkets },
    { title: 'European Markets', rows: data.europeanMarkets },
    { title: 'Asian Markets',    rows: data.asianMarkets },
  ],
};

const GlobalIndicesTable = ({ refreshKey = 0, region = 'Global' }) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    fetchGlobalIndices()
      .then(setData)
      .catch(() => setError('Could not load global indices'))
      .finally(() => setLoading(false));
  }, [refreshKey]);

  if (loading) return <div className="indices-loading">Loading global indices…</div>;
  if (error)   return <div className="indices-error">{error}</div>;
  if (!data)   return null;

  const sections = (REGION_SECTIONS[region] || REGION_SECTIONS.Global)(data);
  if (sections.length === 0) return null;

  return (
    <div>
      {sections.map(s => <IndexTable key={s.title} title={s.title} rows={s.rows} />)}
    </div>
  );
};

export default GlobalIndicesTable;
