import React, { useEffect, useState } from 'react';
import WatchlistTable from '../components/watchlist/WatchlistTable';
import AddCompanyModal from '../components/watchlist/AddCompanyModal';
import CompanyInsightsModal from '../components/watchlist/CompanyInsightsModal';
import { useWatchlist } from '../context/WatchlistContext';

const WatchlistPage = () => {
  const {
    entries, isLoading, error, isActionLoading,
    fetchEntries, addCompany, bulkDelete,
  } = useWatchlist();

  const [addModalOpen, setAddModalOpen] = useState(false);
  const [insightsEntry, setInsightsEntry] = useState(null);

  useEffect(() => { fetchEntries(); }, [fetchEntries]);

  const handleAdd = async (code) => {
    await addCompany(code);
    setAddModalOpen(false);
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">My Watchlist</h1>
        <div className="page-actions">
          <button className="btn btn-secondary" onClick={fetchEntries}>Refresh</button>
          <button className="btn btn-add" onClick={() => setAddModalOpen(true)}>+ Add Company</button>
        </div>
      </div>

      {isLoading && (
        <div className="page-loading">
          <p>Loading data...</p>
        </div>
      )}

      {error && (
        <div className="page-error">
          <p>{error}</p>
          <button onClick={fetchEntries} className="btn btn-primary" style={{ marginTop: 16 }}>Retry</button>
        </div>
      )}

      {!isLoading && !error && (
        <WatchlistTable
          entries={entries}
          onCompanyClick={(entry) => setInsightsEntry(entry)}
          onBulkDelete={bulkDelete}
        />
      )}

      <AddCompanyModal
        isOpen={addModalOpen}
        onClose={() => setAddModalOpen(false)}
        onAdd={handleAdd}
        isLoading={isActionLoading}
      />

      <CompanyInsightsModal
        isOpen={!!insightsEntry}
        onClose={() => setInsightsEntry(null)}
        entry={insightsEntry}
      />
    </div>
  );
};

export default WatchlistPage;
