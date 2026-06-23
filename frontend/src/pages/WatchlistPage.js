import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import WatchlistTable from '../components/watchlist/WatchlistTable';
import AddCompanyModal from '../components/watchlist/AddCompanyModal';
import ImportCsvModal from '../components/watchlist/ImportCsvModal';
import { useWatchlist } from '../context/WatchlistContext';
import './WatchlistPage.css';

const MAX_COMPANIES = 10;

const WatchlistPage = () => {
  const navigate = useNavigate();
  const {
    watchlists, activeId, entries, isLoading, error, isActionLoading,
    fetchWatchlists, fetchEntries, switchWatchlist,
    createWatchlist, deleteWatchlist,
    addCompany, bulkDelete, importCompanies,
  } = useWatchlist();

  const [addModalOpen, setAddModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [newListName, setNewListName] = useState('');
  const [creatingList, setCreatingList] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState(null);

  useEffect(() => {
    fetchWatchlists();
    const t = setInterval(fetchEntries, 5 * 60 * 1000);
    return () => clearInterval(t);
  }, [fetchWatchlists, fetchEntries]);

  const handleAdd = async (code) => {
    await addCompany(code, activeId);
    setAddModalOpen(false);
  };

  const handleCreateList = async (e) => {
    e.preventDefault();
    if (!newListName.trim()) return;
    try {
      await createWatchlist(newListName.trim());
      setNewListName('');
      setCreatingList(false);
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const handleDeleteList = async (id) => {
    await deleteWatchlist(id);
    setDeleteConfirmId(null);
  };

  const activeWatchlist = watchlists.find(w => w.id === activeId);
  const isFull = (activeWatchlist?.companyCount ?? entries.length) >= MAX_COMPANIES;

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">My Watchlists</h1>
        <div className="page-actions">
          <button className="btn btn-secondary" onClick={fetchEntries}>Refresh</button>
          <button className="btn btn-secondary" onClick={() => setImportModalOpen(true)}>Import CSV</button>
          <button
            className="btn btn-add"
            onClick={() => setAddModalOpen(true)}
            disabled={isFull}
            title={isFull ? `Watchlist is full (${MAX_COMPANIES}/${MAX_COMPANIES})` : '+ Add Company'}
          >
            + Add Company
          </button>
        </div>
      </div>

      {/* Watchlist tabs */}
      <div className="wl-tabs-row">
        {watchlists.map(w => (
          <div
            key={w.id}
            className={`wl-tab ${w.id === activeId ? 'wl-tab--active' : ''}`}
            onClick={() => switchWatchlist(w.id)}
          >
            <span className="wl-tab-name">{w.name}</span>
            <span className={`wl-tab-count ${w.companyCount >= MAX_COMPANIES ? 'wl-tab-count--full' : ''}`}>
              {w.companyCount}/{MAX_COMPANIES}
            </span>
            {watchlists.length > 1 && (
              <button
                className="wl-tab-delete"
                title="Delete watchlist"
                onClick={(e) => { e.stopPropagation(); setDeleteConfirmId(w.id); }}
              >
                ×
              </button>
            )}
          </div>
        ))}

        {creatingList ? (
          <form className="wl-new-form" onSubmit={handleCreateList}>
            <input
              className="wl-new-input"
              autoFocus
              value={newListName}
              onChange={e => setNewListName(e.target.value)}
              placeholder="Watchlist name"
              maxLength={50}
            />
            <button className="btn btn-add" type="submit" disabled={isActionLoading}>
              {isActionLoading ? '...' : 'Create'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => { setCreatingList(false); setNewListName(''); }}
            >
              Cancel
            </button>
          </form>
        ) : (
          <button className="wl-new-tab-btn" onClick={() => setCreatingList(true)}>
            + New Watchlist
          </button>
        )}
      </div>

      {/* Delete confirmation inline banner */}
      {deleteConfirmId && (
        <div className="wl-delete-confirm">
          <span>Delete "{watchlists.find(w => w.id === deleteConfirmId)?.name}"? All {watchlists.find(w => w.id === deleteConfirmId)?.companyCount} companies will be removed.</span>
          <button className="btn btn-danger" onClick={() => handleDeleteList(deleteConfirmId)}>Delete</button>
          <button className="btn btn-secondary" onClick={() => setDeleteConfirmId(null)}>Cancel</button>
        </div>
      )}

      {isLoading && (
        <div className="page-loading"><p>Loading data...</p></div>
      )}

      {error && (
        <div className="page-error">
          <p>{error}</p>
          <button onClick={fetchWatchlists} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {!isLoading && !error && (
        <WatchlistTable
          entries={entries}
          onCompanyClick={(entry) => navigate(`/company/${entry.companyCode}`)}
          onBulkDelete={bulkDelete}
        />
      )}

      <AddCompanyModal
        isOpen={addModalOpen}
        onClose={() => setAddModalOpen(false)}
        onAdd={handleAdd}
        isLoading={isActionLoading}
      />

      <ImportCsvModal
        isOpen={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onImport={importCompanies}
        isLoading={isActionLoading}
      />
    </div>
  );
};

export default WatchlistPage;
