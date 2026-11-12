# Design

## Context

See `proposal.md` for why. Truth source is `ShuyunClient.sign` and `getBusiHeader` (read-only; no dependency). `sign` updates an `SM3Digest` with `str.getBytes()` (platform default charset) of `appId + appSecret + timestamp + nonce` and emits BouncyCastle lowercase hex. `getSignHeader` sets `x-app-id`, `nonce`, `timestamp`, and `signature`. `getBusiHeader` adds `operationUserId`, `operationUserName`, `operationUserCode`, and `operationAppCode` with defaults `1`, `idps`, `idps`, and `jtdn`. Those four headers are not hashed. The body is not hashed.

Catalog today: `canonicalizer.concat` (empty separator, `var` / `value` / `secretRef` parts, UTF-8), `hasher.md5` (`hex` / `hex-upper` / `base64` via `DigestEncoding`). `hasher.md5`'s direct feeder is the only concat whose `secretRef` must be sink `HASH`. `bcprov-jdk18on` is already on the runtime classpath and no main source imports it. `RETRY_FLOW` runs the authentication flow before another send of the same step, and fails closed when that flow is missing. Artemis and Gaode restamp inside authentication with no authentication HTTP. `Phase0ApiClient` already accepts `ScriptedClock` and `ScriptedNonce`.

## Goals / Non-Goals

**Goals:**

- One FakeTransport POST whose `signature` equals the published lowercase SM3, and whose second send after 503 carries the second published digest.
- Add `hasher.sm3` only. Keep concat, and keep the existing `hasher.md5` HASH-feeder rule intact.
- Authentication restamps timestamp and nonce and performs zero HTTP.

**Non-Goals:**

- `pageList`, `complete`, and any second route.
- Hutool `nanoId` alphabet or length. Nonces come from the existing `generate: nonce` source.
- Matching platform-default `getBytes()`. The contract is UTF-8. The fixtures are ASCII, so both charsets hash the same bytes.
- SM4, 讯飞 WebSocket, Keytop empty/nested filtering, Host / Catalog / Groovy.

## Decisions

### D1. One list route and synthetic fixtures

**Choice:** POST `https://shuyun.example/sznszhddpt/rest/event/list`. App id `test-app-id` is a GLOBAL string, not a secret. App secret ref `secret/shuyun/app-secret` resolves to `test-app-secret`. Body is the UTF-8 literal `{"eventType":"200170001"}`. `authProfile` is `shuyun-sm3-header-v1`.

**Why:** `/sznszhddpt/rest/event/list` is the `listEvent` path. The event-type literal is the client's public parking-event code, and the signature does not cover the body. The legacy test host and app secret stay out of git.

**Alternatives:** Sign a body copied from a live query — no published witness, and it would drag production traffic into the fixture. Treat the app id as a secret — it is sent as `x-app-id`.

### D2. Published vectors

Computed with BouncyCastle `SM3Digest` over UTF-8, same call shape as `ShuyunClient.sign`, then lowercase hex.

| Send | Material | `signature` |
|------|----------|-------------|
| r1 | `test-app-idtest-app-secret1000n1` | `bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb` |
| r2 | `test-app-idtest-app-secret1001n2` | `c6261c5b5b0eb59b92993bcb8954aec24e5436e1c50b8246d80765e9733aa882` |

Uppercase of the r1 digest MUST NOT be `signature`. Clock values are `1000` then `1001`. Nonce values are `n1` then `n2`. The letter in the nonce keeps the material from looking like a single integer.

### D3. `hasher.sm3` beside `hasher.md5`

**Choice:** New node `hasher.sm3`. One bytes input, string output. `config.encoding` uses the existing `DigestEncoding` set: omitted or `hex` is lowercase hex, `hex-upper` is uppercase hex, `base64` is RFC 4648 of the raw 32 bytes. Implementation calls `SM3Digest` directly. No `Security.addProvider`. The Shuyun definition sets `encoding: hex`.

