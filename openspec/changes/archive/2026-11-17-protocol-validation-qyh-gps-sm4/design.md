# Design

## Context

See `proposal.md` for why. Truth source is `QyhGameVehicleGpsClient.login`, `buildHeader`, and `getVehicleGps` (read-only; no dependency). Login posts `sign`, `t`, and `sysId`. `sign` is `Sm3.sm3(username + millis + sm4Key)` where `sm4Key` is the configured hex string, not the decoded key bytes. The token response field `result.str` is hex ciphertext. `Sm4.decrypt(str, sm4Key)` with `antherd` `sm-crypto` 0.3.2 defaults to ECB and `pkcs#5`: the key and ciphertext are hex, and the plaintext is UTF-8. `buildHeader` sorts the body object, appends `&_t=` + millis + `&symKey=` + that plaintext, percent-encodes the whole string with Spring `UriUtils.encode(s, "UTF-8")` (`Type.URI`, unreserved only), then SM3. The business body is the unsorted object. This gate freezes one field, so those two JSON strings are the same.

Catalog today: `cipher.sm4-ecb` encrypts and requires 16 UTF-8 key bytes and Base64 output. `hasher.sm3` emits lowercase hex. `canonicalizer.concat` parts are `var` / `value` / `secretRef`. `readVar` stringifies a `SecretValue` without a sink check. Extract already stores `as: secret` via `ByteSecret`. A request render runs the body pipeline, then renders headers. `REPLAY_REQUEST` repeats that render and does not re-run earlier business steps. `authenticate` clears FLOW and leaves EXECUTION in place. Session materials are the committed SESSION scope.

## Goals / Non-Goals

**Goals:**

- One FakeTransport sequence whose login body and replayed `X-Sign` equal the published vectors.
- Add `cipher.sm4-ecb-decrypt` and `canonicalizer.percent-encode` only. Leave `cipher.sm4-ecb` and the sink enum unchanged.
- Session holds `token` and hex `str`. The decrypted symKey exists only for the send that hashes it.

**Non-Goals:**

- A conditional step syntax. The sign pipeline is skipped inside the queryGps send when `session.str` is absent.
- Reordering the engine so login happens before the business clock is stamped.
- Response decryption of the GPS payload. `timeOut` is ignored.

## Decisions

### D1. Fixture identities

**Choice:** Host `https://qyh-gps.example`. Paths `/zhjt/sportservice/basic-api/gzqyh/open/api/getToken` and `.../queryGps`. Query pairs `app_id=test-token-app` / `api_key=test-token-key` and `app_id=test-gps-app` / `api_key=test-gps-key`. Username credential `test-user`. `sysId` literal `test-sys`. Agreed key `0123456789abcdeffedcba9876543210` (the `sm-crypto` sample key, 32 hex chars). Plate `A12345`. Token `test-token`. Decrypted symKey `symkey-fixture`. `Content-Type` is `application/json;charset=UTF-8`.

**Why:** The Java defaults in `QyhGameVehicleGpsExtendParam` are gateway credentials. The sample key is visibly not one of them.

**Alternatives:** Copy those defaults into the fixture. Rejected.

### D2. Published vectors and clock order

The business flow assigns `execution.businessT` before the first send. Login assigns `execution.loginT` only after the 401, inside authentication. One `ScriptedClock` therefore yields the business epoch first.

| Item | Value |
|------|--------|
| Ciphertext `result.str` | `8cd2182b020f1d459a719234394e2f06` |
| Business epoch / first `X-Time` | `1700000000000` |
| First `X-Sign` | `8da4c1ef6d160c49f7e5494c8caa76b02c677f0d795e77a914a74b81029a3647` |
| Login epoch `t` | `1700000001000` |
| Login body | `{"sign":"1a625df0ea71ff8731a498b38638987d0ad71e0bcfe1cb79e987de599a29c86f","t":1700000001000,"sysId":"test-sys"}` |
| Second execute `X-Time` | `1700000002000` |
| Second `X-Sign` | `9b4464d0f66c0b9de9d640bcfe9a5fc8126772f4533985198b85b88ea6f1675c` |

