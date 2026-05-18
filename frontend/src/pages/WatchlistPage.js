import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import WatchlistTable from '../components/watchlist/WatchlistTable';
import AddCompanyModal from '../components/watchlist/AddCompanyModal';
import ImportCsvModal from '../components/watchlist/ImportCsvModal';
import { useWatchlist } from '../context/WatchlistContext';

const WatchlistPage = () => {
  const navigate = useNavigate();
  const {
    entries, isLoading, error, isActionLoading,
    fetchEntries, addCompany, bulkDelete, importCompanies,
  } = useWatchlist();

  const [addModalOpen, setAddModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);

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
          <button className="btn btn-secondary" onClick={() => setImportModalOpen(true)}>Import CSV</button>
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
