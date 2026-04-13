export async function fetchFastMovers(range = 'TODAY') {
  const response = await fetch(`/api/fast-movers?range=${encodeURIComponent(range)}`, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response.json();
}
