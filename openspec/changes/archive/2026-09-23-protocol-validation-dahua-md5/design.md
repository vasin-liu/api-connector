## Context

See `proposal.md`. Runtime on `main` has Wenxin / Gaode / IDPS / Huawei FakeTransport gates. Pipeline catalog is `passthrough`, `codec.json`, `canonicalizer.concat`, `canonicalizer.sorted-query`, `signer.hmac-sha256`. `SecretSink` has AUTHORIZATION / HMAC / HEADER / QUERY / BODY — no HASH. `PipelineExecutor.concat` and sorted-query `resolvePart` hardcode `SecretSink.HMAC` for every `secretRef`. `PipelineGraphValidator.typesCompatible` is strict equality (no secret→bytes). Request-step `CONTINUE` does **not** run `applyExtract`; extracts are `StepKind.EXTRACT` steps that read `last` (Wenxin pattern). Legacy: `TrafficDaHuaClient.login` + `TrafficDaHuaUtils.calculateSign`.

## Goals / Non-Goals

**Goals:**

- Engine unlock: `hasher.md5` (bytes in) + `HASH` sink + declared sink on concat/sorted-query `secretRef` parts; auth-hop 401 CONTINUE ≠ CHALLENGE.
- Protocol unlock: table + YAML + FakeTransport for no-`method` nested MD5 → `X-Subject-Token`.
- Keep Wenxin / Gaode / IDPS / Huawei green.

**Non-Goals:**

- RSA; `method=simple` / blank-method branches; keepalive / unauthorize.
- Host / Catalog / AuthProvider / Groovy / public internet.
- Modeling hop-1 401 as Flow `CHALLENGE`.
- Changing `canonicalizer.concat` default ports used by Gaode/IDPS beyond sink selection for `secretRef` parts.
- Treating username as a plain non-secret string to dodge HASH (rejected in cross-review).

## Decisions

### D1. Freeze no-method nested MD5 branch

**Choice:** Protocol table and FakeTransport fixture **omit** the `method` key entirely (so legacy `firstResp.contains("method")` is false). Signature = five-step nest: `md5(pwd)`, `md5(user+p1)`, `md5(p2)`, `md5(user:realm:pTmp)`, `md5(enc:randomKey)`, lowercase hex. Fixture `realm` MUST be non-blank (legacy does not validate realm; empty would stringify oddly).

**Why:** General production path when body has no `method` field.

**Alternatives:** `method=simple` — smaller graph, less representative. Script — rejected.

### D2. hasher.md5 is bytes-in → lowercase hex-out

**Choice:** Node type `hasher.md5`: required port `in` typed **`bytes` only**, `out` lowercase hex **string**. Do **not** declare a union type on `in` (validator is exact equality today). All material reaches the hasher as bytes:

- Password alone: one-part `canonicalizer.concat` with `secretRef` + `sink: HASH` → `hasher.in`.
- Mixed username+digest / username:realm:digest / enc:randomKey: `concat` of `secretRef` (HASH) and/or `var` string parts → `hasher.in`.

Intermediate digests bind to EXECUTION/FLOW as ordinary strings for later `var` parts.

**Why:** Avoids inventing union ports; reuses concat; keeps crypto in Pipeline.

**Alternatives:** Special-case `typesCompatible(secret, bytes)` for hasher only — more validator magic. hasher `in: secret` like HMAC key — cannot also accept concat bytes without a second port.

### D3. HASH sink + honor declared sinks on concat and sorted-query

**Choice:** Add `SecretSink.HASH`. Any `secretRef` that feeds a hasher (today: concat parts whose bytes go into `hasher.md5`) MUST declare `sink: HASH`. Change `PipelineExecutor.concat` **and** sorted-query `resolvePart` to use the **declared** sink when present; if omitted, default remains `HMAC` (Gaode/IDPS unchanged). On a **hash admission path**, missing sink MUST be denied (do not silently default to HMAC). Wrong-api destination checks apply to HASH. Secrets MUST NOT connect directly to `hasher.md5.in` (D2).

**Why:** Proposal called out both hardcoders; concat-only fix would leave sorted-query inconsistent. Username nest needs HASH on concat `secretRef` parts (D7).

**Alternatives:** Only fix concat — rejected (proposal/tasks mismatch).

### D4. Auth hop shape: CONTINUE then EXTRACT steps (not inline extract)

