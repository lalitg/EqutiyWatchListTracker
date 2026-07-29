export async function fetchAmfiFiles() {
  const res = await fetch('/api/amfi/files');
  if (!res.ok) throw new Error(`AMFI files fetch failed: ${res.status}`);
  return res.json();
}

export function amfiNoteUrl(month) {
  return `/api/amfi/files/${month}/note`;
}

export function amfiReportUrl(month) {
  return `/api/amfi/files/${month}/report`;
}
