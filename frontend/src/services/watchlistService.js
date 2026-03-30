import apiClient from './apiClient';

const BASE = '/watchlist';

export function fetchEntries() {
  return apiClient(BASE);
}

export function addCompany(companyCode) {
  return apiClient(BASE, {
    method: 'POST',
    body: JSON.stringify({ companyCode }),
  });
}

export function updateCompany(oldCode, newCode) {
  return apiClient(`${BASE}/${encodeURIComponent(oldCode)}`, {
    method: 'PUT',
    body: JSON.stringify({ companyCode: newCode }),
  });
}

export function deleteCompany(companyCode) {
  return apiClient(`${BASE}/${encodeURIComponent(companyCode)}`, {
    method: 'DELETE',
  });
}

export function importCompanies(companyCodes) {
  return apiClient(`${BASE}/import`, {
    method: 'POST',
    body: JSON.stringify({ companyCodes }),
  });
}
