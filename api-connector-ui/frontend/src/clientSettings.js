const STORAGE_KEY = 'api-connector.console.settings';

const DEFAULTS = {
  apiKeyHeader: 'X-Integration-Api-Key',
  adminApiKey: '',
  runtimeApiKey: '',
};

/** @returns {{ apiKeyHeader: string, adminApiKey: string, runtimeApiKey: string }} */
export function loadClientSettings() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { ...DEFAULTS };
    }
    return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULTS };
  }
}

/** @param {Partial<typeof DEFAULTS>} patch */
export function saveClientSettings(patch) {
  const next = { ...loadClientSettings(), ...patch };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  return next;
}

/** @param {'admin' | 'runtime'} scope */
export function apiKeyForScope(scope) {
  const settings = loadClientSettings();
  if (scope === 'admin') {
    return settings.adminApiKey?.trim() || '';
  }
  return settings.runtimeApiKey?.trim() || settings.adminApiKey?.trim() || '';
}

/** @param {'admin' | 'runtime'} scope */
export function apiKeyHeaders(scope) {
  const key = apiKeyForScope(scope);
  const header = loadClientSettings().apiKeyHeader || DEFAULTS.apiKeyHeader;
  if (!key) {
    return {};
  }
  return { [header]: key };
}
