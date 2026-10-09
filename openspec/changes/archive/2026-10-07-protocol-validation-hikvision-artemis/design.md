## Context

See `proposal.md`. Runtime on `main` has Wenxin / Gaode / IDPS / Huawei / DaHua FakeTransport gates. Catalog: `passthrough`, `codec.json`, `canonicalizer.concat`, `canonicalizer.sorted-query`, `signer.hmac-sha256`, `hasher.md5`. HMAC and MD5 emit **lowercase hex only** (`HmacSha256.hex`, `Md5Hasher.hex`; executor aliases `.out` and `.hex`). Concat already mixes `value` / `var` / `secretRef` with a configured separator (IDPS LF envelope; DaHua empty separator). `{ now: epochMillis }` and `{ generate: nonce }` already exist. Artemis formula is public `com.hikvision.artemis.sdk.util.SignUtil` (HMAC-SHA256 then Apache Commons `Base64.encodeBase64`); this repo must not take that JAR as a dependency.

## Goals / Non-Goals

**Goals:**

- Engine: `encoding: hex | base64` on HMAC and MD5; default hex so Gaode / IDPS / DaHua stay green.
- Protocol: frozen GET table + YAML + FakeTransport 503 → `RETRY_FLOW`.
- Compose StringToSign with concat + sorted-query; no vendor canonicalizer.

**Non-Goals:**

- `canonicalizer.artemis-*`, `signer.artemis-*`, `artemis-http-client`, Groovy, Host, Catalog, AuthProvider.
- Dynamic `isHeaderToSign` / mutating `x-ca-signature-headers` from a live header map.
- POST form `querys ∪ bodys` TreeMap; string/bytes body in resource; `Content-MD5` on this gate (hasher `base64` is unit-tested only).
- iFlytek WebSocket. Public internet.

## Decisions

### D1. Source of truth is SignUtil GET defaults, not Accurio hex

**Choice:** Protocol table copies `SignUtil.buildStringToSign` + `sign` for **GET** with SDK `doGetArtemis` defaults: `Accept: */*`, `Content-Type: application/text;charset=UTF-8`, no `Content-MD5`, no `Date`. Wire headers: `X-Ca-Key`, `X-Ca-Nonce`, `X-Ca-Timestamp`, `X-Ca-Signature`, `X-Ca-Signature-Headers`. Timestamp is `String.valueOf(clock.millis())`. Nonce is `{ generate: nonce }` (scripted in tests). SK is HMAC key only.

**Why:** Official sample is GET + query; Accurio `_sign` + hex is a different protocol.

**Alternatives:** POST JSON + `Content-MD5` — extra hasher on the body and resource rules for string body; deferred.

### D2. HMAC/MD5 encoding flag, not a new node

**Choice:** `config.encoding`: `hex` (default) | `base64`. Format with `java.util.Base64.getEncoder()` (RFC 4648, `=` padding, no wrap) — bit-equivalent to Commons `encodeBase64` used by SignUtil. Validator rejects any other value. Executor: `.out` is the encoded string; when `hex`, keep `.hex` alias (Gaode/IDPS `hmac.hex`); when `base64`, also alias `.base64` and **do not** put Base64 on `.hex`. Same for `hasher.md5`. No new Maven dependency.

**Why:** Smallest catalog change; default preserves shipped YAML.

**Alternatives:** Separate `signer.hmac-sha256-base64` type — extra catalog entry. `codec.base64` after hex — wrong (Base64 of hex ≠ Base64 of digest).

### D3. Frozen GET StringToSign via two pipelines, not a vendor node

**Choice:** Pipeline `resourceQuery`: `canonicalizer.sorted-query` `encoding: none` over `start` / `size` → `execution.canonicalQuery`. Pipeline `stringToSignHmac`: `canonicalizer.concat` separator `""` stitches method block, `X-Ca-Key:` + `secretRef` AK, nonce/timestamp lines, `path?` + canonical query; then `signer.hmac-sha256` `encoding: base64` with explicit `concat.out` → `hmac.in` and SK → `hmac.key` sink `HMAC`. `X-Ca-Signature-Headers` is a GLOBAL constant `X-Ca-Key,X-Ca-Nonce,X-Ca-Timestamp` (TreeMap order of those three names). Flow MUST NOT HMAC.

