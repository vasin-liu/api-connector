# Hikvision Artemis POST AK/SK — protocol table

Canonical Definition: [`hikvision-artemis-post.yaml`](hikvision-artemis-post.yaml).  
Source: public `com.hikvision.artemis.sdk.util.SignUtil` raw-body POST path (`ArtemisHttpUtil.doPostString`-style JSON bytes; artemis-http-client 1.1.x). The GET table is a different definition ([`hikvision-artemis.md`](hikvision-artemis.md)); this Runtime MUST NOT depend on the SDK JAR.

**No production secrets.** Tests use `test-ak` / `test-sk` only.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://hikvision.example` (FakeTransport only) |
| Method | `POST` |
| Path (resource) | `/artemis/api/demo` |
| Auth profile (YAML) | `hikvision-artemis-post-aksk-v1` |

## Headers

| Name | Source | Fixture r1 / r2 |
|------|--------|-----------------|
| `Accept` | GLOBAL | `*/*` |
| `Content-Type` | GLOBAL | `application/json;charset=UTF-8` (no space after `;`) |
| `Content-MD5` | `hasher.md5` `encoding: base64` of the body bytes | `jiion4rNY0nKP5xj4NxZ2w==` (both sends) |
| `X-Ca-Key` | SecretRef AK | `test-ak` |
| `X-Ca-Nonce` | `{ generate: nonce }` | `1` then `2` |
| `X-Ca-Timestamp` | `{ now: epochMillis }` | `1000` then `2000` |
| `X-Ca-Signature` | HMAC-SHA256 **Base64** of StringToSign | see vector |
| `X-Ca-Signature-Headers` | GLOBAL (frozen TreeMap order) | `X-Ca-Key,X-Ca-Nonce,X-Ca-Timestamp` |

No `Date`. SK is the HMAC key only (sink HMAC). It MUST NOT appear as a header, query value, or body field.

## Body (frozen)

| Field | Value |
|-------|--------|
| Wire body | UTF-8 of GLOBAL `bodyJson` = `{"pageNo":1,"pageSize":20}` (compact, no spaces) |
| Body pipeline | one `canonicalizer.concat` of `{ var: global.bodyJson }` — no `codec.json` |
| Body in resource | **never** — the body is not merged into the canonical resource |

One GLOBAL string is the only body source. The same variable feeds the `Content-MD5` pipeline, so the hashed bytes and the wire bytes are the same literal. Flow MUST NOT hash or HMAC.

## Query parameters

| Name | In resource canonical set? | Source | Wire / canonical (`encoding: none`) |
|------|----------------------------|--------|-------------------------------------|
| `start` | **yes** | GLOBAL `start` | `0` |
| `size` | **yes** | GLOBAL `size` | `20` |

Sorted by key: `size=20&start=0`.

## StringToSign (frozen POST)

`SignUtil.buildStringToSign`: uppercase method, then present `Accept` / `Content-MD5` / `Content-Type` / `Date` values each followed by U+000A, then signed header lines `Name:value` + U+000A, then resource with **no** extra LF before the path. The POST adds the `Content-MD5` value because that header is present; this gate still omits `Date`.

```text
POST
*/*
jiion4rNY0nKP5xj4NxZ2w==
application/json;charset=UTF-8
X-Ca-Key:test-ak
X-Ca-Nonce:1
X-Ca-Timestamp:1000
/artemis/api/demo?size=20&start=0
```

Pipeline: `canonicalizer.sorted-query` (`encoding: none`) then `canonicalizer.concat` (empty separator stitching method + Accept + Content-MD5 + Content-Type + `X-Ca-*` lines + `path?` + query) then `signer.hmac-sha256` `encoding: base64`. Flow MUST NOT sort, concat StringToSign, MD5, or HMAC.

### Known test vector (FakeTransport only)

| Input | Value |
|-------|--------|
| HMAC key | UTF-8 `test-sk` |
| Algorithm | HMAC-SHA256 then RFC 4648 Base64 (`=` padding, no wrap) |
| r1 digest | `3Cct0Tx+S72OvWBlot3ziFhqIoCmaqQM75kvVHUEI0U=` |
| r2 StringToSign | same as r1 except nonce `2` and timestamp `2000` |
| r2 digest | `E/91FfyK4t2MvNyRAS4C9S7yN1MsnQgbknFxA91kfxo=` |
| r1 hex (MUST NOT be `X-Ca-Signature`) | `dc272dd13c7e4bbd8ebd6065a2ddf388586a2280a66aa40cef992f5475042345` |
| Body hex MD5 (MUST NOT be `Content-MD5`) | `8e28a89f8acd6349ca3f9c63e0dc59db` |
| `Content-MD5` | `jiion4rNY0nKP5xj4NxZ2w==` (both sends) |
| HMAC-SHA256 Base64 of query `size=20&start=0` alone | `rYxkl/xQs/ivi1994S7FV6FiLLUVrAAbOkcnyPy12OE=` — MUST NOT equal r1 |
| HMAC-SHA256 Base64 of the body alone | `vrSMx+IhX+6B1W+uvjyNi1mWaeXBHoFBDUOQybDhvGw=` — MUST NOT equal r1 |

Clock millis: `1000` then `2000`. Nonce source: `"1"` then `"2"` (not live UUID).

## Replay / freshness

Artemis POST is request signing, not a session token hop. YAML uses **timestamp + nonce refresh + `RETRY_FLOW`** (GET pattern: the re-stamp/re-digest/re-sign steps live in the YAML `authentication` flow, which sends no HTTP and holds no token):

1. Business: epoch stamp → nonce → bind AK → sorted-query → body MD5 → StringToSign HMAC → POST.
2. FakeTransport returns **503**.
3. `RETRY_FLOW` runs the re-sign flow (new timestamp, new nonce, re-digest of the same body literal, new HMAC), then renders a new request (not a byte clone of send 1). The body literal and `Content-MD5` stay identical across sends.
4. Second POST differs in `X-Ca-Timestamp`, `X-Ca-Nonce`, and `X-Ca-Signature` only.

## Assumptions

| Topic | Frozen here | Unverified vs live Artemis |
|-------|-------------|----------------------------|
| Header names | `X-Ca-*` as stored | SDK `isHeaderToSign` prefix case |
| Nonce | Scripted `"1"` / `"2"` | Live `UUID.randomUUID()` |
| Content-Type spacing | `application/json;charset=UTF-8` (no space) | Some SDK constants use `application/json; charset=UTF-8` (space) |
| Body compactness | `{"pageNo":1,"pageSize":20}` exact bytes | Portal sample bodies |
| POST form `query∪body` TreeMap | **Out of scope** — raw-body POST only | `doPostForm` form merge |
| `Date` header | **Out of scope** — omitted | SDK sends `Date` on some paths |
| 503 rebuild | Scripted retry | Live 401/403 handling may differ |

## FakeTransport script (acceptance)

| Send | Timestamp | Nonce | Body | `Content-MD5` | Signature |
|------|-----------|-------|------|---------------|-----------|
| r1 | `1000` | `1` | `{"pageNo":1,"pageSize":20}` | `jiion4rNY0nKP5xj4NxZ2w==` | r1 digest above |
| r2 (after 503 + RETRY_FLOW) | `2000` | `2` | identical | identical | r2 digest above |

r2 is a new `RawHttpRequest` instance; r1 bytes are not cloned. `X-Ca-Key` is `test-ak`. SK never appears on the wire or in the DecisionTrace.
