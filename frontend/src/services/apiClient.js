const API_BASE = '/api/v1';

async function apiClient(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;
  const config = {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  };

  const response = await fetch(url, config);

  if (!response.ok) {
    let errorMessage = `HTTP ${response.status}`;
    try {
      const errorData = await response.json();
      errorMessage = errorData.error || errorMessage;
    } catch {
      // response body wasn't JSON
    }
    throw new Error(errorMessage);
  }

  // 204 No Content
  if (response.status === 204) return null;
  return response.json();
}

export default apiClient;
