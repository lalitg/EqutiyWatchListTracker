import React from 'react';
import SentimentBadge from '../shared/SentimentBadge';
import './TrendPanel.css';

const PERIODS = [
  { key: 'shortTerm', label: 'Short Term' },
  { key: 'mediumTerm', label: 'Medium Term' },
  { key: 'longTerm', label: 'Long Term' },
];

const TrendPanel = ({ trend }) => {
  if (!trend) return null;

  return (
    <div className="trend-panel">
      <h3 className="trend-panel-title">Trend Analysis</h3>
      <div className="trend-cards">
        {PERIODS.map(({ key, label }) => {
          const item = trend[key];
          if (!item) return null;
          return (
            <div key={key} className="trend-card">
              <div className="trend-card-header">
                <span className="trend-period">{label}</span>
                <SentimentBadge sentiment={item.direction} />
              </div>
              <p className="trend-summary">{item.summary}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default TrendPanel;
