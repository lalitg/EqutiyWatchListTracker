import React, { useEffect, useState, useCallback } from 'react';
import { fetchAmfiFiles, openAmfiNote, downloadAmfiReport } from '../services/amfiService';
import Locked from '../components/shared/Locked';
import { FEATURES } from '../context/EntitlementsContext';
import './MutualFundsPage.css';

function monthLabel(monthStr) {
  const [year, month] = monthStr.split('-');
  const d = new Date(Number(year), Number(month) - 1, 1);
  return d.toLocaleString('en-IN', { month: 'long', year: 'numeric' });
}

/**
 * One month's documents.
 *
 * These are buttons rather than links. A link is a browser navigation and a navigation sends no
 * Authorization header, so once the documents moved behind the paywall every click came back 403
 * whatever the reader had paid for. Fetching the bytes here is what lets the request carry the
 * token — at the cost of having to show "Opening…" ourselves, because a fetch has no progress bar
 * of its own the way a navigation does.
 */
function MutualFundRow({ file }) {
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);

  const run = async (which, action) => {
    setBusy(which);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(null);
    }
  };

  const openNote = () => {
    // Opened by the click itself, before anything is awaited: a tab opened after the fetch
    // resolves is outside the user gesture and a popup blocker stops it.
    const tab = window.open('', '_blank');
    return run('note', async () => {
      try {
        await openAmfiNote(file.month, tab);
      } catch (e) {
        if (tab && !tab.closed) tab.close();
        throw e;
      }
    });
  };

  const saveReport = () => run('report', () => downloadAmfiReport(file.month));

  return (
    <div className="mf-row">
      <div className="mf-month">{file.monthLabel || monthLabel(file.month)}</div>
      <div className="mf-actions">
        {file.noteAvailable ? (
          <button
            type="button"
            className="btn btn-secondary mf-action-btn"
            onClick={openNote}
            disabled={busy !== null}
          >
            {busy === 'note' ? '📄 Opening…' : '📄 View Monthly Note'}
          </button>
        ) : (
          <span className="mf-unavailable">Monthly Note unavailable</span>
        )}
        {file.reportAvailable ? (
          <button
            type="button"
            className="btn btn-secondary mf-action-btn"
            onClick={saveReport}
            disabled={busy !== null}
          >
            {busy === 'report' ? '📊 Downloading…' : '📊 Download Report (Excel)'}
          </button>
        ) : (
          <span className="mf-unavailable">Report unavailable</span>
        )}
        {error && <span className="mf-doc-error" role="alert">{error}</span>}
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

      {!loading && (
        <Locked
          feature={FEATURES.MUTUAL_FUNDS}
          title="AMFI monthly documents"
          note="The industry note and the full data report for every month, straight from AMFI.
                Included from the Basic plan upwards."
        >
          {error ? (
            <div className="page-error">
              <p>{error}</p>
              <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={load}>Retry</button>
            </div>
          ) : files.length === 0 ? (
            <div className="mf-empty">No AMFI documents available yet.</div>
          ) : (
            <div className="mf-list">
              {files.map(file => <MutualFundRow key={file.month} file={file} />)}
            </div>
          )}
        </Locked>
      )}
    </div>
  );
};

export default MutualFundsPage;
