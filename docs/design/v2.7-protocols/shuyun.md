# Shuyun SM3 header sign — protocol table

Canonical Definition: [`shuyun.yaml`](shuyun.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.shuyun.ShuyunClient.sign` and `getBusiHeader`. Old Java Catalog/AuthProvider is gone.

**No production secrets.** Tests use `test-app-id` / `test-app-secret` only. Do not copy the host or secret from `ShuyunClientTest`.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://shuyun.example` (FakeTransport only) |
| Method | `POST` |
| Path | `/sznszhddpt/rest/event/list` |
| Auth profile (YAML) | `shuyun-sm3-header-v1` |
| Body (not hashed) | `{"eventType":"200170001"}` |

## Headers

| Name | In SM3 material? | Source | Fixture r1 / r2 |
|------|------------------|--------|-----------------|
| `Content-Type` | **no** | literal | `application/json;charset=UTF-8` |
| `x-app-id` | **yes** (as app id) | GLOBAL `appId` | `test-app-id` |
| `nonce` | **yes** | `{ generate: nonce }` | `n1` then `n2` |
| `timestamp` | **yes** | `{ now: epochMillis }` | `1000` then `1001` |
| `signature` | **NO — digest** | `hasher.sm3` lowercase hex | see vector |
| `operationUserId` | **no** | literal | `1` |
| `operationUserName` | **no** | literal | `idps` |
| `operationUserCode` | **no** | literal | `idps` |
| `operationAppCode` | **no** | literal | `jtdn` |

`appSecret` is HASH material only. It MUST NOT appear as a header, query, or body field.

## Signed material

1. Concatenate UTF-8 strings with an **empty** separator: `appId` + `appSecret` + `timestamp` + `nonce`.
2. SM3 over those bytes (BouncyCastle `SM3Digest`, same call shape as `ShuyunClient.sign`).
3. Emit **lowercase** hexadecimal (32 bytes → 64 hex chars).

Charset contract is UTF-8. The legacy client used platform-default `getBytes()`; fixtures are ASCII so both charsets hash the same bytes.

Pipeline: `canonicalizer.concat` (`separator: ""`, secret part sink `HASH`) then `hasher.sm3` (`encoding: hex`). Flow MUST NOT hash.

### Known test vector (FakeTransport only)

| Send | Material | `signature` |
|------|----------|-------------|
| r1 | `test-app-idtest-app-secret1000n1` | `bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb` |
| r2 | `test-app-idtest-app-secret1001n2` | `c6261c5b5b0eb59b92993bcb8954aec24e5436e1c50b8246d80765e9733aa882` |

Uppercase of the r1 digest `BCE0B91E9BA1E45AAE2CCD3341C7F5BCF0889E2B08D12ED7B3CD6DC9744C05CB` MUST NOT be `signature`.

Clock: `1000` then `1001` (epoch millis). Nonce: `n1` then `n2`.

## Replay / freshness

No Session login hop. YAML uses **timestamp + nonce refresh + `RETRY_FLOW`**:

1. Business: stamp → nonce → sign pipeline → POST (already signed).
2. FakeTransport returns **503**.
3. `RETRY_FLOW` runs authentication, which re-stamps, generates a new nonce, and re-SM3s with **zero** authentication HTTP; the business template is rebuilt (not a byte clone of send 1).
4. Second POST differs in `timestamp`, `nonce`, and `signature`. Body bytes stay equal to the first body.

## Assumptions

| Topic | Frozen here | Unverified vs live Shuyun |
|-------|-------------|---------------------------|
| Charset | UTF-8 | Legacy platform default |
| Nonce alphabet | Injectable `generate: nonce` | Hutool `nanoId` |
| Routes | `list` only | `pageList` / `complete` share the same headers |
| Body | Fixed event-type JSON | Live query fields vary |

## Out of scope

`pageList`, `complete`, Hutool nanoId alphabet/length, platform-default `getBytes()`, SM4, 讯飞 WebSocket, Keytop empty/nested filtering, Host / Catalog / Groovy, public internet, production secrets.

## FakeTransport script (acceptance)

| Send | Freshness | SM3 |
|------|-----------|-----|
| r1 | `timestamp=1000`, `nonce=n1` | r1 digest above |
| r2 (after 503 + `RETRY_FLOW`) | `timestamp=1001`, `nonce=n2` | r2 digest above |

r2 is a new `RawHttpRequest` instance; r1 bytes are not cloned. Authentication HTTP count is zero.
