import React, { useState, useRef } from 'react';
import Modal from '../shared/Modal';
import './ImportCsvModal.css';

const STEPS = { UPLOAD: 'UPLOAD', MAP: 'MAP', RESULT: 'RESULT' };

const ImportCsvModal = ({ isOpen, onClose, onImport, isLoading }) => {
  const [step, setStep] = useState(STEPS.UPLOAD);
  const [headers, setHeaders] = useState([]);
  const [rows, setRows] = useState([]);
  const [selectedHeader, setSelectedHeader] = useState('');
  const [fileName, setFileName] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const fileInputRef = useRef(null);

  const parseCsv = (text) => {
    const lines = text.split(/\r?\n/).filter(l => l.trim());
    if (lines.length < 2) return { headers: [], rows: [] };
    const parseRow = (line) => {
      const cols = [];
      let cur = '';
      let inQuotes = false;
      for (const ch of line) {
        if (ch === '"') { inQuotes = !inQuotes; }
        else if (ch === ',' && !inQuotes) { cols.push(cur.trim()); cur = ''; }
        else { cur += ch; }
      }
      cols.push(cur.trim());
      return cols;
    };
    const h = parseRow(lines[0]);
    const r = lines.slice(1).map(parseRow);
    return { headers: h, rows: r };
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setError('');
    setFileName(file.name);
    const reader = new FileReader();
    reader.onload = (evt) => {
      const { headers: h, rows: r } = parseCsv(evt.target.result);
      if (h.length === 0) {
        setError('Could not parse CSV — file appears empty or invalid.');
        return;
      }
      setHeaders(h);
      setRows(r);
      const auto = h.find(hdr =>
        /symbol|code|ticker|company/i.test(hdr)
      ) || h[0];
      setSelectedHeader(auto);
      setStep(STEPS.MAP);
    };
    reader.readAsText(file);
  };

  const handleImport = async () => {
    const colIdx = headers.indexOf(selectedHeader);
    const codes = rows
      .map(r => r[colIdx])
      .filter(v => v && v.trim())
      .map(v => v.trim().toUpperCase());

    if (codes.length === 0) {
      setError('No company codes found in the selected column.');
      return;
    }

    setError('');
    try {
      const res = await onImport(codes);
      setResult(res);
      setStep(STEPS.RESULT);
    } catch (err) {
      setError('Import failed: ' + err.message);
    }
  };

  const handleClose = () => {
    setStep(STEPS.UPLOAD);
    setHeaders([]);
    setRows([]);
    setSelectedHeader('');
    setFileName('');
    setResult(null);
    setError('');
    if (fileInputRef.current) fileInputRef.current.value = '';
    onClose();
  };

  const colIdx = headers.indexOf(selectedHeader);
  const previewCodes = colIdx >= 0
    ? rows.slice(0, 5).map(r => r[colIdx]).filter(Boolean)
    : [];

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Import from CSV" maxWidth={480}>
      {step === STEPS.UPLOAD && (
        <div className="csv-upload-area">
          <p className="csv-hint">Upload a CSV file containing company codes (NSE symbols).</p>
          <label className="csv-file-label">
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,text/csv"
              onChange={handleFileChange}
              className="csv-file-input"
            />
            <span className="csv-file-btn">Choose CSV File</span>
          </label>
          {error && <p className="csv-error">{error}</p>}
          <div className="csv-modal-actions">
            <button className="btn btn-secondary" onClick={handleClose}>Cancel</button>
          </div>
        </div>
      )}

      {step === STEPS.MAP && (
        <div className="csv-map-area">
          <p className="csv-hint">
            <strong>{fileName}</strong> — {rows.length} rows found.
          </p>
          <div className="csv-col-select">
            <label className="csv-col-label">Select the column containing company codes:</label>
            <select
              className="csv-col-dropdown"
              value={selectedHeader}
              onChange={e => setSelectedHeader(e.target.value)}
            >
              {headers.map(h => (
                <option key={h} value={h}>{h}</option>
              ))}
            </select>
          </div>
          {previewCodes.length > 0 && (
            <div className="csv-preview">
              <span className="csv-preview-label">Preview:</span>
              <span className="csv-preview-codes">{previewCodes.join(', ')}{rows.length > 5 ? ', …' : ''}</span>
            </div>
          )}
          <p className="csv-total">{rows.filter(r => r[colIdx] && r[colIdx].trim()).length} codes will be imported</p>
          {error && <p className="csv-error">{error}</p>}
          <div className="csv-modal-actions">
            <button className="btn btn-secondary" onClick={() => { setStep(STEPS.UPLOAD); setError(''); }}>Back</button>
            <button
              className="btn btn-primary"
              onClick={handleImport}
              disabled={isLoading || !selectedHeader}
            >
              {isLoading ? 'Importing…' : 'Import'}
            </button>
          </div>
        </div>
      )}

      {step === STEPS.RESULT && result && (
        <div className="csv-result-area">
          <div className="csv-result-grid">
            <div className="csv-result-item success">
              <span className="csv-result-count">{result.imported}</span>
              <span className="csv-result-label">Imported</span>
            </div>
            <div className="csv-result-item neutral">
              <span className="csv-result-count">{result.skipped}</span>
              <span className="csv-result-label">Skipped (already in watchlist)</span>
            </div>
            <div className={`csv-result-item ${result.failed > 0 ? 'error' : 'neutral'}`}>
              <span className="csv-result-count">{result.failed}</span>
              <span className="csv-result-label">Failed</span>
            </div>
          </div>
          {result.failedCodes && result.failedCodes.length > 0 && (
            <div className="csv-failed-codes">
              <span className="csv-failed-label">Could not resolve:</span>
              <span className="csv-failed-list">{result.failedCodes.join(', ')}</span>
            </div>
          )}
          <div className="csv-modal-actions">
            <button className="btn btn-primary" onClick={handleClose}>Done</button>
          </div>
        </div>
      )}
    </Modal>
  );
};

export default ImportCsvModal;
