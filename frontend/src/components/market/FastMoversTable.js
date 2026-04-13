import React from 'react';
import './FastMoversTable.css';

const fmt = (val) =>
  val != null
    ? Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : '—';

const FastMoversTable = ({ title, data, type }) => {
  const isGainer = type === 'gainers';

  if (!data || data.length === 0) {
    return (
      <div className="fm-section">
        <h3 className="fm-section-title">{title}</h3>
        <p className="fm-empty">Not enough data yet.</p>
      </div>
    );
  }

  return (
    <div className="fm-section">
      <h3 className="fm-section-title">{title}</h3>
      <div className="fm-table-wrap">
        <table className="fm-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Symbol</th>
              <th>Company</th>
              <th>Price</th>
              <th>Change %</th>
            </tr>
          </thead>
          <tbody>
            {data.map((item) => {
              const pct = item.pctChange != null ? Number(item.pctChange) : null;
              const sign = pct != null && pct > 0 ? '+' : '';
              const arrow = pct != null && pct > 0 ? '▲' : pct != null && pct < 0 ? '▼' : '';
              return (
                <tr key={item.companyCode}>
                  <td className="fm-rank">{item.rank}</td>
                  <td className="fm-symbol">{item.companyCode}</td>
                  <td className="fm-company">{item.companyName}</td>
                  <td className="fm-price">{fmt(item.currentPrice)}</td>
                  <td className={`fm-change ${isGainer ? 'fm-gain' : 'fm-loss'}`}>
                    {arrow} {pct != null ? `${sign}${pct.toFixed(2)}%` : '—'}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default FastMoversTable;
