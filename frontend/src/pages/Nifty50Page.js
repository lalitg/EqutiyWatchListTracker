import React from 'react';
import './Nifty50Page.css';

const Nifty50Page = () => {
  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Nifty 50</h1>
        <button className="btn btn-secondary">Refresh</button>
      </div>

      <div className="nifty-table-wrap">
        <table className="nifty-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Symbol</th>
              <th>Company</th>
              <th>Price (₹)</th>
              <th>Change %</th>
              <th>52W High</th>
              <th>52W Low</th>
              <th>Volume</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colSpan={8} className="nifty-empty">
                Data coming soon
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default Nifty50Page;
