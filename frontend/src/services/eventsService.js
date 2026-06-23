export async function fetchEvents(symbol) {
  const response = await fetch(`/api/events?key=${encodeURIComponent(symbol)}`);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}