The canonical preimage for the first sign is `{"plateNo":"A12345"}&_t=1700000000000&symKey=symkey-fixture`. Percent-encoding makes `%7B%22plateNo%22%3A%22A12345%22%7D%26_t%3D1700000000000%26symKey%3Dsymkey-fixture`. SM3 is over that UTF-8. Both digests were computed with BouncyCastle 1.80 `SM3Digest` and `SM4Engine` + `PKCS7Padding`, which matches `pkcs#5` on a 16-byte block. Do not edit a vector to fit a failing test.

**Why:** `REPLAY_REQUEST` does not re-run the business assign, so `X-Time` is the earlier tick. The old client stamps login first; this gate follows the engine.

**Alternatives:** Stamp `X-Time` inside authentication after login. The second execute would not share that path, because a valid session does not enter authentication.

### D3. `cipher.sm4-ecb-decrypt` beside the encrypt node

**Choice:** New node. `.in` is bytes of hex text. `.key` is a `secretRef` whose UTF-8 is 32 hex chars; decode to 16 bytes, then `SM4Engine` + `PKCS7Padding` in decrypt mode. Output port is the plaintext string. No `config.encoding`. Invalid hex or a bad key fails before HTTP with a fixed message that omits the key. No `Security.addProvider`.

**Why:** `cipher.sm4-ecb` takes a raw 16-byte UTF-8 key, encrypts, and emits Base64. Folding hex and decrypt into it would loosen the CETC rule. The 32-character sample key must still fail that encrypt node.

**Alternatives:** `cipher.sm4` plus a mode field. Rejected with the CETC node. Depend on `sm-crypto` / Nashorn. Rejected.

### D4. `canonicalizer.percent-encode` encodes the whole string

**Choice:** Bytes in, bytes out. Apply the same octet rules as `SortedQueryCanonicalizer.uriEncode`: `URLEncoder` UTF-8, then `+` to `%20`, `*` to `%2A`, `%7E` back to `~`. Feed the bytes to `hasher.sm3`. Do not add an encoding flag.

**Why:** Spring `Type.URI` keeps only unreserved characters. `sorted-query` `rfc3986` encodes each pair and leaves `&` and `=` as separators. This preimage contains `{`, `"`, `&`, and `=`.

**Alternatives:** A new alphabet next to `uriEncode`. The fixture has no `~` or space in the canonical string; the `a b~` scenario keeps the shared helper honest.

### D5. Session stores ciphertext; symKey is a send-local secret

**Choice:** Extract `$.result.token` with `as: secret` to `session.token`, and `$.result.str` as a string to `session.str`. Do not extract `timeOut`. On a queryGps send that sees `session.str`, bind the decrypt output to `flow.symKey` as `ByteSecret` (same wrapper as extract `as: secret`). That binding lives in FLOW and is not copied into session materials. Output syntax:

```yaml
flow.symKey:
  port: cipher.out
  as: secret
```

A string port ref stays a plain string, which is how `flow.xSign` is bound.

**Why:** Concat cannot take another node's port. The plaintext has to land in a variable, and that variable is key material. Storing it in the session would keep it for the session TTL. ECB decrypt of the stored hex is deterministic, so the next execute can decrypt again.

**Alternatives:** Store `symkey-fixture` in the session. Rejected. Leave `flow.symKey` as a plain string. `readVar` would copy it with no sink admission.

### D6. Existing sinks, two admissions

