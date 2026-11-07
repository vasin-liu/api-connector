## Context

See `proposal.md`. The published GET definition `hikvision-artemis` signs a StringToSign with no `Content-MD5` line. Catalog already has `canonicalizer.concat`, `canonicalizer.sorted-query`, `signer.hmac-sha256` (`encoding: base64`), and `hasher.md5` (`encoding: base64` = RFC 4648 of the raw MD5). `codec.json` encodes the execution-variable object (`JsonBytes`), which includes timestamp, nonce, and other fields as they are assigned. `renderBody` sends `body.pipeline` through that path and returns the first bytes port. Concat ignores that object and emits UTF-8 of its configured parts.

## Goals / Non-Goals

**Goals:**

- Frozen POST table + YAML + FakeTransport whose `Content-MD5` is Base64(MD5(wire body)) and whose StringToSign contains that value.
- One GLOBAL string is the only body source. Flow does not hash or HMAC.
- GET definition and its vectors stay as published.

**Non-Goals:**

- A new pipeline node, `canonicalizer.artemis-*`, `artemis-http-client`, Groovy, Host, Catalog, AuthProvider.
- Dynamic `isHeaderToSign` / live `x-ca-*` prefix scan.
- Form `querys ∪ bodys` TreeMap. `Date`. Signing `codec.json` output.
- iFlytek WebSocket. Public internet.

## Decisions

### D1. New definition, GET stays frozen

**Choice:** `definition.id: hikvision-artemis-post`, files `hikvision-artemis-post.md` / `.yaml`. Do not edit `hikvision-artemis.yaml` or its StringToSign.

**Why:** GET `planId` and the "no Content-MD5" requirement stay intact. POST is a different method and header set.

**Alternatives:** Add a second request on the GET definition — one plan would have to carry both shapes and risks rewriting the GET vector.

### D2. Body bytes are UTF-8 of one GLOBAL string, not `codec.json`

**Choice:** GLOBAL `bodyJson` = `{"pageNo":1,"pageSize":20}` (compact, no space). Body pipeline is a single `canonicalizer.concat` part `{ var: global.bodyJson }`. The HTTP body is that pipeline. A second pipeline concats the same variable, then `hasher.md5` `encoding: base64`, and binds `execution.contentMd5`.

**Why:** `codec.json` / `encodeJsonBody`'s object is every execution variable, so the signed bytes would change when nonce or timestamp is assigned and would not be a stable Content-MD5. Concat of one variable is one source for both the wire body and the hash. `hasher.md5.in` stays bytes; the string variable is turned into bytes by concat, which matches the existing type rule.

**Alternatives:** Hash inside Flow — forbidden. Vendor "body MD5" node — rejected while concat + hasher already express it. Store concat bytes and reuse them as the body — no bytes-typed variable port exists; a new node would be a catalog change this gate does not need.

### D3. StringToSign inserts Content-MD5 between Accept and Content-Type

**Choice:** Same line rule as the GET gate (`SignUtil`: each *present* special header value plus U+000A; omitted `Date` emits nothing). POST adds the Content-MD5 value because the header is present. Resource stays `path?` + sorted query (`encoding: none`). Body bytes are not appended to the resource.

Frozen StringToSign for r1 (U+000A between lines; no trailing LF after the resource):

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

Headers on the wire: `Accept`, `Content-Type`, `Content-MD5`, `X-Ca-Key`, `X-Ca-Nonce`, `X-Ca-Timestamp`, `X-Ca-Signature`, `X-Ca-Signature-Headers` (`X-Ca-Key,X-Ca-Nonce,X-Ca-Timestamp`). Query `start=0`, `size=20`. HMAC key is SK, sink HMAC. AK is HEADER plus the concat `X-Ca-Key:` line (same split as GET).

**Why:** This is the raw-body POST shape (`doPostString` / bytes). Merging form fields into the resource is a different Artemis entry point and stays out.

