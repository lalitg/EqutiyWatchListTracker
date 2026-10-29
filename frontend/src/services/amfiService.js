import { apiFetch, apiFetchRaw } from './apiClient';

export async function fetchAmfiFiles() {
  return apiFetch('/api/amfi/files');
}

/**
 * The filename the server chose, so a saved file is not called "report" with no extension.
 *
 * @param {Response} response
 * @param {string} fallback used when the header is absent or unparseable
 */
function filenameFrom(response, fallback) {
  const disposition = response.headers.get('Content-Disposition') || '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return match ? decodeURIComponent(match[1]) : fallback;
}

/**
 * Fetches one AMFI document as bytes.
 *
 * These were plain links until the documents moved behind the paywall. A link is a browser
 * navigation, and a navigation carries no Authorization header — so every click was refused with
 * 403 no matter which plan the reader was on. Fetching the bytes ourselves is what lets the
 * request carry the token; the object URL that follows is only a way to hand the finished file
 * to the browser.
 */
async function fetchAmfiDocument(path, fallbackName) {
  const response = await apiFetchRaw(path);

  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const body = await response.json();
      message = body.error || body.message || message;
    } catch {
      // not JSON — the status is all we have
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return { blob: await response.blob(), filename: filenameFrom(response, fallbackName) };
}

function saveBlob(url, filename) {
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
}

/**
 * Opens the monthly note in a tab the caller already opened, or saves it if that tab is gone.
 *
 * The tab has to be opened by the click itself and passed in here: opening one after the document
 * has been fetched happens outside the user gesture, and a popup blocker stops it.
 *
 * @param {string} month YYYY-MM
 * @param {Window|null} tab a window opened synchronously in the click handler
 */
export async function openAmfiNote(month, tab) {
  const { blob, filename } =
    await fetchAmfiDocument(`/api/amfi/files/${month}/note`, `amfi-note-${month}.pdf`);

  const url = URL.createObjectURL(blob);
  if (tab && !tab.closed) {
    tab.location = url;
  } else {
    saveBlob(url, filename);
  }
  // Revoked late, and never straight away: the tab is still loading from this URL, and pulling it
  // out from under the viewer leaves a blank page.
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

/**
 * Saves the monthly data report.
 *
 * @param {string} month YYYY-MM
 */
export async function downloadAmfiReport(month) {
  const { blob, filename } =
    await fetchAmfiDocument(`/api/amfi/files/${month}/report`, `amfi-report-${month}.xls`);

  const url = URL.createObjectURL(blob);
  saveBlob(url, filename);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
