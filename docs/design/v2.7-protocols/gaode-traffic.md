# Gaode traffic HMAC — protocol table

Canonical Definition: [`gaode-traffic.yaml`](gaode-traffic.yaml).  
Sources: `docs/legacy-auth-inventory.md` (`GAODE_TRAFFIC`, `gaode_traffic_hmac_v1`, HMAC on sorted query), `docs/THIRDPART-MIGRATION.md` (`https://et-api.amap.com`), public Amap signing notes. Old Java Catalog/AuthProvider is gone.

**No production secrets.** Tests use `test-ak` / `test-sk` only.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://et-api.amap.com` |
| Method | `GET` |
| Path | `/v3/traffic/status/rectangle` |
| Auth profile (YAML) | `gaode-traffic-hmac-v1` |

## Query parameters

| Name | In HMAC canonical set? | Source | Fixture |
|------|------------------------|--------|---------|
| `city` | **yes** | GLOBAL `city` | `110000` |
| `key` | **yes** | SecretRef `secret/gaode-traffic/app-key` (AK) | `test-ak` |
| `timestamp` | **yes** (freshness) | `execution.timestamp` = `now: epochMillis` | `1000` then `1001` |
| `sig` | **NO — excluded** | pipeline HMAC hex | see vector |

`sig` is the signature **query** name (not a header). It MUST NOT be included in the sorted set that is hashed. The YAML `exclude: [sig]` records that rule even when `sig` is absent from the canonicalizer `params` map.

## Canonical string

1. Take the query map **without** `sig`.
2. Sort keys by UTF-8 / Unicode code-point order (`String` natural order for ASCII names).
3. Join `key=value` with separator **`&`**.
4. Charset **UTF-8**. Values in this table are unescaped ASCII; no percent-encoding in the sign base string.

### Known test vector (FakeTransport only)

| Input | Value |
|-------|--------|
| Unsorted example | `timestamp=1000`, `key=test-ak`, `city=110000` (and `sig` if present — dropped) |
| Canonical string | `city=110000&key=test-ak&timestamp=1000` |
| HMAC key | UTF-8 `test-sk` |
| Algorithm | HMAC-SHA256 |
| Digest | lowercase hex `7de16153d564bf1afd3b97d75f6c771a4bd7e7ac88d8d1648608b10241056155` |

Shuffling pair order **without** sorting (`timestamp=1000&key=test-ak&city=110000`) MUST NOT produce that digest.

Pipeline node: `canonicalizer.sorted-query` (separator `&`, `exclude: [sig]`) then `signer.hmac-sha256` (key = app secret, sink HMAC). Flow MUST NOT sort the query map. HTTP query declaration order in YAML is `city`, `key`, `timestamp`, `sig` — not alphabetical (`city`, `key`, `sig`, `timestamp`).

## Replay / freshness

Gaode traffic has no documented 403 + `X-Challenge` nonce. YAML uses **timestamp refresh + `RETRY_FLOW`**:

1. Business: stamp → bind AK → sign pipeline → GET (already signed).
2. FakeTransport returns **503**.
3. `RETRY_FLOW` clears FLOW, authentication re-stamps and re-HMACs, then the business template is rebuilt (not a byte clone of send 1).
4. Second GET has a different `timestamp` and a different `sig`.

## Assumptions

| Topic | Frozen here | Unverified vs live et-api |
|-------|-------------|---------------------------|
| `sig` exclusion | **Excluded** from the sorted set | Confirm if a future vendor doc includes `sig` (would be a spec change) |
| Separator | `&` | Not empty-string concat |
| Freshness field | query `timestamp` (epoch millis) | Live API may use another window field |
| 503 rebuild | Scripted retry; not a live challenge | Live 403/invalid-sig handling may differ |
| Path | `/v3/traffic/status/rectangle` | Other traffic paths share the same HMAC |

## FakeTransport script (acceptance)

| Send | Query freshness | HMAC |
|------|-----------------|------|
| r1 | `timestamp=1000` | vector digest above |
| r2 (after 503 + RETRY_FLOW) | `timestamp=1001` | HMAC of `city=110000&key=test-ak&timestamp=1001` |

r2 is a new `RawHttpRequest` instance; r1 bytes are not cloned.
