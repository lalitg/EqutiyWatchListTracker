import apiClient from './apiClient';

const BASE = '/watchlist';

export function fetchEntries() {
  return apiClient(`${BASE}/getAllCompanies`);
}

export function addCompany(companyCode) {
  return apiClient(`${BASE}/addCompany`, {
    method: 'POST',
    body: JSON.stringify({ companyCode }),
  });
}

export function updateCompany(oldCode, newCode) {
  return apiClient(`${BASE}/updateCompany/${encodeURIComponent(oldCode)}`, {
    method: 'PUT',
    body: JSON.stringify({ companyCode: newCode }),
  });
}

export function deleteCompany(companyCode) {
  return apiClient(`${BASE}/deleteCompany/${encodeURIComponent(companyCode)}`, {
    method: 'DELETE',
  });
}

export function importCompanies(companyCodes) {
  return apiClient(`${BASE}/import`, {
    method: 'POST',
    body: JSON.stringify({ companyCodes }),
  });
}
