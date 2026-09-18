import React, { useState, useMemo, useEffect } from 'react';
import { WATCHLIST_DESCRIPTIONS as WD } from '../../constants/marketDescriptions';
import './WatchlistTable.css';
import SentimentBadge from '../shared/SentimentBadge';
import { useSentiments } from '../../hooks/useSentiments';

const TABLE_COLUMNS = [
  { key: 'companyCode', label: 'Symbol',  sortable: true, tooltip: WD['col.symbol'] },
  { key: 'companyName', label: 'Company', sortable: true, tooltip: WD['col.company'] },
];

/**
 * The two news-sentiment columns.
 *
 * Kept out of TABLE_COLUMNS because they render a badge rather than a cell value, but they carry
 * everything sorting needs: `read` pulls this column's reading out of the fetched map, and
 * `tieBreak` settles two companies that landed on the same score. Defining them once is what keeps
 * each header, its cell and its comparator describing the same column.
 */
const SENTIMENT_COLUMNS = [
  {
    key: 'sentimentLatest',
    label: 'Latest News Sentiments',
    tooltip: WD['col.sentimentLatest'],
    variant: 'latest',
    read: (reading) => reading?.latest,
    // Equal scores, so the newer headline goes first: of two identical readings it is the one that
    // still describes the present.
    tieBreak: (a, b) => (b?.latest?.publishedAt ?? 0) - (a?.latest?.publishedAt ?? 0),
  },
  {
    key: 'sentimentQuarter',
    label: 'Overall News Sentiments',
    tooltip: WD['col.sentimentQuarter'],
    variant: 'aggregate',
    read: (reading) => reading?.quarter,
    // Equal averages, so the one resting on more articles goes first — the rule the Extremes
    // boards break ties on, and for the same reason: twenty articles agreeing is a stronger claim
    // than one loud headline sitting at the same point on the scale.
    tieBreak: (a, b) => (b?.quarter?.articleCount ?? 0) - (a?.quarter?.articleCount ?? 0),
  },
];

const SENTIMENT_BY_KEY = Object.fromEntries(SENTIMENT_COLUMNS.map(col => [col.key, col]));

const ROWS_PER_PAGE = 25;

const DeleteIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="3 6 5 6 21 6"/>
    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
  </svg>
);

