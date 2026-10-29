import { apiFetch } from './apiClient';

export async function fetchSebiActivity(symbol) {
  return apiFetch(`/api/sebi/company/${encodeURIComponent(symbol)}`);
}
