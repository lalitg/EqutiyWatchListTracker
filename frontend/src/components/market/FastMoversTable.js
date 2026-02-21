import React from 'react';
import './FastMoversTable.css';

const FastMoversTable = ({ title, data, type }) => {
  if (!data || data.length === 0) {
    return <p className="fm-empty">No data available.</p>;
  }

  const isGainer = type === 'gainers';

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
              <th>Volume</th>
            </tr>
          </thead>
          <tbody>
            {data.map((item) => (
              <tr key={item.symbol}>
                <td className="fm-rank">{item.rank}</td>
                <td className="fm-symbol">{item.symbol}</td>
                <td className="fm-company">{item.companyName}</td>
                <td className="fm-price">{item.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                <td className={`fm-change ${isGainer ? 'fm-gain' : 'fm-loss'}`}>
                  {isGainer ? '+' : ''}{item.change.toFixed(2)}%
                </td>
                <td className="fm-volume">{item.volume}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default FastMoversTable;