**Alternatives:** Drop the query so the resource is only the path — weaker, because it would not prove the body stayed out of the resource. `application/json; charset=UTF-8` (space after semicolon) — some SDK constants use the space; this gate freezes the no-space form used by the GET charset style and records the space form as unverified against a live SDK.

### D4. Published vectors

**Choice:** Fixtures `test-ak` / `test-sk`. Clock millis `1000` then `2000`. Nonce `1` then `2`. Stub host `https://hikvision.example`. Path `/artemis/api/demo`. Body and Content-MD5 are identical on both sends.

| Field | Value |
|-------|--------|
| Body | `{"pageNo":1,"pageSize":20}` |
| `Content-MD5` | `jiion4rNY0nKP5xj4NxZ2w==` |
| MD5 hex (MUST NOT be the header) | `8e28a89f8acd6349ca3f9c63e0dc59db` |
| r1 `X-Ca-Signature` | `3Cct0Tx+S72OvWBlot3ziFhqIoCmaqQM75kvVHUEI0U=` |
| r2 `X-Ca-Signature` | `E/91FfyK4t2MvNyRAS4C9S7yN1MsnQgbknFxA91kfxo=` |
| r1 HMAC hex (MUST NOT be `X-Ca-Signature`) | `dc272dd13c7e4bbd8ebd6065a2ddf388586a2280a66aa40cef992f5475042345` |

HMAC-SHA256 Base64 of the query `size=20&start=0` alone is `rYxkl/xQs/ivi1994S7FV6FiLLUVrAAbOkcnyPy12OE=`. HMAC-SHA256 Base64 of the body alone is `vrSMx+IhX+6B1W+uvjyNi1mWaeXBHoFBDUOQybDhvGw=`. Neither equals r1.

**Why:** Apply can assert bit-exact headers without live Artemis. The GET r1 vector `8vM5Z2n43HSpDRpHRbKJNQVgHbgWP9E+1ocZIzjhE0o=` was reproduced with the same line rule before these POST digests were frozen.

### D5. RETRY_FLOW on 503, body unchanged

**Choice:** Business POST 503 → `RETRY_FLOW` (GET pattern: the freshness rebuild steps — new timestamp, new nonce, re-bind AK, sorted-query, body re-digest, re-sign — are declared as the YAML `authentication` flow; it sends no HTTP and stores no session token). The runtime routes `RETRY_FLOW` through that flow, then renders a new request instance. Second send: new timestamp, nonce, and signature. Body bytes and `Content-MD5` stay the published values.

**Why:** `FlowRuntime.retryFlow` delegates to the `authentication` flow; with none declared, `RETRY_FLOW` terminates `FAILURE`. The GET gate already ships this exact re-sign-only flow shape (its r2 vector comes from the same mechanism). Re-digesting the frozen `bodyJson` re-produces the identical `Content-MD5`, proving the body is not derived from the mutable execution object D2 forbids.

### D6. Stop if the catalog cannot keep one body

**Choice:** If `body.pipeline` cannot emit the GLOBAL string's UTF-8 as the `RawHttpRequest` body while `hasher.md5` hashes those same bytes, stop. Update this change's design and spec. Do not MD5 in Flow, do not add a vendor node, and do not sign `codec.json` output to get a green test.

**Why:** The GET change already treated a missing catalog shape as a spec update, not an implicit signer.

## Risks / Trade-offs

- [Body pipeline gains a second bytes node] → `encodeJsonBody` returns the first bytes port. YAML keeps the body pipeline to one concat. The test compares wire body to the published literal, not to an intermediate port.
- [Two concats drift] → Both parts reference `global.bodyJson`. The test requires `Content-MD5` = Base64(MD5(wire body)) = the published value.
- [Charset or JSON differs on a live device] → Table marks `application/json;charset=UTF-8` and the compact body as frozen fixtures, unverified against portal samples.
- [GET regression] → Do not edit the GET YAML. Re-run the existing Artemis GET protocol test.

## Migration Plan

Additive definition and test. Rollback is dropping the new YAML and test. No change to loaded GET plans.

## Open Questions

None. Live SDK spacing and form-body merge stay out of this gate on purpose.
