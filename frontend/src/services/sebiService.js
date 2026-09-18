export async function fetchSebiActivity(symbol) {
  const res = await fetch(`/api/sebi/company/${symbol}`);
  if (!res.ok) throw new Error(`SEBI fetch failed: ${res.status}`);
  return res.json();
}
