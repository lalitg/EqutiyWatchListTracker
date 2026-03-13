import React, { useState, useMemo, useEffect } from 'react';
import './WatchlistTable.css';

const TABLE_COLUMNS = [
  { key: 'companyId', label: 'Company ID', sortable: true },
  { key: 'companyName', label: 'Company', sortable: true },
  { key: 'week52Low', label: '52 Week Low', sortable: true },
  { key: 'week52High', label: '52 Week High', sortable: true },
  { key: 'allTimeLow', label: 'All Time Low', sortable: true },
  { key: 'allTimeHigh', label: 'All Time High', sortable: true },
  { key: 'currentValue', label: 'Current Value', sortable: true },
  { key: 'tradedVolume', label: 'Traded Volume', sortable: true },
];

const ROWS_PER_PAGE = 10;

const DeleteIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="3 6 5 6 21 6"/>
    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
  </svg>
);

const WatchlistTable = ({ entries, onCompanyClick, onBulkDelete }) => {
  const [sortConfig, setSortConfig] = useState({ key: null, direction: 'asc' });
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedRowIds, setSelectedRowIds] = useState(new Set());

  useEffect(() => { setCurrentPage(1); }, [entries.length]);
  useEffect(() => { setSelectedRowIds(new Set()); }, [entries]);

  const formatCellValue = (value) => {
    if (value === null || value === undefined || value === '') return '-';
    return value;
  };

  const filteredEntries = useMemo(() => {
    if (!searchQuery.trim()) return entries;
    const q = searchQuery.toLowerCase();
    return entries.filter(e =>
      e.companyCode?.toLowerCase().includes(q) ||
      e.companyName?.toLowerCase().includes(q)
    );
  }, [entries, searchQuery]);

  const sortedEntries = useMemo(() => {
    if (!sortConfig.key) return filteredEntries;
    return [...filteredEntries].sort((a, b) => {
      const av = a[sortConfig.key] ?? '';
      const bv = b[sortConfig.key] ?? '';
      if (av < bv) return sortConfig.direction === 'asc' ? -1 : 1;
      if (av > bv) return sortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
  }, [filteredEntries, sortConfig]);

  const paginatedEntries = useMemo(() => {
    const start = (currentPage - 1) * ROWS_PER_PAGE;
    return sortedEntries.slice(start, start + ROWS_PER_PAGE);
  }, [sortedEntries, currentPage]);

  const totalPages = Math.ceil(sortedEntries.length / ROWS_PER_PAGE);

  const handleSortToggle = (key) => {
    setSortConfig(prev => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  const getSortIcon = (key) => {
    if (sortConfig.key !== key) return '\u21D5';
    return sortConfig.direction === 'asc' ? '\u2191' : '\u2193';
  };

  const handleSearchChange = (e) => { setSearchQuery(e.target.value); setCurrentPage(1); };
  const handleSearchClear = () => setSearchQuery('');

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
          <div className="wl-search-box">
            <input
              type="text"
              placeholder="Search..."
              value={searchQuery}
              onChange={handleSearchChange}
              className="wl-search-input"
            />
            {searchQuery && (
              <button className="wl-search-clear" onClick={handleSearchClear}>x</button>
            )}
          </div>
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
                <th key={col.key} onClick={() => handleSortToggle(col.key)} className="wl-sortable">
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
