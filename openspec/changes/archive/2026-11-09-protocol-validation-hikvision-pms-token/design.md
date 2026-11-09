# Design

## Context

See `proposal.md`. Truth source is `suntek-system/system-thirdpart` `HikvisionPmsClient.withToken` + `BaseClient.postHttpRequest` (read-only archaeology; no dependency introduced): `token = md5Hex(uri + body + secret).toUpperCase()`, placed as `?token=` on a POST whose body is fastjson-serialized params (`appkey` included) and whose `Content-Type` is hutool `application/json;charset=UTF-8`. Catalog today: `hasher.md5` emits `hex` (lowercase) or `base64` — uppercase hex does not exist. Concat parts with `secretRef` must declare sink `HASH`, so a credential cannot flow into a wire-body concat. `FlowRuntime` runs `RETRY_REQUEST` by re-rendering the same request without any auth flow (mock-c precedent), while `RETRY_FLOW` routes through the `authentication` flow (learned in the Artemis POST gate).

## Goals / Non-Goals

**Goals:**

- Protocol table + Canonical YAML + FakeTransport gate whose `token` equals uppercase hex MD5 of URI path + wire body + secret.
- One GLOBAL `bodyJson` literal is the only body source; wire body bytes and hashed bytes are the same bytes.
- Catalog delta: `DigestEncoding.HEX_UPPER` reachable as `encoding: hex-upper` on `hasher.md5` / `signer.hmac-sha256`; `hex` / `base64` outputs and the lowercase `.hex` bindings unchanged.
- 503 → `RETRY_REQUEST`: second send is a new instance with identical body and token.

**Non-Goals:**

- Production appkey composition (secret-in-body via concat is blocked by HASH admission — recorded gap, below).
- `/ImageServer*` binary downloads; `Date` headers; other PMS endpoints; live portal verification.
- A new pipeline node, a case-transform node, vendor `canonicalizer.pms-*`, Groovy, Host, Catalog, AuthProvider.
- 公网调用；生产密钥入库。Accurio 命名就此作废（真实代码库零命中）。

## Decisions

### D1. Source of truth is HikvisionPmsClient, Accurio retired

**Choice:** The table freezes what the legacy client does: POST, JSON body, `?token=<UPPERCASE-HEX-MD5>`, uri = path only (no host, no query). The "Accurio hex HMAC" phrasing from earlier proposals is dropped; it has no referent in the real codebase.

**Why:** 50-package audit of system-thirdpart found no Accurio; PMS is the only non-Artemis Hikvision protocol in code, and it is MD5 (not HMAC), lowercase-digest-uppercased (not native uppercase digest).

**Alternatives:** Keep hunting a public "Accurio" spec — no starting point exists; the live client is authoritative.

### D2. Fixture-literal body; one GLOBAL feeds wire and hash

**Choice:** GLOBAL `bodyJson` = `{"appkey":"test-ak"}` (compact, fixture). Body pipeline = one `canonicalizer.concat` of `{ var: global.bodyJson }` (Artemis POST D2 pattern). Token pipeline = `canonicalizer.concat` parts `[ global.uriPath, global.bodyJson, secretRef secretKey (sink HASH) ]` → `hasher.md5` `encoding: hex-upper` → bind `execution.token` → request query `token: "{execution.token}"`.

**Why:** The hashed bytes and the wire bytes are the same literal, so the spec's "hash the exact bytes you send" is structural, not test-enforced. The secret enters only the hash admission (sink HASH), never the wire. appkey is a fixture literal, consistent with the table's fixtures-only stance.