**Choice:** The agreed key's login concat part is `secretRef` with sink `HASH`. Its decrypt `.key` edge is `secretRef` with sink `ENCRYPT`. `flow.symKey` enters the canonical concat as `var` with sink `HASH`. `SecretSinkPolicy` checks api id. A secret variable part with a missing or non-`HASH` sink fails validation and does not copy the bytes. `SecretSink` gains no value. Username uses sink `HASH` on the login concat and sink `HEADER` on `X-Username`.

**Why:** The login preimage is the hex key string. The business preimage is the decrypted symKey. Those are different bytes and different edges.

**Alternatives:** A `DECRYPT` sink. The key is still a cipher key.

### D7. Sign inside the queryGps send

**Choice:** Business flow assigns `execution.businessT`, then sends queryGps. `X-Time` is that variable. On each send, after the session rebind that already happens before render, run pipeline `querySign` only when `session.str` is present. It decrypts `session.str`, percent-encodes the canonical concat, and binds `flow.xSign`. Headers `X-Access-Token`, `X-Username`, `X-System`, `X-Time`, and `X-Sign` render after that. When `session.str` is absent, skip `querySign` and omit `X-Access-Token` and `X-Sign`. FakeTransport returns 401, which selects `AUTHENTICATE` and `REPLAY_REQUEST`.

Authentication assigns `execution.loginT`, builds the login sign, posts getToken, and on `success` true plus `$.result.str` extracts the session fields and commits VALID. Otherwise `AUTH_FAILED`. Authentication does not decrypt. The replay render sees `session.str` and runs `querySign` with the business epoch already assigned.

A later execute binds the session before the business flow, so the first send runs `querySign` and does not call getToken. `failureCooldown` is 5s. Replay of queryGps is `SAFE` / `allowAutomaticReplay: true` / `maxAttempts: 2`. getToken is `UNSAFE` / `allowAutomaticReplay: false` / `maxAttempts: 1`.

Login body is a concat of `{"sign":"`, the digest, `","t":`, the login epoch, and `,"sysId":"test-sys"}`, not `codec.json`. The query body is a concat of the frozen `{"plateNo":"A12345"}`.

**Why:** A pipeline step before the request runs on the first execute while `session.str` is still absent, and `REPLAY_REQUEST` does not run that step again. The second execute never enters authentication, so the sign cannot live only there. Render already runs once per send, including the replay.

**Alternatives:** Conditional steps. A larger flow change. Decrypting during authentication into FLOW and hoping the second execute still has it. FLOW is not session state.

### D8. Stop rather than invent a vendor node

**Choice:** If the catalog cannot produce the D2 login body and both `X-Sign` values, stop and update this change's spec and design. Do not add `cipher.qyh-sm4`, do not hash or decrypt in Flow, and do not copy a production host, app id, or key into git.

## Risks / Trade-offs

- [Business epoch is earlier than login `t`] → D2 freezes that order. A test that stamps login first will not match the vectors.
- [`symkey-fixture` survives percent-encoding because it is unreserved] → The encoded preimage is pipeline input, not a header or body. The protocol test asserts it is absent from the wire, the trace, and exception text. Do not log the preimage.
- [`readVar` reveals any `SecretValue`] → D6 rejects a secret variable part that omits `HASH` before the bytes are copied.
- [Skipping `querySign` decrypts an empty string instead] → The skip happens before the decrypt node runs. An empty `session.str` that is present still fails decrypt before HTTP, without the key in the message.
- [PKCS7 and `pkcs#5` diverge] → D2 was computed with BC 1.80 PKCS7 and matches the published ciphertext. The test asserts that hex, not a fresh encryption of another string.
- [The 401 probe is an extra queryGps] → The spec requires that probe to carry no token. The signed vector is the replay, then the second execute.

## Migration Plan

No runtime migration. The change adds two pipeline nodes, a secret-variable HASH check, send-time execution of `querySign`, a protocol table, Canonical YAML, and tests. Existing protocol gates stay on their current nodes. `cipher.sm4-ecb` tests stay green, including rejection of a 32-character key.

## Open Questions

None.
