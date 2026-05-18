import React, { createContext, useContext, useReducer, useCallback, useEffect } from 'react';
import * as watchlistService from '../services/watchlistService';
import { notifySymbolAdded } from '../services/newsService';
import { useAuth } from './AuthContext';

const WatchlistContext = createContext();

const ACTIONS = {
  FETCH_START: 'FETCH_START',
  FETCH_SUCCESS: 'FETCH_SUCCESS',
  FETCH_ERROR: 'FETCH_ERROR',
  ACTION_START: 'ACTION_START',
  ACTION_END: 'ACTION_END',
};

const initialState = {
  entries: [],
  isLoading: false,
  error: null,
  isActionLoading: false,
};

function reducer(state, action) {
  switch (action.type) {
    case ACTIONS.FETCH_START:
      return { ...state, isLoading: true, error: null };
    case ACTIONS.FETCH_SUCCESS:
      return { ...state, isLoading: false, entries: action.payload };
    case ACTIONS.FETCH_ERROR:
      return { ...state, isLoading: false, error: action.payload };
    case ACTIONS.ACTION_START:
      return { ...state, isActionLoading: true };
    case ACTIONS.ACTION_END:
      return { ...state, isActionLoading: false };
    default:
      return state;
  }
}

export function WatchlistProvider({ children }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const { isLoggedIn } = useAuth();

  // Clear entries when user logs out
  useEffect(() => {
    if (!isLoggedIn) {
      dispatch({ type: ACTIONS.FETCH_SUCCESS, payload: [] });
    }
  }, [isLoggedIn]);

  const fetchEntries = useCallback(async () => {
    if (!isLoggedIn) return;
    dispatch({ type: ACTIONS.FETCH_START });
    try {
      const data = await watchlistService.fetchEntries();
      dispatch({ type: ACTIONS.FETCH_SUCCESS, payload: data });
    } catch (err) {
      dispatch({ type: ACTIONS.FETCH_ERROR, payload: 'Failed to load data. Please make sure the backend server is running.' });
    }
  }, [isLoggedIn]);

  const addCompany = useCallback(async (companyCode) => {
    if (!isLoggedIn) {
      alert('Please log in to add companies to your watchlist.');
      return;
    }
    dispatch({ type: ACTIONS.ACTION_START });
    try {
      await watchlistService.addCompany(companyCode);
      notifySymbolAdded(companyCode);
      await fetchEntries();
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      dispatch({ type: ACTIONS.ACTION_END });
    }
  }, [fetchEntries, isLoggedIn]);

  const updateCompany = useCallback(async (oldCode, newCode) => {
    dispatch({ type: ACTIONS.ACTION_START });
    try {
      await watchlistService.updateCompany(oldCode, newCode);
      await fetchEntries();
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      dispatch({ type: ACTIONS.ACTION_END });
    }
  }, [fetchEntries]);

  const deleteCompany = useCallback(async (companyCode) => {
    dispatch({ type: ACTIONS.ACTION_START });
    try {
      await watchlistService.deleteCompany(companyCode);
      await fetchEntries();
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      dispatch({ type: ACTIONS.ACTION_END });
    }
  }, [fetchEntries]);

  const bulkDelete = useCallback(async (companyCodes) => {
    dispatch({ type: ACTIONS.ACTION_START });
    try {
      for (const code of companyCodes) {
        await watchlistService.deleteCompany(code);
      }
      await fetchEntries();
    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      dispatch({ type: ACTIONS.ACTION_END });
    }
  }, [fetchEntries]);

  const importCompanies = useCallback(async (companyCodes, mode) => {
    dispatch({ type: ACTIONS.ACTION_START });
    try {
      const result = await watchlistService.importCompanies(companyCodes, mode);
      companyCodes.forEach(notifySymbolAdded);
      await fetchEntries();
      return result;
    } catch (err) {
      throw err;
    } finally {
      dispatch({ type: ACTIONS.ACTION_END });
    }
  }, [fetchEntries]);

  const value = {
    ...state,
    fetchEntries,
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
