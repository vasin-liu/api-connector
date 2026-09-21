# IDPS header AK/SK — protocol table

Canonical Definition: [`idps-aksk.yaml`](idps-aksk.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.idps.IdpsUtils` + `IdpsClient` (`aksk_hmac_sha256`). Not gateway inbound SHA256, not `IdpsUtils2` (`token_sha256`), not `getSign()` MD5. Old Java Catalog/AuthProvider is gone.

**No production secrets.** Tests use `test-ak` / `test-sk` only. Do not copy keys from `BrainApiTool`.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://idps.example` (FakeTransport only) |
| Method | `GET` |
| Path (URI in envelope) | `/api/v2/demo` |
| Auth profile (YAML) | `idps-aksk-hmac-v1` |
| Algorithm literal | `aksk_hmac_sha256` |

## Headers

| Name | Source | Fixture r1 / r2 |
|------|--------|-----------------|
| `X-Auth-Key` | SecretRef AK | `test-ak` |
| `X-Auth-Algorithm` | GLOBAL algorithm | `aksk_hmac_sha256` |
| `X-Auth-Signature` | HMAC-SHA256 hex of envelope | see vector |
| `X-Auth-Timestamp` | `{ now: isoOffset }` (UTC clock) | `1970-01-01T00:00:01Z` then `1970-01-01T00:00:02Z` |
| `X-Auth-SnowflakeID` | `{ generate: nonce }` | `1` then `2` |

SK is the HMAC key only (sink HMAC). It MUST NOT appear as a header or query value.

## Query parameters

| Name | In HMAC canonical set? | Source | Wire (IdpsClient) | Canonical (RFC3986) |
|------|------------------------|--------|-------------------|---------------------|
| `city` | **yes** | GLOBAL `city` | `110000` | `110000` |
| `q` | **yes** | GLOBAL `q` | `a*b` | `a%2Ab` |

`RequestRenderer` joins unencoded `name=value`, matching `IdpsClient` `MapUtil.joinIgnoreNull`. `BrainApiTool` instead puts the encoded string on the URL — **not** this table’s wire behavior.

The HTTP fixture uses `*` (legal in `java.net.URI`) so the wire can stay unencoded. Space → `%20` is covered by the sorted-query unit test, not by the FakeTransport URL.

## Canonical query (RFC3986 then sort)

1. Percent-encode each key and value: UTF-8; `+` → `%20`; `*` → `%2A`; `%7E` → `~`.
2. Sort the encoded `key=value` items by ASCII order.
3. Join with `&`. Empty map → `""` (envelope still has a blank line between URI and nonce).

### Encoding examples (node unit test)

| Raw | Encoded item |
|-----|----------------|
| `q=a b` | `q=a%20b` |
| `q=a*b` | `q=a%2Ab` |
| `note=~ok` | `note=~ok` |

Published query line for the HMAC vector: `city=110000&q=a%2Ab`.

## Envelope (stringToSign)

Seven lines joined by U+000A (LF). **No** trailing LF after the nonce.

```text
aksk_hmac_sha256
test-ak
1970-01-01T00:00:01Z
GET
/api/v2/demo
city=110000&q=a%2Ab
1
```

Pipeline: `canonicalizer.sorted-query` (`encoding: rfc3986`) then `canonicalizer.concat` (separator LF) then `signer.hmac-sha256`. Flow MUST NOT sort, encode, or HMAC.

### Known test vector (FakeTransport only)

| Input | Value |
|-------|--------|
| HMAC key | UTF-8 `test-sk` |
| Algorithm | HMAC-SHA256 |
| r1 digest | lowercase hex `f4722b6df8d00aae9995b83ef056f3442c8c13c32571a82007ca54a762aa7de6` |
| r2 envelope | same as r1 except timestamp `1970-01-01T00:00:02Z` and nonce `2` |
| r2 digest | lowercase hex `5d5a0d988a600516ce38e5b078e07d04c0583187e8496065e3e92349bbcda6dc` |
| HMAC of query line alone | `0a09bb7975e2144b5cbd8a4b559262390d39c48ad1af1f2a68e641fda2ce5f2e` (MUST NOT equal r1) |

Clock instants: 1000 ms then 2000 ms UTC. Format is `yyyy-MM-dd'T'HH:mm:ssXXX` (no millis); 1000 vs 1001 would collide.

## Replay / freshness

IDPS has no Session token and no 403 challenge nonce. YAML uses **timestamp + nonce refresh + `RETRY_FLOW`**:

1. Business: ISO stamp → nonce → bind AK → RFC3986 pipeline → envelope HMAC → GET (already signed).
2. FakeTransport returns **503**.
3. `RETRY_FLOW` clears FLOW; authentication re-stamps, generates a new nonce, and re-HMACs; the business template is rebuilt (not a byte clone of send 1).
4. Second GET differs in `X-Auth-Timestamp`, `X-Auth-SnowflakeID`, and `X-Auth-Signature`.

## Assumptions

| Topic | Frozen here | Unverified vs live IDPS |
|-------|-------------|-------------------------|
| Wire query encoding | Unencoded (`IdpsClient`) | `BrainApiTool` encodes the URL |
| Timestamp zone | UTC in tests | Production uses `ZonedDateTime.now()` (JVM default) |
| Snowflake | Scripted nonce `"1"` / `"2"` | Live Hutool snowflake bit layout |
| Path | `/api/v2/demo` | Other GET paths share the same HMAC |
| POST body in envelope | **Out of scope** | Not in `IdpsUtils` query canonical |
| 503 rebuild | Scripted retry | Live 401/403 handling may differ |

## FakeTransport script (acceptance)

| Send | Timestamp | SnowflakeID | Signature |
|------|-----------|-------------|-----------|
| r1 | `1970-01-01T00:00:01Z` | `1` | r1 digest above |
| r2 (after 503 + RETRY_FLOW) | `1970-01-01T00:00:02Z` | `2` | r2 digest above |

r2 is a new `RawHttpRequest` instance; r1 bytes are not cloned. Wire query contains `q=a*b`; envelope query line contains `q=a%2Ab`.