**Choice:** Challenge authorize is a **request** step: `when: all` of status 401, `$.randomKey` exists, `$.realm` exists, and `$.encryptType` equals `MD5` → `CONTINUE`; else FAIL + AUTH_FAILED. **Do not** put `extract` on the request step (runtime ignores it there). After CONTINUE, use **separate EXTRACT steps** (one field per step today): `$.realm`, `$.randomKey`, `$.encryptType` into FLOW/EXECUTION string vars. Then pipeline signature steps. Token authorize request → CONTINUE on 200 → EXTRACT `$.token` as secret → `session.token` + `onCommit` VALID/generation. No `extraCommitOn: CHALLENGE`. Business: 401 → `AUTHENTICATE` then `REPLAY_REQUEST`.

**Why:** Matches Wenxin and actual `FlowRuntime` (`applyExtract` only on `StepKind.EXTRACT`).

**Alternatives:** Inline extract on request — would require a Runtime change out of scope. Single multi-target extract — not supported today.

### D5. Session token header is X-Subject-Token with explicit HEADER sink

**Choice:** Business header `X-Subject-Token` binds `session.token` with **`sink: HEADER`** (must be explicit — `RequestRenderer` defaults missing secret sink to `AUTHORIZATION`). Stub business under `https://dahua.example/...`.

**Why:** Production uses `X-Subject-Token`, not Bearer.

### D6. FakeTransport sequence

**Choice:** Business 401 → one challenge authorize (401 + fixed non-blank realm / randomKey / encryptType=MD5, **no method key**) → EXTRACT×3 → nested MD5 pipelines → one token authorize (200 + token/duration) → EXTRACT token + VALID → replay business 200. Assert hop-2 body `signature` equals table hex and replay has `X-Subject-Token=test-token`. Second execute: zero authorize calls. Challenge 401 without `randomKey` → cooldown storm.

### D7. Username enters nest via HASH on concat secretRef (not var stringify)

**Choice:** Username remains secret material. For `md5(user+p1)` and `md5(user:realm:pTmp)`, concat parts use `secretRef` with **`sink: HASH`**, plus `var` for prior hex digests / realm / randomKey. **Forbidden:** assigning username to a var and relying on `readVar`/`stringify` of `SecretValue` (bypasses sink policy today).

**Why:** Cross-review blocker — nest uses username twice; treating it as plaintext dodges secret boundary.

**Alternatives:** Fixture username as GLOBAL string — rejected (non-goal).

### D10. Two `type: secret` credentials (not username-password composite)

**Choice:** Definition credentials are two independent `type: secret` entries, e.g. `dahuaUser` / `dahuaPass` (fixtures `test-user` / `test-pass`), each with a `value` ref and matching `apiId`. Authorize JSON `userName` and concat `secretRef` parts both use these ids with field `value` (current `PipelineExecutor.concat` / `requireCredential(..., "value")`). Hop-1/hop-2 body builds `userName` via credential ref or HASH-safe path that does not stringify a secret var into concat.

**Why:** Second-pass review — composite `username-password` needs `field: username|password`, but concat hardcodes `"value"`. Two secrets avoid a Runtime schema change this gate.

**Alternatives:** Extend concat parts with `field:` — deferred; more surface than the protocol gate needs.

### D8. Freeze login constants in the protocol table

**Choice:** Table/YAML freeze: `clientType: "web"`, hop-2 `expiredTime: 86400`, headers `Content-Type: application/json;charset=UTF-8`, `X-Api-Version: V1.0` on both authorize hops (and business if asserted). FakeTransport MAY ignore header checks, but definition must emit them.

**Why:** Legacy defaults / 415-on-wrong-Content-Type; avoids silent drift.

### D9. Auth flow step order (canonical)

```
bind credentials
→ challengeAuthorize (401 + CONTINUE)
→ extract realm / randomKey / encryptType
→ pipeline nest (concat+HASH + hasher.md5 ×5)
→ tokenAuthorize (200 + CONTINUE)
→ extract token + session VALID
→ (return) REPLAY business
```

## Risks / Trade-offs

- [Concat/sorted-query sink default change] → Omit sink ⇒ HMAC; declare HASH only where needed; regression Gaode/IDPS/Huawei.
- [Verbose nest YAML] → Prefer clear multi-step pipelines over one opaque mega-graph.
- [401 CONTINUE already works by accident] → Task 3.1 locks the contract.
- [readVar still reveals secrets without sink] → D7 forbids that path for username; do not “fix” stringify in this change unless a follow-up OpenSpec opens.