The concat that feeds `hasher.sm3` follows the `hasher.md5` feeder rule: a `secretRef` part on that concat declares sink `HASH`; `HMAC`, `AUTHORIZATION`, `BODY`, or a missing sink is rejected. A `secretRef` edge straight into `hasher.sm3.in` fails compile. Do not rewrite the `hasher.md5` requirement.

**Why:** SM3 is a hash of bytes, like MD5, not an HMAC. Reusing `DigestEncoding` avoids a second parser. Direct `SM3Digest` matches the truth source and uses the dependency already declared.

**Alternatives:** A `hasher` node with an `algorithm` field — splits one catalog entry that every current definition names explicitly. Register BC as a JCA provider and call `MessageDigest.getInstance("SM3")` — extra global state for the same digest. Inline SM3 in Flow — forbidden by the pipeline boundary.

### D4. Concat, then hash; body is not the hash input

**Choice:**

1. Business flow assigns `execution.timestamp` with `{ now: epochMillis }` and `execution.nonce` with `{ generate: nonce }`.
2. `canonicalizer.concat`, `separator: ""`, parts in order: `global.appId`, `secretRef` app secret sink `HASH`, `execution.timestamp`, `execution.nonce`. Then `hasher.sm3` `encoding: hex`, bound to `flow.signature`.
3. Request headers: `Content-Type: application/json;charset=UTF-8`, `x-app-id`, `nonce`, `timestamp`, `signature`, and the four operation literals. Body pipeline is a concat of the frozen JSON text, not `codec.json` of the execution object.

**Why:** Empty-separator concat is already UTF-8. `codec.json` would emit execution fields the vendor body does not have. The operation headers are request literals because they are not signature material.

**Alternatives:** Put the operation headers into the concat and exclude them — concat has no exclude list. Hash in an authentication HTTP call — `sign` does not call the network.

### D5. 503 → `RETRY_FLOW` with a stamp-only authentication flow

**Choice:** `replayability: CONDITIONALLY_SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`, `onStaleFlowBinding: RETRY_FLOW`, `maxAuthAttempts` at least 2. Business transitions: 503 → `RETRY_FLOW`, 200 → `SUCCESS`. Authentication repeats the timestamp assign, the nonce assign, and the sign pipeline, and has no request step.

**Why:** `RETRY_FLOW` without an authentication flow fails closed after one HTTP call. Artemis already shows that this flow can assign a new nonce and return without HTTP, after which the same business step is sent again. The first send consumes clock `1000` and nonce `n1`. The authentication restamp consumes `1001` and `n2`. The business stamp steps do not run again on that retry. A third scripted value is unused. If a second send shows another pair, stop and update this change; do not edit the published digests to fit.

**Alternatives:** `RETRY_REQUEST` — leaves the first timestamp and nonce in place, so `signature` cannot change. A remote login inside authentication — `ShuyunClient` has no login hop.

### D6. Stop rather than invent a vendor node

**Choice:** If concat, `hasher.sm3`, or the stamp-only authentication flow cannot produce these two signatures, stop and update this change's spec and design. Do not hash in Flow, do not add `hasher.shuyun-sm3`, and do not copy the legacy test's host or secret into git.

## Risks / Trade-offs

- [Platform-default `getBytes()` differs from UTF-8 for non-ASCII secrets] → Fixtures are ASCII. The protocol table states UTF-8 as the contract.
- [`hasher.md5` HASH checks ignore `hasher.sm3`] → The SM3 feeder rule is a separate requirement. Implementation extends the check; it does not loosen the MD5 rule.
- [BouncyCastle becomes a real runtime use, not only a declared dependency] → Call `SM3Digest` only. No provider registration, no SM4.
- [Body event code looks like production traffic] → It is the client's public type constant. The signature vector does not include it.
- [Spare third clock and nonce values] → Same shape as the Artemis script. The published r2 pair is the assertion.

## Migration Plan

No runtime migration. The change adds a pipeline node, a protocol table, Canonical YAML, and a FakeTransport test. Existing protocol gates stay on `hasher.md5` and `signer.hmac-sha256`.

## Open Questions

None.
