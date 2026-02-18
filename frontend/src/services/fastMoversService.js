import apiClient from './apiClient';
import { getFastMovers as getMock } from '../mockData/fastMoversMock';

export async function fetchFastMovers(period = '1D') {
  try {
    return await apiClient(`/market/fast-movers?period=${encodeURIComponent(period)}`);
  } catch {
    return getMock(period);
  }
}
