import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import * as watchlistService from '../services/watchlistService';
import { notifySymbolAdded } from '../services/newsService';
import { useAuth } from './AuthContext';

const WatchlistContext = createContext();

export function WatchlistProvider({ children }) {
  const { isLoggedIn } = useAuth();

  const [watchlists, setWatchlists] = useState([]);       // [{id, name, companyCount}, ...]
  const [activeId, setActiveId] = useState(null);          // currently selected watchlist id
  const [entries, setEntries] = useState([]);              // companies in active watchlist
  const [isLoading, setIsLoading] = useState(false);
  const [isActionLoading, setIsActionLoading] = useState(false);
  const [error, setError] = useState(null);

  // ── Reset on logout ──────────────────────────────────────────────────────
  useEffect(() => {
    if (!isLoggedIn) {
      setWatchlists([]);
      setActiveId(null);
      setEntries([]);
      setError(null);
    }
  }, [isLoggedIn]);

  // ── Load all watchlists, then load entries for the first/active one ──────
  const fetchWatchlists = useCallback(async () => {
    if (!isLoggedIn) return;
    setIsLoading(true);
    setError(null);
    try {
      const lists = await watchlistService.getLists();
      setWatchlists(lists);
      if (lists.length > 0) {
        const targetId = activeId && lists.find(l => l.id === activeId) ? activeId : lists[0].id;
        setActiveId(targetId);
        const data = await watchlistService.fetchEntries(targetId);
        setEntries(data);
      } else {
        setActiveId(null);
        setEntries([]);
      }
    } catch (err) {
      setError('Failed to load watchlists. Please make sure the backend server is running.');
    } finally {
      setIsLoading(false);
    }
  }, [isLoggedIn, activeId]);

  // ── Switch active watchlist ──────────────────────────────────────────────
  const switchWatchlist = useCallback(async (id) => {
    if (id === activeId) return;
    setActiveId(id);
    setIsLoading(true);
    setError(null);
    try {
      const data = await watchlistService.fetchEntries(id);
      setEntries(data);
    } catch (err) {
      setError('Failed to load watchlist entries.');
    } finally {
      setIsLoading(false);
    }
  }, [activeId]);

  // ── Reload entries for the current active watchlist ──────────────────────
  const fetchEntries = useCallback(async () => {
    if (!isLoggedIn || !activeId) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await watchlistService.fetchEntries(activeId);
      setEntries(data);
    } catch (err) {
      setError('Failed to load data. Please make sure the backend server is running.');
    } finally {
      setIsLoading(false);
    }
  }, [isLoggedIn, activeId]);

  // ── Create a new watchlist ───────────────────────────────────────────────
  const createWatchlist = useCallback(async (name) => {
    if (!isLoggedIn) return;
    setIsActionLoading(true);
    try {
      const created = await watchlistService.createList(name);
      const updatedLists = await watchlistService.getLists();
      setWatchlists(updatedLists);
      setActiveId(created.id);
      setEntries([]);
    } catch (err) {
      throw err;
    } finally {
      setIsActionLoading(false);
    }
  }, [isLoggedIn]);

  // ── Delete a watchlist ───────────────────────────────────────────────────
  const deleteWatchlist = useCallback(async (id) => {
    setIsActionLoading(true);
    try {
      await watchlistService.deleteList(id);
      const updatedLists = await watchlistService.getLists();
      setWatchlists(updatedLists);
      if (id === activeId) {
        if (updatedLists.length > 0) {
          setActiveId(updatedLists[0].id);
          const data = await watchlistService.fetchEntries(updatedLists[0].id);
          setEntries(data);
        } else {
          setActiveId(null);
          setEntries([]);
        }
      }
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      setIsActionLoading(false);
    }
  }, [activeId]);

  // ── Add company to active watchlist ──────────────────────────────────────
  const addCompany = useCallback(async (companyCode, watchlistId) => {
    if (!isLoggedIn) {
      alert('Please log in to add companies to your watchlist.');
      return;
    }
    const targetId = watchlistId ?? activeId;
    setIsActionLoading(true);
    try {
      await watchlistService.addCompany(companyCode, targetId);
      notifySymbolAdded(companyCode);
      const [data, lists] = await Promise.all([
        watchlistService.fetchEntries(targetId),
        watchlistService.getLists(),
      ]);
      setEntries(data);
      setWatchlists(lists);
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      setIsActionLoading(false);
    }
  }, [isLoggedIn, activeId]);

  // ── Update company in active watchlist ───────────────────────────────────
  const updateCompany = useCallback(async (oldCode, newCode) => {
    setIsActionLoading(true);
    try {
      await watchlistService.updateCompany(oldCode, newCode, activeId);
      const data = await watchlistService.fetchEntries(activeId);
      setEntries(data);
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      setIsActionLoading(false);
    }
  }, [activeId]);

  // ── Delete company from active watchlist ─────────────────────────────────
  const deleteCompany = useCallback(async (companyCode) => {
    setIsActionLoading(true);
    try {
      await watchlistService.deleteCompany(companyCode, activeId);
      const [data, lists] = await Promise.all([
        watchlistService.fetchEntries(activeId),
        watchlistService.getLists(),
      ]);
      setEntries(data);
      setWatchlists(lists);
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      setIsActionLoading(false);
    }
  }, [activeId]);

  // ── Bulk delete from active watchlist ────────────────────────────────────
  const bulkDelete = useCallback(async (companyCodes) => {
    setIsActionLoading(true);
    try {
      for (const code of companyCodes) {
        await watchlistService.deleteCompany(code, activeId);
      }
      const [data, lists] = await Promise.all([
        watchlistService.fetchEntries(activeId),
        watchlistService.getLists(),
      ]);
      setEntries(data);
      setWatchlists(lists);
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      setIsActionLoading(false);
    }
  }, [activeId]);

  // ── Import companies into active watchlist ───────────────────────────────
  const importCompanies = useCallback(async (companyCodes, mode) => {
    setIsActionLoading(true);
    try {
      const result = await watchlistService.importCompanies(companyCodes, mode, activeId);
      companyCodes.forEach(notifySymbolAdded);
      const [data, lists] = await Promise.all([
        watchlistService.fetchEntries(activeId),
        watchlistService.getLists(),
      ]);
      setEntries(data);
      setWatchlists(lists);
      return result;
    } catch (err) {
      throw err;
    } finally {
      setIsActionLoading(false);
    }
  }, [activeId]);

  const value = {
    watchlists,
    activeId,
    entries,
    isLoading,
    isActionLoading,
    error,
    fetchWatchlists,
    fetchEntries,
    switchWatchlist,
    createWatchlist,
    deleteWatchlist,
    addCompany,
    updateCompany,
    deleteCompany,
    bulkDelete,
    importCompanies,
  };

  return (
    <WatchlistContext.Provider value={value}>
      {children}
    </WatchlistContext.Provider>
  );
}

export function useWatchlist() {
  const ctx = useContext(WatchlistContext);
  if (!ctx) throw new Error('useWatchlist must be used within WatchlistProvider');
  return ctx;
}
