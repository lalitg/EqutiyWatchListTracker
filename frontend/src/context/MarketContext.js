import React, { createContext, useContext, useReducer, useCallback } from 'react';
import * as marketService from '../services/marketService';

const MarketContext = createContext();

const ACTIONS = {
  GLOBAL_START: 'GLOBAL_START',
  GLOBAL_SUCCESS: 'GLOBAL_SUCCESS',
  GLOBAL_ERROR: 'GLOBAL_ERROR',
  SET_REGION: 'SET_REGION',
  DOMESTIC_START: 'DOMESTIC_START',
  DOMESTIC_SUCCESS: 'DOMESTIC_SUCCESS',
  DOMESTIC_ERROR: 'DOMESTIC_ERROR',
  SET_SECTOR: 'SET_SECTOR',
};

const initialState = {
  global: null,
  globalLoading: false,
  globalError: null,
  selectedRegion: 'US',
  domestic: null,
  domesticLoading: false,
  domesticError: null,
  selectedSector: 'All',
};

function reducer(state, action) {
  switch (action.type) {
    case ACTIONS.GLOBAL_START:
      return { ...state, globalLoading: true, globalError: null };
    case ACTIONS.GLOBAL_SUCCESS:
      return { ...state, globalLoading: false, global: action.payload };
    case ACTIONS.GLOBAL_ERROR:
      return { ...state, globalLoading: false, globalError: action.payload };
    case ACTIONS.SET_REGION:
      return { ...state, selectedRegion: action.payload };
    case ACTIONS.DOMESTIC_START:
      return { ...state, domesticLoading: true, domesticError: null };
    case ACTIONS.DOMESTIC_SUCCESS:
      return { ...state, domesticLoading: false, domestic: action.payload };
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

  const fetchGlobal = useCallback(async (region) => {
    const r = region || state.selectedRegion;
    dispatch({ type: ACTIONS.GLOBAL_START });
    try {
      const data = await marketService.fetchGlobalInsights(r);
      dispatch({ type: ACTIONS.GLOBAL_SUCCESS, payload: data });
    } catch (err) {
      dispatch({ type: ACTIONS.GLOBAL_ERROR, payload: err.message });
    }
  }, [state.selectedRegion]);

  const setRegion = useCallback((region) => {
    dispatch({ type: ACTIONS.SET_REGION, payload: region });
  }, []);

  const fetchDomestic = useCallback(async (sector) => {
    const s = sector || state.selectedSector;
    dispatch({ type: ACTIONS.DOMESTIC_START });
    try {
      const data = await marketService.fetchDomesticInsights(s);
      dispatch({ type: ACTIONS.DOMESTIC_SUCCESS, payload: data });
    } catch (err) {
      dispatch({ type: ACTIONS.DOMESTIC_ERROR, payload: err.message });
    }
  }, [state.selectedSector]);

  const setSector = useCallback((sector) => {
    dispatch({ type: ACTIONS.SET_SECTOR, payload: sector });
  }, []);

  const value = {
    ...state,
    fetchGlobal,
    setRegion,
    fetchDomestic,
    setSector,
  };

  return (
    <MarketContext.Provider value={value}>
      {children}
    </MarketContext.Provider>
  );
}

export function useMarket() {
  const ctx = useContext(MarketContext);
  if (!ctx) throw new Error('useMarket must be used within MarketProvider');
  return ctx;
}