Frozen StringToSign (U+000A between lines; **no** trailing LF after resource):

```text
GET
*/*
application/text;charset=UTF-8
X-Ca-Key:test-ak
X-Ca-Nonce:1
X-Ca-Timestamp:1000
/artemis/api/demo?size=20&start=0
```

**Why:** Same composition IDPS used for a seven-line envelope. Dynamic header prefix scan is unnecessary when the header set is frozen.

**Alternatives:** `canonicalizer.artemis-string-to-sign` — rejected (IDPS already forbade `canonicalizer.aksk-*`). Signing in Flow — forbidden.

### D4. Published FakeTransport vector

**Choice:** Fixtures `test-ak` / `test-sk`. Clock millis `1000` then `2000`. Nonce `1` then `2`. Path `/artemis/api/demo`. Query `start=0` `size=20` (sorted `size=20&start=0`). Stub host `https://hikvision.example`.

| Send | Timestamp | Nonce | `X-Ca-Signature` |
|------|-----------|-------|------------------|
| r1 | `1000` | `1` | `8vM5Z2n43HSpDRpHRbKJNQVgHbgWP9E+1ocZIzjhE0o=` |
| r2 | `2000` | `2` | `lR1hXqQhapV53HJazMDZPUn4NXMywjKFaMKeyLZeYxU=` |

r1 hex (must **not** appear as `X-Ca-Signature`): `f2f3396769f8dc74a90d1a4745b2893505601db8163fd13ed687192338e1134a`.

**Why:** Apply can assert bit-exact Base64 without live Artemis.

### D5. AK HEADER + concat; SK HMAC only

**Choice:** Two `type: secret` credentials `accessKey` / `secretKey`. Bind AK to EXECUTION for header `X-Ca-Key` with sink `HEADER`. Concat `secretRef` for the `X-Ca-Key:` line uses omitted/HMAC sink (same as IDPS envelope AK line). SK only HMAC key port.

**Why:** AK is on the wire by design; SK must never be.

### D6. RETRY_FLOW on 503, no session hop

**Choice:** Business GET 503 → `RETRY_FLOW` (IDPS pattern). No authentication flow. Second execute is a fresh stamp/sign (tests may use 200 on send 2). Valid-session skip does not apply.

**Why:** Artemis GET is request signing, not cookie/token session.

### D7. Gap discipline

**Choice:** If YAML `"\n"` is not U+000A, stop and spec a named `lf` separator (IDPS already locked this). If concat cannot stitch `X-Ca-Key:` + secret without revealing SK, stop and spec — do not add Script. If a future POST needs `query∪body` merge or `Content-MD5`, new OpenSpec — do not special-case in Flow.

## Risks / Trade-offs

- [Default encoding change] → Omit encoding ⇒ hex; only Artemis sets `base64`; regression Gaode/IDPS/DaHua.
- [`.hex` alias polluted with Base64] → D2: `.hex` only when encoding is hex.
- [Live SDK `isHeaderToSign` uses `x-ca-` vs `X-Ca-`] → Frozen names on both StringToSign and wire; FakeTransport does not claim live portal accept.
- [Concat stitches wrong newline around resource] → Unit test StringToSign bytes equal D4; protocol test asserts table Base64.
- [AK in concat vs HEADER double-read] → Same credential id; sink policy per use.

## Migration Plan

1. Protocol table with D4 vector.
2. HMAC/MD5 encoding unit tests (hex default, Base64 vector, unknown encoding).
3. YAML + FakeTransport RED then GREEN. Re-run Gaode / IDPS / Huawei / DaHua.
4. No production deploy.

## Open Questions

None that change specs or tasks. Live Artemis header-case and nonce UUID format are table assumptions, not apply blockers.