const WatchlistTable = ({ entries, onCompanyClick, onBulkDelete }) => {
  const [sortConfig, setSortConfig] = useState({ key: null, direction: 'asc' });
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedRowIds, setSelectedRowIds] = useState(new Set());

  useEffect(() => { setCurrentPage(1); }, [entries.length]);
  useEffect(() => { setSelectedRowIds(new Set()); }, [entries]);

  const formatCellValue = (value) => {
    if (value === null || value === undefined || value === '') return '-';
    return value;
  };

  // Fetched for every entry rather than only the page on screen. Sorting by a sentiment column needs
  // a reading for every row, and a watchlist holds at most ten companies anyway. Scoping it to the
  // page would also feed the sort back into its own input: the sort decides which rows are on the
  // page, which would decide what is fetched, which would decide the sort.
  const { sentiments } = useSentiments(entries.map(e => e.companyCode));

  const sortedEntries = useMemo(() => {
    if (!sortConfig.key) return entries;

    const column = SENTIMENT_BY_KEY[sortConfig.key];
    const factor = sortConfig.direction === 'asc' ? 1 : -1;

    if (!column) {
      return [...entries].sort((a, b) => {
        const av = a[sortConfig.key] ?? '';
        const bv = b[sortConfig.key] ?? '';
        if (av < bv) return sortConfig.direction === 'asc' ? -1 : 1;
        if (av > bv) return sortConfig.direction === 'asc' ? 1 : -1;
        return 0;
      });
    }

    const scoreOf = (entry) => {
      const reading = column.read(sentiments[entry.companyCode]);
      return typeof reading?.score === 'number' ? reading.score : null;
    };

    return [...entries].sort((a, b) => {
      const av = scoreOf(a);
      const bv = scoreOf(b);

      // A company with no scored news has no place on the scale, so it sinks to the bottom in BOTH
      // directions. Sorting it as zero would drop companies nothing has been written about into the
      // middle of the ranking, and letting it lead the ascending sort would turn "most negative
      // first" into a list of companies that simply have no news.
      if (av === null && bv === null) return 0;
      if (av === null) return 1;
      if (bv === null) return -1;

      if (av !== bv) return (av - bv) * factor;

      // The tie-break deliberately ignores the sort direction: reversing the column should reverse
      // the ranking, not demote the newest article or the best-evidenced average within a tie.
      return column.tieBreak(sentiments[a.companyCode], sentiments[b.companyCode]);
    });
    // `sentiments` belongs in the dependencies: it arrives after the first render, and without it a
    // table sorted by sentiment would keep the order it had while the scores were still loading.
  }, [entries, sortConfig, sentiments]);

  const paginatedEntries = useMemo(() => {
    const start = (currentPage - 1) * ROWS_PER_PAGE;
    return sortedEntries.slice(start, start + ROWS_PER_PAGE);
  }, [sortedEntries, currentPage]);

  const totalPages = Math.ceil(sortedEntries.length / ROWS_PER_PAGE);

  /**
   * Opens the company's Sentiments tab from a sentiment badge.
   *
   * stopPropagation is what makes this work at all. The whole row already carries an onClick that
   * navigates to the same company's default tab; without stopping the bubble both handlers run,
   * the row's runs second and wins, and the badge appears to do nothing.
   */
  const openSentiments = (e, entry) => {
    e.stopPropagation();
    onCompanyClick(entry, { tab: 'sentiments' });
  };

  /**
   * Sorts by the clicked header, and reverses it when the same header is clicked again.
   *
   * A sentiment column opens on its most useful reading — most positive first — rather than on
   * the ascending order the text columns start from. Nobody opens a sentiment ranking to see the
   * worst news first, while Symbol and Company are looked up alphabetically, so the two kinds of
   * column legitimately start from opposite ends of their scale.
   */
  const handleSortToggle = (key) => {
    setSortConfig(prev => {
      if (prev.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      return { key, direction: SENTIMENT_BY_KEY[key] ? 'desc' : 'asc' };
    });
  };

  const getSortIcon = (key) => {
    if (sortConfig.key !== key) return '\u21D5';
    return sortConfig.direction === 'asc' ? '\u2191' : '\u2193';
  };

  const handleRowSelectionToggle = (e, code) => {
    e.stopPropagation();
    const updated = new Set(selectedRowIds);
    updated.has(code) ? updated.delete(code) : updated.add(code);
    setSelectedRowIds(updated);
  };

  const handleBulkDeleteClick = () => {
    if (selectedRowIds.size === 0) return;
    const n = selectedRowIds.size;
    if (window.confirm(`Remove ${n} ${n === 1 ? 'company' : 'companies'} from watchlist?`)) {
      onBulkDelete(Array.from(selectedRowIds));
      setSelectedRowIds(new Set());
    }
  };

  const getSerialNumber = (index) => (currentPage - 1) * ROWS_PER_PAGE + index + 1;
  const isRowSelected = (code) => selectedRowIds.has(code);

  if (!entries || entries.length === 0) {
    return (
      <div className="wl-no-data">
        <p>No companies in your watchlist yet.</p>
        <p className="wl-no-data-hint">Click "Add Company" to get started.</p>
      </div>
    );
  }

  return (
    <div className="wl-table-container">
      <div className="wl-toolbar">
        <div className="wl-toolbar-left">
          <span className="wl-result-count">
            {sortedEntries.length} {sortedEntries.length === 1 ? 'company' : 'companies'}
          </span>
        </div>
        {selectedRowIds.size > 0 && (
          <div className="wl-toolbar-right">
            <span className="wl-selected-count">{selectedRowIds.size} selected</span>
            <button className="wl-btn-clear-selection" onClick={() => setSelectedRowIds(new Set())}>Clear</button>
            <button className="wl-btn-bulk-delete" onClick={handleBulkDeleteClick}>Delete Selected</button>
          </div>
        )}
      </div>

      <div className="wl-table-scroll">
        <table className="wl-data-table">
          <thead>
            <tr>
              <th className="wl-th-sno">S.No.</th>
              {TABLE_COLUMNS.map(col => (
                <th key={col.key} onClick={() => handleSortToggle(col.key)} className="wl-sortable" data-tooltip={col.tooltip}>
                  <span className="wl-th-content">
                    {col.label}
                    <span className={`wl-sort-icon ${sortConfig.key === col.key ? 'active' : ''}`}>
                      {getSortIcon(col.key)}
                    </span>
                  </span>
                </th>
              ))}
              {SENTIMENT_COLUMNS.map(col => (
                // The sort hint is appended here rather than written into the shared description,
                // which the Nifty index and sector tables also use for these columns — and
                // neither of those sorts.
                <th
                  key={col.key}
                  onClick={() => handleSortToggle(col.key)}
                  className="wl-th-sentiment wl-sortable"
                  data-tooltip={`${col.tooltip} Click to sort.`}
                >
                  <span className="wl-th-content">
                    {col.label}
                    <span className={`wl-sort-icon ${sortConfig.key === col.key ? 'active' : ''}`}>
                      {getSortIcon(col.key)}
                    </span>
                  </span>
                </th>
              ))}
              <th className="wl-th-actions"></th>
            </tr>
          </thead>
          <tbody>
            {paginatedEntries.map((entry, index) => (
              <tr
                key={entry.companyCode || index}
                onClick={() => onCompanyClick(entry)}
                className={`wl-clickable-row ${isRowSelected(entry.companyCode) ? 'wl-selected-row' : ''}`}
              >
                <td className="wl-sno">{getSerialNumber(index)}</td>
                {TABLE_COLUMNS.map(col => (
                  <td key={col.key} className={col.key === 'companyName' ? 'wl-company-name' : ''}>
                    {formatCellValue(entry[col.key])}
                  </td>
                ))}
                {SENTIMENT_COLUMNS.map(col => (
                  <td key={col.key} className="wl-td-sentiment">
                    <SentimentBadge
                      sentiment={col.read(sentiments[entry.companyCode])}
                      variant={col.variant}
                      compact
                      showScore
                      onClick={(e) => openSentiments(e, entry)}
                    />
                  </td>
                ))}
                <td className="wl-td-actions">
                  <button
                    className={`wl-btn-row-delete ${isRowSelected(entry.companyCode) ? 'selected' : ''}`}
                    onClick={(e) => handleRowSelectionToggle(e, entry.companyCode)}
                    title={isRowSelected(entry.companyCode) ? 'Deselect' : 'Select for deletion'}
                  >
                    <DeleteIcon />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {sortedEntries.length > ROWS_PER_PAGE && (
        <div className="wl-pagination">
          <div className="wl-pagination-info">
            Showing {(currentPage - 1) * ROWS_PER_PAGE + 1} - {Math.min(currentPage * ROWS_PER_PAGE, sortedEntries.length)} of {sortedEntries.length}
          </div>
          <div className="wl-page-buttons">
            <button onClick={() => setCurrentPage(1)} disabled={currentPage === 1} className="wl-page-btn" title="First page">&laquo;</button>
            <button onClick={() => setCurrentPage(p => p - 1)} disabled={currentPage === 1} className="wl-page-btn" title="Previous page">&lsaquo;</button>
            <span className="wl-page-indicator">{currentPage} / {totalPages}</span>
            <button onClick={() => setCurrentPage(p => p + 1)} disabled={currentPage === totalPages} className="wl-page-btn" title="Next page">&rsaquo;</button>
            <button onClick={() => setCurrentPage(totalPages)} disabled={currentPage === totalPages} className="wl-page-btn" title="Last page">&raquo;</button>
          </div>
        </div>
      )}
    </div>
  );
};

export default WatchlistTable;
