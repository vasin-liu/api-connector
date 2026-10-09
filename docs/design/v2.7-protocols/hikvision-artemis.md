# Hikvision Artemis GET AK/SK — protocol table

Canonical Definition: [`hikvision-artemis.yaml`](hikvision-artemis.yaml).  
Source: public `com.hikvision.artemis.sdk.util.SignUtil` + `ArtemisHttpUtil.doGetArtemis` defaults (artemis-http-client 1.1.x). Accurio hex HMAC is a different protocol. This Runtime MUST NOT depend on the SDK JAR.

**No production secrets.** Tests use `test-ak` / `test-sk` only.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://hikvision.example` (FakeTransport only) |
| Method | `GET` |
| Path (resource) | `/artemis/api/demo` |
| Auth profile (YAML) | `hikvision-artemis-aksk-v1` |

## Headers

| Name | Source | Fixture r1 / r2 |
|------|--------|-----------------|
| `Accept` | GLOBAL | `*/*` |
| `Content-Type` | GLOBAL | `application/text;charset=UTF-8` |
| `X-Ca-Key` | SecretRef AK | `test-ak` |
| `X-Ca-Nonce` | `{ generate: nonce }` | `1` then `2` |
| `X-Ca-Timestamp` | `{ now: epochMillis }` | `1000` then `2000` |
| `X-Ca-Signature` | HMAC-SHA256 **Base64** of StringToSign | see vector |
| `X-Ca-Signature-Headers` | GLOBAL (frozen TreeMap order) | `X-Ca-Key,X-Ca-Nonce,X-Ca-Timestamp` |

No `Content-MD5`. No `Date`. SK is the HMAC key only (sink HMAC). It MUST NOT appear as a header or query value.

## Query parameters

| Name | In resource canonical set? | Source | Wire / canonical (`encoding: none`) |
|------|----------------------------|--------|-------------------------------------|
| `start` | **yes** | GLOBAL `start` | `0` |
| `size` | **yes** | GLOBAL `size` | `20` |

Sorted by key: `size=20&start=0`.

## StringToSign (frozen GET)

`SignUtil.buildStringToSign`: uppercase method, then present `Accept` / `Content-MD5` / `Content-Type` / `Date` values each followed by U+000A, then signed header lines `Name:value` + U+000A, then resource with **no** extra LF before the path. This gate omits `Content-MD5` and `Date`.

```text
GET
*/*
application/text;charset=UTF-8
X-Ca-Key:test-ak
X-Ca-Nonce:1
X-Ca-Timestamp:1000
/artemis/api/demo?size=20&start=0
```

Pipeline: `canonicalizer.sorted-query` (`encoding: none`) then `canonicalizer.concat` (empty separator stitching prefixes + AK + nonce/timestamp + `path?` + query) then `signer.hmac-sha256` `encoding: base64`. Flow MUST NOT sort, concat StringToSign, or HMAC.

### Known test vector (FakeTransport only)

| Input | Value |
|-------|--------|
| HMAC key | UTF-8 `test-sk` |
| Algorithm | HMAC-SHA256 then RFC 4648 Base64 (`=` padding, no wrap) |
| r1 digest | `8vM5Z2n43HSpDRpHRbKJNQVgHbgWP9E+1ocZIzjhE0o=` |
| r2 StringToSign | same as r1 except nonce `2` and timestamp `2000` |
| r2 digest | `lR1hXqQhapV53HJazMDZPUn4NXMywjKFaMKeyLZeYxU=` |
| r1 hex (MUST NOT be `X-Ca-Signature`) | `f2f3396769f8dc74a90d1a4745b2893505601db8163fd13ed687192338e1134a` |
| HMAC-SHA256 Base64 of query `size=20&start=0` alone | MUST NOT equal r1 |

Clock millis: `1000` then `2000`. Nonce source: `"1"` then `"2"` (not live UUID).

## Replay / freshness

Artemis GET is request signing, not a session token hop. YAML uses **timestamp + nonce refresh + `RETRY_FLOW`**:

1. Business: epoch stamp → nonce → bind AK → sorted-query → StringToSign HMAC → GET.
2. FakeTransport returns **503**.
3. `RETRY_FLOW` re-stamps, generates a new nonce, and re-HMACs; the request is rebuilt (not a byte clone of send 1).
4. Second GET differs in `X-Ca-Timestamp`, `X-Ca-Nonce`, and `X-Ca-Signature`.

## Assumptions

| Topic | Frozen here | Unverified vs live Artemis |
|-------|-------------|----------------------------|
| Header names | `X-Ca-*` as stored | SDK `isHeaderToSign` prefix case |
| Nonce | Scripted `"1"` / `"2"` | Live `UUID.randomUUID()` |
| GET defaults | SDK `doGetArtemis` Accept / Content-Type | Portal may require other Accept |
| POST `query∪body` / `Content-MD5` | **Out of scope** | `doPostForm` / `doPostBytes` |
| 503 rebuild | Scripted retry | Live 401/403 handling may differ |

## FakeTransport script (acceptance)

| Send | Timestamp | Nonce | Signature |
|------|-----------|-------|-----------|
| r1 | `1000` | `1` | r1 digest above |
| r2 (after 503 + RETRY_FLOW) | `2000` | `2` | r2 digest above |

r2 is a new `RawHttpRequest` instance; r1 bytes are not cloned. `X-Ca-Key` is `test-ak`. SK never appears on the wire.
