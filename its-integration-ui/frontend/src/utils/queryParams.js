/** @returns {Record<string, string>} */
export function rowsToQuery(rows) {
  const map = {};
  for (const row of rows || []) {
    const key = row.key?.trim();
    if (key) {
      map[key] = row.value ?? '';
    }
  }
  return map;
}

/** @param {Array<{name:string,example?:string,in?:string}>|undefined} parameters */
export function paramsToQueryRows(parameters) {
  const queryParams = (parameters || []).filter((p) => !p.in || p.in === 'query');
  if (!queryParams.length) {
    return [{ key: '', value: '' }];
  }
  return queryParams.map((p) => ({ key: p.name, value: p.example ?? '' }));
}

/** @param {Record<string, string>|undefined} query */
export function queryToRows(query) {
  if (!query || !Object.keys(query).length) {
    return [{ key: '', value: '' }];
  }
  return Object.entries(query).map(([key, value]) => ({ key, value: String(value) }));
}
