import React, { createContext, useContext, useReducer, useCallback } from 'react';
import * as marketService from '../services/marketService';

const MarketContext = createContext();

const ACTIONS = {
  GLOBAL_START:    'GLOBAL_START',
  GLOBAL_SUCCESS:  'GLOBAL_SUCCESS',
  GLOBAL_ERROR:    'GLOBAL_ERROR',
  SET_REGION:      'SET_REGION',
  DOMESTIC_START:  'DOMESTIC_START',
  DOMESTIC_SUCCESS: 'DOMESTIC_SUCCESS',
  DOMESTIC_ERROR:  'DOMESTIC_ERROR',
  SET_SECTOR:      'SET_SECTOR',
};

/** How long (ms) cached data is considered fresh before a re-fetch is triggered. */
const CACHE_TTL_MS    = 15 * 60 * 1000; // 15 minutes
/** Minimum staleness (ms) before a tab-focus event triggers a re-fetch. */
const STALE_FOCUS_MS  =  5 * 60 * 1000; //  5 minutes

/**
 * Initial reducer state.
 *
 * globalCache is a plain object keyed by region (e.g. 'US', 'India').
 * Each entry is { news, fetchedAt } where fetchedAt is a Date.now() timestamp.
 * A null/undefined value means data for that region has not been fetched yet.
 * Once fetched, the region key is refreshed either by: manual Refresh button,
 * auto 15-min interval, tab-focus staleness check, or stale tab-switch.
 */
const initialState = {
  globalCache:    {},
  globalLoading:  false,
  globalError:    null,
  selectedRegion: 'Global',
  domestic:       null,
  domesticLoading: false,
  domesticError:  null,
  selectedSector: 'All',
};

function reducer(state, action) {
  switch (action.type) {
    case ACTIONS.GLOBAL_START:
      return { ...state, globalLoading: true, globalError: null };
    case ACTIONS.GLOBAL_SUCCESS:
      return {
        ...state,
        globalLoading: false,
        globalCache: {
          ...state.globalCache,
          [action.payload.region]: { ...action.payload.data, fetchedAt: Date.now() },
        },
      };
    case ACTIONS.GLOBAL_ERROR:
      return { ...state, globalLoading: false, globalError: action.payload };
    case ACTIONS.SET_REGION:
      return { ...state, selectedRegion: action.payload };
    case ACTIONS.DOMESTIC_START:
      return { ...state, domesticLoading: true, domesticError: null };
    case ACTIONS.DOMESTIC_SUCCESS:
      return { ...state, domesticLoading: false, domestic: { ...action.payload, fetchedAt: Date.now() } };
    case ACTIONS.DOMESTIC_ERROR:
      return { ...state, domesticLoading: false, domesticError: action.payload };
    case ACTIONS.SET_SECTOR:
      return { ...state, selectedSector: action.payload };
    default:
      return state;
  }
}

