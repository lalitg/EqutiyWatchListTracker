import { apiFetch } from './apiClient';

export async function fetchEvents(symbol) {
  return apiFetch(`/api/events?key=${encodeURIComponent(symbol)}`);
}

/**
 * The macro calendar — RBI MPC, FOMC, CPI, NFP, Budget and the rest.
 *
 * Part of the Basic plan, so the request has to carry the reader's token; it was being made with
 * a bare fetch, which the gateway saw as anonymous and refused.
 */
export async function fetchMacroEvents() {
  return apiFetch('/api/macro-events?upcoming=true');
}
