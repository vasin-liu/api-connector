# Design

## Context

See `proposal.md` for why. Truth source is `QyhMetroClient.inOutRecord` and `getSHA256` (read-only; no dependency). The signature is uppercase hex SHA-256 of `timestamp + token + nonce + timestamp`. `timestamp` is `System.currentTimeMillis() / 1000`. The live nonce is `String.valueOf(Math.random()).substring(2)`. Query values are concatenated raw, including a space in `yyyy-MM-dd HH:mm:ss`.

`hasher.sm3` already digests concat bytes through `DigestEncoding` (`hex`, `hex-upper`, `base64`). `{ now: epochSeconds }` fails in `DefinitionValidator` and `FlowRuntime`. `RETRY_FLOW` clears FLOW, runs authentication, then repeats the same request step. It does not re-run earlier business steps. Shuyun already assigns freshness in both flows and recomputes the digest inside authentication.

## Goals / Non-Goals

**Goals:**

- One FakeTransport GET whose first signature and post-503 signature equal the published vectors.
- Add `hasher.sha256` by the same path as `hasher.sm3`.
- Admit `{ now: epochSeconds }` as integer seconds of the injected clock.

**Non-Goals:**

- A Yuhaoban definition, even though its digest formula matches.
- Percent-encoding query values, or a new `SecretSink`.
- Classifying the metro JSON `success` flag.

## Decisions

### D1. Fixture identities

**Choice:** Host `https://qyh-metro.example`, path `/jtj/in-out`. Query `start_time=2026-01-01T00:00:00` then `end_time=2026-01-01T00:10:00`. Pass id literal `test-pass`. Token credential `test-metro-token`.

**Why:** `QyhMetroExtendParam` defaults are a production host, pass id, and token.

**Alternatives:** Copy those defaults into the fixture. Rejected.

### D2. Published vectors and clock order

The business flow assigns `{ now: epochSeconds }` and `{ generate: nonce }` before the first send. Authentication assigns both again only after 503. One scripted clock and one scripted nonce therefore yield the first pair on the business steps.

| Item | Value |
|------|--------|
| First instant | `1700000000000` ms → timestamp `1700000000`, nonce `1` |
| First preimage | `1700000000test-metro-token11700000000` |
| First `signature` | `F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5` |
| Second instant | `1700000002000` ms → timestamp `1700000002`, nonce `2` |
| Second preimage | `1700000002test-metro-token21700000002` |
| Second `signature` | `0B3FF5CB28670D23AA4266EF97AF9512DBB7463BE874501E10800789CF45033E` |
| Truncation | instant `1999` ms → decimal text `1` |

Both digests are JDK SHA-256 over those UTF-8 bytes, then uppercase hex. Do not edit a vector to fit a failing test.

**Why:** `RETRY_FLOW` does not re-enter the business assign steps. The second pair has to come from authentication.

**Alternatives:** Stamp the timestamp only inside authentication and force an auth hop before the first GET. The live call signs the first request directly.

### D3. `hasher.sha256` copies `hasher.sm3`

**Choice:** New hasher. `MessageDigest.getInstance("SHA-256")`, then the existing `DigestEncoding`. `.in` is bytes. Output is the string on `hasher.out`. The metro node sets `encoding: hex-upper`. Bind `flow.signature` from `hasher.out`. Do not bind `hasher.hex`: the executor adds that alias only for lowercase `hex`. No Maven dependency and no JCA provider registration. `hasher.md5`, `hasher.sm3`, and `signer.hmac-sha256` stay as they are.

**Why:** The encoding set and the HASH-sink feeder rule already exist for SM3. SHA-256 is the JDK digest, not an HMAC.

**Alternatives:** Call `signer.hmac-sha256` with an empty key. That digest is not SHA-256. Fold SHA-256 into `hasher.sm3` with an algorithm field. That loosens the SM3 node.

### D4. Control flow follows Shuyun

**Choice:** Empty-separator concat parts, in order: `execution.timestamp`, the token `secretRef` with sink `HASH`, `execution.nonce`, `execution.timestamp` again. Business steps assign the second timestamp, generate the nonce, run that pipeline, set `flow.signed` true, then GET. Authentication repeats the assign, nonce, pipeline, and `flow.signed` steps and has no request. HTTP 503 selects `RETRY_FLOW`. Limits match Shuyun: `maxAuthAttempts: 2`, `maxAuthDepth: 1`, `transitionLimit: 16`. The request is `CONDITIONALLY_SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`. Headers are templates. The GET has no body.

**Why:** Clearing FLOW drops `flow.signature`. The request step then renders whatever authentication just wrote into EXECUTION and FLOW. `RETRY_FLOW` already resets the nonce-consumed flag before authentication.

**Alternatives:** Recompute the digest in `beforeSend` only. Shuyun does not, and the signature would still need the authentication assigns after FLOW is cleared.

### D5. `epochSeconds` is a long

**Choice:** `FlowRuntime` returns `NumberValue` of `clock.millis() / 1000L`. For a positive instant, Java long division truncates toward zero. `RequestRenderer` already stringifies a number with `String.valueOf`, so the header text has no fraction. `DefinitionValidator` accepts `epochSeconds` beside `epochMillis` and `isoOffset`. `unknownNowForm` uses `{ now: unix }`.

**Why:** `epochMillis` is already a number. A string assign would be a second clock type.

**Alternatives:** Keep rejecting `epochSeconds` and divide in a pipeline node. Flow would still be unable to stamp the header from the clock.

### D6. Query values are literals without spaces

**Choice:** `start_time` and `end_time` are the `T`-separated literals above, in that order. The signature does not cover them.

**Why:** `RequestRenderer` joins query text without encoding. `URI.create` rejects a raw space. The live `yyyy-MM-dd HH:mm:ss` value is outside this gate.

**Alternatives:** Percent-encode query values in the renderer. That changes every existing definition's wire query.

## Risks / Trade-offs

- [Binding `hasher.hex` when encoding is `hex-upper`] → Bind `hasher.out`. The `.hex` alias is only written for lowercase hex.
- [Storing seconds in a 32-bit int] → Use a `long`. `1700000000` fits in an int; later instants do not need a second type.
- [Scripted clock order] → The business assign consumes `1700000000000` ms and nonce `1`. Authentication consumes `1700000002000` ms and nonce `2`.
- [Previously invalid `{ now: epochSeconds }` now compiles] → The negative validator example moves to `{ now: unix }`. Definitions that used only `epochMillis` or `isoOffset` stay valid.

## Migration Plan

Additive runtime and one new definition. No stored data. Rollback is reverting the change.
