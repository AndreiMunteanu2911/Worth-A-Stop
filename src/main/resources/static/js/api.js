export async function requestJson(url, {method = 'GET', payload} = {}) {
    const headers = {Accept: 'application/json'};
    if (method !== 'GET') {
        const csrfHeader = document.querySelector('meta[name="csrf-header"]').content;
        headers[csrfHeader] = document.querySelector('meta[name="csrf-token"]').content;
    }
    if (payload !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch(url, {
        method, headers, cache: 'no-store', credentials: 'same-origin',
        body: payload === undefined ? undefined : JSON.stringify(payload)
    });
    if (!response.ok) {
        const problem = await response.json().catch(() => null);
        throw new Error(problem?.detail || 'The request failed. Please try again.');
    }
    if (response.status !== 204) return response.json();
}
