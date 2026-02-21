import apiClient from './apiClient';
import { getCompanyInsights as getMock } from '../mockData/companyInsightsMock';

export async function fetchCompanyInsights(companyCode) {
  try {
    return await apiClient(`/company/insights/${encodeURIComponent(companyCode)}`);
  } catch {
    return getMock(companyCode);
  }
}