export function MarketProvider({ children }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  /**
   * Fetches global market insights for the given region — cache first.
   *
   * If data for this region is already in globalCache, returns immediately
   * without making any API calls. This ensures tab switches are instant
   * after the first load. To bypass the cache, use refreshGlobal instead.
   *
   * @param {string} region - Region tab key ('US', 'Europe', 'Asia', 'India', 'Global').
   */
  const fetchGlobal = useCallback(async (region) => {
    const r = region || state.selectedRegion;
    const cached = state.globalCache[r];
    const isFresh = cached && (Date.now() - cached.fetchedAt) < CACHE_TTL_MS;
    if (isFresh) {
      console.log(`[MarketContext] Cache hit for region '${r}' — skipping fetch`);
      return;
    }
    console.log(`[MarketContext] Fetching global insights for region '${r}'${cached ? ' (stale cache)' : ''}`);
    dispatch({ type: ACTIONS.GLOBAL_START });
    try {
      const data = await marketService.fetchGlobalInsights(r);
      dispatch({ type: ACTIONS.GLOBAL_SUCCESS, payload: { region: r, data } });
    } catch (err) {
      console.error(`[MarketContext] Failed to fetch global insights for region '${r}':`, err.message);
      dispatch({ type: ACTIONS.GLOBAL_ERROR, payload: err.message });
    }
  }, [state.selectedRegion, state.globalCache]);

  /**
   * Force-refreshes global market insights for the given region, bypassing
   * the cache. Called when the user clicks the Refresh button on GlobalMarketPage.
   * Overwrites the existing cache entry for the region with fresh data.
   *
   * @param {string} region - Region tab key to refresh.
   */
  const refreshGlobal = useCallback(async (region) => {
    const r = region || state.selectedRegion;
    console.log(`[MarketContext] Force-refreshing global insights for region '${r}'`);
    dispatch({ type: ACTIONS.GLOBAL_START });
    try {
      const data = await marketService.fetchGlobalInsights(r);
      dispatch({ type: ACTIONS.GLOBAL_SUCCESS, payload: { region: r, data } });
    } catch (err) {
      console.error(`[MarketContext] Failed to refresh global insights for region '${r}':`, err.message);
      dispatch({ type: ACTIONS.GLOBAL_ERROR, payload: err.message });
    }
  }, [state.selectedRegion]);

  /**
   * Updates the selected region tab.
   *
   * The GlobalMarketPage useEffect triggers fetchGlobal automatically
   * whenever selectedRegion changes, so switching tabs is all that's needed.
   *
   * @param {string} region - The newly selected region tab.
   */
  const setRegion = useCallback((region) => {
    dispatch({ type: ACTIONS.SET_REGION, payload: region });
  }, []);

  /**
   * Fetches domestic market insights for the given sector.
   *
   * Domestic data is not cached — each sector switch triggers a fresh fetch
   * since sector news is lightweight (single keyword, single API call).
   *
   * @param {string} sector - Sector tab key ('All', 'IT', 'Banking', etc.).
   */
  const fetchDomestic = useCallback(async (sector) => {
    const s = sector || state.selectedSector;
    console.log(`[MarketContext] Fetching domestic insights for sector '${s}'`);
    dispatch({ type: ACTIONS.DOMESTIC_START });
    try {
      const data = await marketService.fetchDomesticInsights(s);
      dispatch({ type: ACTIONS.DOMESTIC_SUCCESS, payload: data });
    } catch (err) {
      console.error(`[MarketContext] Failed to fetch domestic insights for sector '${s}':`, err.message);
      dispatch({ type: ACTIONS.DOMESTIC_ERROR, payload: err.message });
    }
  }, [state.selectedSector]);

  /**
   * Force-refreshes domestic market insights for the given sector, bypassing
   * any in-flight guard. Called by the auto-refresh interval and tab-focus
   * listener in DomesticMarketPage.
   *
   * @param {string} sector - Sector tab key to refresh.
   */
  const refreshDomestic = useCallback(async (sector) => {
    const s = sector || state.selectedSector;
    console.log(`[MarketContext] Force-refreshing domestic insights for sector '${s}'`);
    dispatch({ type: ACTIONS.DOMESTIC_START });
    try {
      const data = await marketService.fetchDomesticInsights(s);
      dispatch({ type: ACTIONS.DOMESTIC_SUCCESS, payload: data });
    } catch (err) {
      console.error(`[MarketContext] Failed to refresh domestic insights for sector '${s}':`, err.message);
      dispatch({ type: ACTIONS.DOMESTIC_ERROR, payload: err.message });
    }
  }, [state.selectedSector]);

  /**
   * Updates the selected domestic sector tab.
   *
   * @param {string} sector - The newly selected sector tab.
   */
  const setSector = useCallback((sector) => {
    dispatch({ type: ACTIONS.SET_SECTOR, payload: sector });
  }, []);

  const value = {
    ...state,
    fetchGlobal,
    refreshGlobal,
    setRegion,
    fetchDomestic,
    refreshDomestic,
    setSector,
    CACHE_TTL_MS,
    STALE_FOCUS_MS,
  };

  return (
    <MarketContext.Provider value={value}>
      {children}
    </MarketContext.Provider>
  );
}

/**
 * Custom hook to consume MarketContext.
 * Must be used within a MarketProvider.
 *
 * @returns {object} Market context value containing state and action handlers.
 */
export function useMarket() {
  const ctx = useContext(MarketContext);
  if (!ctx) throw new Error('useMarket must be used within MarketProvider');
  return ctx;
}
