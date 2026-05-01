import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchSectorIndices } from '../../services/indicesService';
import './SectorPerformanceGrid.css';

const SectorPerformanceGrid = () => {
  const [sectors, setSectors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]   = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchSectorIndices()
      .then(setSectors)
      .catch(() => setError('Could not load sector data'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="sector-loading">Loading sectors…</div>;
  if (error)   return <div className="sector-error">{error}</div>;

  return (
    <div className="sector-grid-wrap">
      <div className="sector-grid-label">Sectors</div>
      <div className="sector-grid">
        {sectors.map((s) => {
          const pct = s.changePercent != null ? Number(s.changePercent) : null;
          const cls = pct == null ? 'neutral' : pct > 0 ? 'gain' : pct < 0 ? 'loss' : 'neutral';
          const arrow = pct > 0 ? '▲' : pct < 0 ? '▼' : '';
          const label = pct != null
            ? `${arrow} ${Math.abs(pct).toFixed(2)}%`
            : '—';

          return (
            <div
              key={s.nseParam}
              className="sector-card"
              onClick={() => navigate(`/market/domestic/sector/${encodeURIComponent(s.nseParam)}`)}
            >
              <div className="sector-card-name">{s.displayName}</div>
              <div className={`sector-card-change ${cls}`}>{label}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default SectorPerformanceGrid;