**Alternatives:** `codec.json` body with a bytes-input concat for the token — needs a new catalog shape (concat consuming another node's bytes port); rejected for this gate, recorded as the production-composition gap. Secret concat part with a non-HASH sink to build the body — would weaken the HASH-admission validator rule that DaHua relies on.

### D3. hex-upper is a DigestEncoding value, not a node or a transform

**Choice:** Add `HEX_UPPER` to `DigestEncoding` (format = lowercase hex then uppercased; `parse` accepts `hex-upper`). Executor: `HEX_UPPER` writes only `id.out`; the `.hex` alias remains lowercase-`HEX`-only (mirrors the base64 rule from the Artemis GET gate D2). Validator needs no separate allowlist — encoding checks go through `DigestEncoding.parse`.

**Why:** Same smallest-catalog-change pattern as the GET gate's D2 encoding flag; default (`hex`) preserves shipped DaHua YAML byte-for-byte.

**Alternatives:** A `transform.upper-case` node — a case node is a string→string transform, a new category, and risks uppercasing secrets in other uses. `signer.hmac-sha256`-style dedicated node — catalog bloat for one encoding form.

### D4. Published vectors (FakeTransport only)

Fixtures `test-ak` (appkey) / `test-sk` (secret). Stub host `https://hikvision-pms.example`. Frozen endpoint `/getParkingInfo`, body `{"appkey":"test-ak"}`.

| Field | Value |
|-------|--------|
| Hash material | `/getParkingInfo{"appkey":"test-ak"}test-sk` |
| token (r1 = r2) | `EDAA4B3136EA75DF9A435C95B920DD9E` |
| lowercase hex (MUST NOT be token) | `edaa4b3136ea75df9a435c95b920dd9e` |
| Second endpoint (formula generality, table only) | `/getVehicleInOutRecordPage` + `{"appkey":"test-ak","pageNo":1,"pageSize":20}` → `10E3EB030CE9C0BDE8681993F3A69799` |

**Why:** Deterministic (no clock/nonce), so a single vector proves the formula; the second endpoint documents insertion-order JSON generality without a second request.

### D5. 503 → RETRY_REQUEST, deterministic token, no auth flow

**Choice:** `replayability: SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`; transitions: 503 → `RETRY_REQUEST`, 200 → `SUCCESS`. No `authentication` flow. Second send is a fresh render: new `RawHttpRequest` instance, identical body bytes and token.

**Why:** `RETRY_REQUEST` in `FlowRuntime` re-renders without touching `authenticate` (mock-c precedent), so no auth flow is required — unlike `RETRY_FLOW`, which terminates FAILURE without one (Artemis POST gate finding). The token is a pure function of path+body; rebuilding freshness would be wrong here.

**Alternatives:** `RETRY_FLOW` — semantically incorrect (no freshness exists) and mechanically requires an auth flow.

### D6. Stop if one-body-source breaks

**Choice:** If the gate cannot keep wire body = hashed bytes with the current catalog (e.g. concat or hasher refuses the shape), stop and update this change's specs/design. Do not MD5 in Flow, do not uppercase in Flow, do not sign `codec.json` output.

**Why:** Same gap discipline as the Artemis POST gate D6.

## Risks / Trade-offs

- [appkey is a literal, not a credential binding] → Table states fixtures-only; production appkey-in-body needs a catalog decision (secret→body admission or concat bytes input) — explicitly deferred, recorded here and in the table.
- [Content-Type spelling] → hutool `application/json;charset=UTF-8` frozen; space form unverified against the PMS portal doc — table marks it.
- [HEX_UPPER leaks onto `.hex`] → Spec forbids; test asserts the uppercase digest never appears on a `.hex`-bound value and that hex-default YAML output stays lowercase.
- [Deterministic token mistaken for a replay bug] → Spec scenario pins r2 body+token equality as REQUIRED behavior; contrast documented against Artemis freshness.

## Migration Plan

Additive: new enum value, new YAML, new test. Existing YAMLs never mention `hex-upper`; their compile and output stay byte-identical. Rollback = drop the new files and the enum value.

## Open Questions

None. Production appkey composition is a recorded future gap, not an open question — it needs its own change if the Host phase ever loads PMS with real tenants.
