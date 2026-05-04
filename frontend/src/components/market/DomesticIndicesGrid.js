import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchDomesticIndices } from '../../services/indicesService';
import './DomesticIndicesGrid.css';

function fmt(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const DomesticIndicesGrid = () => {
  const [indices, setIndices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchDomesticIndices()
      .then(setIndices)
      .catch(() => setError('Could not load domestic indices'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="dig-loading">Loading indices…</div>;
  if (error)   return <div className="dig-error">{error}</div>;

  return (
    <div className="dig-section">
      <div className="dig-section-title">Indian Indices</div>
      <div className="dig-grid">
        {indices.map((idx) => {
          const pct = idx.changePercent != null ? Number(idx.changePercent) : null;
          const chg = idx.change != null ? Number(idx.change) : null;
          const positive = pct != null && pct > 0;
          const negative = pct != null && pct < 0;
          const arrow = positive ? '▲' : negative ? '▼' : '';
          const sign  = positive ? '+' : '';

          return (
            <div
              key={idx.nseParam}
              className={`dig-card ${positive ? 'dig-card--gain' : negative ? 'dig-card--loss' : ''} ${idx.hasConstituents ? 'dig-card--clickable' : ''}`}
              onClick={() => idx.hasConstituents && navigate(`/market/domestic/index/${encodeURIComponent(idx.nseParam)}`)}
            >
              <div className="dig-card-name">{idx.displayName}</div>
              <div className="dig-card-ltp">{fmt(idx.ltp)}</div>
              <div className={`dig-card-pct ${positive ? 'gain' : negative ? 'loss' : ''}`}>
                {pct != null ? `${arrow} ${Math.abs(pct).toFixed(2)}%` : '—'}
              </div>
              <div className={`dig-card-chg ${positive ? 'gain' : negative ? 'loss' : ''}`}>
                {chg != null ? `${sign}${fmt(Math.abs(chg))}` : ''}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default DomesticIndicesGrid;
