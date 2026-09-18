import apiClient from './apiClient';

export async function fetchCompanyInsights(companyCode) {
  return apiClient(`/company/insights/${encodeURIComponent(companyCode)}`);
}
