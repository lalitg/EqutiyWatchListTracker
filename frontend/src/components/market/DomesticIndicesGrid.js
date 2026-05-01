import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchDomesticIndices } from '../../services/indicesService';
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

  if (loading) return <div className="indices-loading">Loading indices…</div>;
  if (error)   return <div className="indices-error">{error}</div>;

  return (
    <div className="indices-section">
      <div className="indices-section-title">Indian Indices</div>
      <div className="indices-table-wrap">
        <table className="indices-table">
          <thead>
            <tr>
              <th>Index</th>
              <th>LTP</th>
              <th>Change</th>
              <th>Chg %</th>
            </tr>
          </thead>
          <tbody>
            {indices.map((idx) => (
              <tr
                key={idx.nseParam}
                style={idx.hasConstituents ? { cursor: 'pointer' } : {}}
                className={idx.hasConstituents ? 'nifty-clickable-row' : ''}
                onClick={() => {
                  if (idx.hasConstituents)
                    navigate(`/market/domestic/index/${encodeURIComponent(idx.nseParam)}`);
                }}
              >
                <td><span className="idx-name">{idx.displayName}</span></td>
                <td><span className="idx-ltp">{fmt(idx.ltp)}</span></td>
                <td><ChangeCell val={idx.change} /></td>
                <td><ChangeCell val={idx.changePercent} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default DomesticIndicesGrid;
