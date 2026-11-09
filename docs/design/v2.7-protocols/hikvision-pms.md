# Hikvision PMS 下行 token — protocol table

Canonical Definition: [`hikvision-pms.yaml`](hikvision-pms.yaml).  
Source: legacy `suntek-system/system-thirdpart` `HikvisionPmsClient.withToken` + `BaseClient.postHttpRequest`（考古引用，不引入依赖）。This is **not** the Artemis protocol — different definition, different headers, different digest. The earlier "Accurio hex HMAC" phrasing is retired: it has no referent in the real codebase. This Runtime MUST NOT depend on any vendor SDK JAR.

**No production secrets.** Tests use `test-ak` (appkey) / `test-sk` (secret) only.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://hikvision-pms.example` (FakeTransport only) |
| Method | `POST` |
| Path (hash material, no host, no query) | `/getParkingInfo` |
| Auth profile (YAML) | `hikvision-pms-download-token-v1` |

## Headers

| Name | Source | Value |
|------|--------|-------|
| `Content-Type` | GLOBAL | `application/json;charset=UTF-8` (hutool `ContentType.JSON`; no space after `;`) |

No `X-Ca-*` headers. No `Date`. No `Authorization`. The secret is never a header value.

## Body (frozen)

| Field | Value |
|-------|--------|
| Wire body | UTF-8 of GLOBAL `bodyJson` = `{"appkey":"test-ak"}` (compact, no spaces) |
| Body pipeline | one `canonicalizer.concat` of `{ var: global.bodyJson }` — no `codec.json` |
| Hashed bytes | the **same** `bodyJson` literal — hash material and wire bytes are one source |

One GLOBAL string is the only body source. `appkey` is a **fixture literal** in this gate; production appkey composition is a recorded out-of-scope gap (concat parts with `secretRef` must declare sink `HASH`, so a credential cannot flow into a wire-body concat without a future catalog decision).

## Token (query)

```text
token = MD5( uriPath + bodyBytes + secret ).toUpperCase()
```

- `uriPath` = path only (`/getParkingInfo`) — no host, no query.
- `bodyBytes` = the exact wire body bytes.
- `secret` = UTF-8 secret key, enters via concat part `secretRef` with sink `HASH`.
- Digest encoding: `hasher.md5` with `encoding: hex-upper` — **uppercase** hexadecimal MD5.
- Placed as query parameter `token` on the POST URL.

Pipeline: `canonicalizer.concat` (parts `[uriPath, bodyJson, secretRef(HASH)]`) → `hasher.md5` `encoding: hex-upper` → bind `execution.token`. Flow MUST NOT MD5 or uppercase.

## Known test vector (FakeTransport only)

| Input | Value |
|-------|--------|
| Hash material | `/getParkingInfo{"appkey":"test-ak"}test-sk` |
| token (r1 = r2, deterministic) | `EDAA4B3136EA75DF9A435C95B920DD9E` |
| lowercase hex (MUST NOT be token) | `edaa4b3136ea75df9a435c95b920dd9e` |
| Base64 of the raw MD5 (MUST NOT be token) | `7apLMTbqdd+aQ1yVuSDdng==` |

Formula generality (table only, no second request in the gate):

| Endpoint | Body | token |
|----------|------|-------|
| `/getVehicleInOutRecordPage` | `{"appkey":"test-ak","pageNo":1,"pageSize":20}` | `10E3EB030CE9C0BDE8681993F3A69799` |

## Replay / freshness

The token is a **deterministic** function of URI path and body — no timestamp, no nonce, no session. YAML uses `replayability: SAFE` + 503 → `RETRY_REQUEST`:

1. Business: bind token pipeline → POST `/getParkingInfo?token=...`.
2. FakeTransport returns **503**.
3. `RETRY_REQUEST` re-renders the request: a **new `RawHttpRequest` instance** whose body bytes and `token` **equal** the first send. Identical r1/r2 token is REQUIRED behavior here — the opposite of the Artemis gates, whose freshness fields must change.
4. No `authentication` flow: `RETRY_REQUEST` in the runtime does not route through authentication.

## Assumptions

| Topic | Frozen here | Unverified vs live PMS |
|-------|-------------|------------------------|
| Content-Type spacing | `application/json;charset=UTF-8` (hutool) | Portal doc may show `application/json` bare |
| Body serialization | compact JSON, insertion order (`appkey` first) | Live fastjson version specifics |
| Hash material joins | plain concatenation, no separators | — |
| `getParkingInfo` body | `{"appkey":"test-ak"}` (HashMap, single key) | Other endpoints add business fields |
| Production appkey | **Out of scope** — recorded catalog gap | Needs secret→body admission or concat bytes input |
| `/ImageServer*` binary download | **Out of scope** | Image endpoints return file bytes |
| 503 handling | Scripted `RETRY_REQUEST` | Live portal error codes may differ |

## FakeTransport script (acceptance)

| Send | Body | `token` |
|------|------|---------|
| r1 | `{"appkey":"test-ak"}` | `EDAA4B3136EA75DF9A435C95B920DD9E` |
| r2 (after 503 + RETRY_REQUEST) | identical | identical |

r2 is a new `RawHttpRequest` instance; r1 bytes are not cloned. SK never appears on the wire or in the DecisionTrace.
