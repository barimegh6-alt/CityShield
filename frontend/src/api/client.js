const BASE = import.meta.env.VITE_API_BASE || '/api/v1';

export class ApiError extends Error {
  constructor(message, status = 0, code = null, details = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export async function api(method, path, body) {
  let res;
  try {
    res = await fetch(BASE + path, {
      method,
      headers: { Accept: 'application/json', ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw new ApiError('Cannot reach the CityShield backend (is it running on port 8080?)', 0, 'NETWORK');
  }
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }
  if (!res.ok) {
    const d = data && typeof data === 'object' ? data : {};
    throw new ApiError(
      d.message || `Request failed (HTTP ${res.status})`,
      res.status,
      d.code || null,
      Array.isArray(d.details) ? d.details : d.details ? [String(d.details)] : []
    );
  }
  return data;
}

export const get = (path, params) => {
  let qs = '';
  if (params) {
    const sp = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') sp.append(k, v);
    });
    const s = sp.toString();
    if (s) qs = (path.includes('?') ? '&' : '?') + s;
  }
  return api('GET', path + qs);
};
export const post = (path, body) => api('POST', path, body === undefined ? {} : body);
export const put = (path, body) => api('PUT', path, body);
export const patch = (path, body) => api('PATCH', path, body === undefined ? {} : body);
export const del = (path) => api('DELETE', path);
