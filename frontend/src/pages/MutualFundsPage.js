import React, { useEffect, useState, useCallback } from 'react';
import { fetchAmfiFiles, amfiNoteUrl, amfiReportUrl } from '../services/amfiService';
import './MutualFundsPage.css';

function monthLabel(monthStr) {
  const [year, month] = monthStr.split('-');
  const d = new Date(Number(year), Number(month) - 1, 1);
  return d.toLocaleString('en-IN', { month: 'long', year: 'numeric' });
}

function MutualFundRow({ file }) {
  return (
    <div className="mf-row">
      <div className="mf-month">{file.monthLabel || monthLabel(file.month)}</div>
      <div className="mf-actions">
        {file.noteAvailable ? (
          <a
            className="btn btn-secondary mf-action-btn"
            href={amfiNoteUrl(file.month)}
            target="_blank"
            rel="noopener noreferrer"
          >
            📄 View Monthly Note
          </a>
        ) : (
          <span className="mf-unavailable">Monthly Note unavailable</span>
        )}
        {file.reportAvailable ? (
          <a
            className="btn btn-secondary mf-action-btn"
            href={amfiReportUrl(file.month)}
            download
          >
            📊 Download Report (Excel)
          </a>
        ) : (
          <span className="mf-unavailable">Report unavailable</span>
        )}
      </div>
    </div>
  );
}

const MutualFundsPage = () => {
  const [files, setFiles]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    fetchAmfiFiles()
      .then(setFiles)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Mutual Funds</h1>
        <button className="btn btn-secondary" onClick={load}>Refresh</button>
      </div>

      <p className="mf-subtitle">
        Monthly AMFI documents — industry note (PDF) and data report (Excel).
      </p>

      {loading && <div className="page-loading"><p>Loading documents…</p></div>}

      {error && !loading && (
        <div className="page-error">
          <p>{error}</p>
          <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={load}>Retry</button>
        </div>
      )}

      {!loading && !error && files.length === 0 && (
        <div className="mf-empty">No AMFI documents available yet.</div>
      )}

      {!loading && !error && files.length > 0 && (
        <div className="mf-list">
          {files.map(file => <MutualFundRow key={file.month} file={file} />)}
        </div>
      )}
    </div>
  );
};

export default MutualFundsPage;
