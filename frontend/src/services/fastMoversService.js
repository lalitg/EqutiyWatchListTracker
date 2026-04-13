export async function fetchFastMovers(range = 'TODAY') {
  const response = await fetch(`/api/fast-movers?range=${encodeURIComponent(range)}`);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response.json();
}
