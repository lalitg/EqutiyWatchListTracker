import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import WatchlistTable from '../components/watchlist/WatchlistTable';
import AddCompanyModal from '../components/watchlist/AddCompanyModal';
import ImportCsvModal from '../components/watchlist/ImportCsvModal';
import { useWatchlist } from '../context/WatchlistContext';
import { useEntitlements, LIMITS } from '../context/EntitlementsContext';
import { subscribeHref } from '../components/shared/Locked';
import { Link } from 'react-router-dom';
import { WATCHLIST_DESCRIPTIONS as WD } from '../constants/marketDescriptions';
import './WatchlistPage.css';

/**
 * Used until the plan is known, and if it never arrives.
 *
 * The old hard-coded ceiling, kept as the fallback rather than something smaller: a reader whose
 * entitlements could not be read should not suddenly find their tenth company rejected.
 */
const FALLBACK_COMPANIES = 10;

const WatchlistPage = () => {
  const navigate = useNavigate();
  const { limit } = useEntitlements();

  // Both caps are the plan's, not the page's. Nothing on the server enforces them today, which is
  // why a downgrade can leave a reader over the line: the rule is "no new ones", never "we deleted
  // the extras", so what they already have stays readable.
  const maxCompanies  = limit(LIMITS.COMPANIES_PER_WATCHLIST, FALLBACK_COMPANIES);
  const maxWatchlists = limit(LIMITS.WATCHLISTS, -1);
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
  const isFull = maxCompanies >= 0
    && (activeWatchlist?.companyCount ?? entries.length) >= maxCompanies;
  const atListLimit = maxWatchlists >= 0 && watchlists.length >= maxWatchlists;

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">My Watchlists</h1>
        <div className="page-actions">
          <button className="btn btn-secondary" onClick={fetchEntries} data-tooltip={WD['btn.refresh']}>Refresh</button>
          <button className="btn btn-secondary" onClick={() => setImportModalOpen(true)} data-tooltip={WD['btn.importCsv']}>Import CSV</button>
          <button
            className="btn btn-add"
            onClick={() => setAddModalOpen(true)}
            disabled={isFull}
            data-tooltip={isFull
              ? `This watchlist holds the ${maxCompanies} companies your plan allows. Remove one, or see the plans for more room.`
              : WD['btn.addCompany']}
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
            <span
              className={`wl-tab-count ${maxCompanies >= 0 && w.companyCount >= maxCompanies ? 'wl-tab-count--full' : ''}`}
              data-tooltip={`${w.companyCount} of ${maxCompanies} company slots used`}
            >
              {w.companyCount}/{maxCompanies}
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
        ) : atListLimit ? (
          // A link rather than a disabled button: "you cannot" is only half an answer, and the
          // other half is on the pricing page.
          <Link
            className="wl-new-tab-btn wl-new-tab-btn--locked"
            to={subscribeHref('watchlists')}
            data-tooltip={`Your plan includes ${maxWatchlists} ${maxWatchlists === 1 ? 'watchlist' : 'watchlists'}. See the plans for more.`}
          >
            <span aria-hidden="true">&#128274;</span> New Watchlist
          </Link>
        ) : (
          <button className="wl-new-tab-btn" onClick={() => setCreatingList(true)} data-tooltip={WD['btn.newList']}>
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
          /* `tab` lets the table send a click straight to a section of the company page — the
             sentiment badges use it to open the Sentiments tab. The URL is built here rather than
             inside the table so that route shape stays this page's concern and WatchlistTable
             remains presentational, knowing nothing about routing. */
          onCompanyClick={(entry, { tab } = {}) => navigate(
            `/company/${encodeURIComponent(entry.companyCode)}${tab ? `?tab=${tab}` : ''}`
          )}
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
