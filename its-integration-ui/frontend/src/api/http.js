import { apiKeyHeaders } from '../clientSettings';

const API_BASE = '/api/v1/admin';
const RUNTIME_API_BASE = '/api/v1/integrations';

/**
 * @param {string} path
 * @param {RequestInit} [init]
 */
export async function apiFetch(path, init = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      Accept: 'application/json',
      ...apiKeyHeaders('admin'),
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers || {}),
    },
    ...init,
  });
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const err = await response.json();
      message = err.message || message;
    } catch {
      message = (await response.text()) || message;
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

/**
 * @param {string} path
 * @param {RequestInit} [init]
 */
async function runtimeFetch(path, init = {}) {
  const response = await fetch(`${RUNTIME_API_BASE}${path}`, {
    headers: {
      Accept: 'application/json',
      ...apiKeyHeaders('runtime'),
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers || {}),
    },
    ...init,
  });
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const err = await response.json();
      message = err.message || message;
    } catch {
      message = (await response.text()) || message;
    }
    throw new Error(message);
  }
  return response.json();
}

export function listConnectors() {
  return apiFetch('/connectors');
}

export function getConnector(code3rd) {
  return apiFetch(`/connectors/${encodeURIComponent(code3rd)}`);
}

/**
 * @param {object} body SaveConnectorConfigRequest
 */
export function createConnector(body) {
  return apiFetch('/connectors', { method: 'POST', body: JSON.stringify(body) });
}

/**
 * @param {string} code3rd
 * @param {object} body
 */
export function saveConnector(code3rd, body) {
  return apiFetch(`/connectors/${encodeURIComponent(code3rd)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

/**
 * @param {string} code3rd
 * @param {object} body
 */
export function publishConnector(code3rd, body) {
  return apiFetch(`/connectors/${encodeURIComponent(code3rd)}/publish`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function deleteConnector(code3rd) {
  return apiFetch(`/connectors/${encodeURIComponent(code3rd)}`, { method: 'DELETE' });
}

/** 从本地库重新加载已发布配置到运行时注册表 */
export function reloadFromStore() {
  return apiFetch('/sync', { method: 'POST' });
}

export function listProfiles() {
  return apiFetch('/profiles');
}

export function getConsoleInfo() {
  return apiFetch('/console-info');
}

export function getProfile(profileId) {
  return apiFetch(`/profiles/${encodeURIComponent(profileId)}`);
}

/** 按 Spec 端点 id 调用（推荐） */
export function invokeEndpoint(code3rd, endpointId, body = {}) {
  return runtimeFetch(
    `/${encodeURIComponent(code3rd)}/endpoints/${encodeURIComponent(endpointId)}/invoke`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}

/** 列出已登记端点 */
export function listConnectorEndpoints(code3rd) {
  return runtimeFetch(`/${encodeURIComponent(code3rd)}/endpoints`);
}

/** 管理端试调（允许 method+path，走 Admin API） */
export function trialInvokeAdmin(code3rd, body) {
  if (body?.endpointId && !body?.path && !body?.uri && !body?.method) {
    const { endpointId, query, headers, body: reqBody } = body;
    return apiFetch(`/connectors/${encodeURIComponent(code3rd)}/trial/invoke`, {
      method: 'POST',
      body: JSON.stringify({
        endpointId,
        query: query || {},
        headers: headers || {},
        body: typeof reqBody === 'string' ? reqBody : reqBody ? JSON.stringify(reqBody) : null,
      }),
    }).then((data) => ({ httpStatus: data.httpStatus ?? data.vendorHttpStatus, body: data }));
  }
  const payload = { ...body };
  if (payload.uri && !payload.path) {
    payload.path = payload.uri;
    delete payload.uri;
  }
  return apiFetch(`/connectors/${encodeURIComponent(code3rd)}/trial/invoke`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }).then((data) => ({ httpStatus: data.httpStatus ?? data.vendorHttpStatus, body: data }));
}

/** 通用 invoke（兼容试调，优先走 Admin trial） */
export function trialProxy(code3rd, body) {
  return trialInvokeAdmin(code3rd, body);
}

/** @deprecated 使用 trialInvokeAdmin */
export function trialProxyRuntime(code3rd, body) {
  if (body?.endpointId && !body?.path && !body?.uri && !body?.method) {
    const { endpointId, query, headers, body: reqBody } = body;
    return invokeEndpoint(code3rd, endpointId, {
      query: query || {},
      headers: headers || {},
      body: typeof reqBody === 'string' ? reqBody : reqBody ? JSON.stringify(reqBody) : null,
    }).then((data) => ({ httpStatus: data.vendorHttpStatus ?? data.httpStatus, body: data }));
  }
  const payload = { ...body };
  if (payload.uri && !payload.path) {
    payload.path = payload.uri;
    delete payload.uri;
  }
  return runtimeFetch(`/${encodeURIComponent(code3rd)}/invoke`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }).then((data) => ({ httpStatus: data.vendorHttpStatus ?? data.httpStatus, body: data }));
}
