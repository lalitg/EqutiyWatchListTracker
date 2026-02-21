import React from 'react';

const COLORS = {
  BULLISH: { bg: '#dcfce7', color: '#16a34a', border: '#bbf7d0' },
  BEARISH: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
  NEUTRAL: { bg: '#fefce8', color: '#ca8a04', border: '#fef08a' },
};

const SentimentBadge = ({ sentiment }) => {
  const key = sentiment?.toUpperCase() || 'NEUTRAL';
  const scheme = COLORS[key] || COLORS.NEUTRAL;

  const style = {
    display: 'inline-block',
    padding: '4px 12px',
    borderRadius: '20px',
    fontSize: '12px',
    fontWeight: 600,
    letterSpacing: '0.5px',
    background: scheme.bg,
    color: scheme.color,
    border: `1px solid ${scheme.border}`,
  };

  return <span style={style}>{key}</span>;
};

export default SentimentBadge;
