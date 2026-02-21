import apiClient from './apiClient';
import { getGlobalInsights as globalMock, getDomesticInsights as domesticMock } from '../mockData/marketInsightsMock';

export async function fetchGlobalInsights(region = 'US') {
  try {
    return await apiClient(`/market/insights/global?region=${encodeURIComponent(region)}`);
  } catch {
    return globalMock(region);
  }
}

export async function fetchDomesticInsights(sector = 'All') {
  try {
    return await apiClient(`/market/insights/domestic?sector=${encodeURIComponent(sector)}`);
  } catch {
    return domesticMock(sector);
  }
}
