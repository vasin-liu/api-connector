# Design

## Context

See `proposal.md` for why. Truth source is `CetcClient.postRequest` and `CetcUtils.sm4Encrypt` (read-only; no dependency). The client writes the request object with `JacksonUtils.json()` (`NON_NULL`, `SORT_PROPERTIES_ALPHABETICALLY`), encrypts that UTF-8 with `Cipher.getInstance("SM4/ECB/PKCS5Padding")` and `SecretKeySpec(appSecret.getBytes(UTF_8), "SM4")`, then Base64-encodes with `java.util.Base64.getEncoder()`. `post` sends `{"data":"<base64>"}` as Hutool JSON. The only route is `POST /detect/third/traffic/event/collect`. The response is parsed as plaintext JSON. `sm4Decrypt` is not on this path. `CetcUtils` registers `BouncyCastleProvider`; this runtime already calls `SM3Digest` directly and does not register a provider.

Catalog today: `canonicalizer.concat` (UTF-8 parts), `hasher.sm3`, `signer.hmac-sha256` with a `.key` secret edge. An HTTP body pipeline's last node must output bytes. Concat parts are `var` / `value` / `secretRef`, not ports. `SecretSink` is `AUTHORIZATION`, `HMAC`, `HEADER`, `QUERY`, `BODY`, `HASH`. `SecretSinkPolicy` checks api id only.

## Goals / Non-Goals

**Goals:**

- One FakeTransport POST whose body equals the published `{"data":...}` vector.
- Add `cipher.sm4-ecb` and sink `ENCRYPT` only. Leave the `HASH` and `BODY` admission rules unchanged.
- A 16-byte key. Output is standard Base64. The key stays off the wire, the trace, and exception text.

**Non-Goals:**

- Response decryption, optional event fields, and re-running Jackson inside the pipeline.
- 24-byte or 32-byte keys, CBC, IVs, and a mode or padding config.
- The 全运会 SM4 path (`QyhGameVehicleGpsClient`).
- A 503 retry. ECB over a fixed plaintext is deterministic, so a second body cannot prove rebuild.
- `Security.addProvider`. Host / Catalog / Groovy. Public internet. Production secrets.

## Decisions

### D1. One route and a frozen plaintext

**Choice:** POST `https://cetc.example/detect/third/traffic/event/collect`. `Content-Type` is `application/json;charset=UTF-8`. App secret ref `secret/cetc/app-secret` resolves to `test-cetc-secret` (16 UTF-8 bytes). `authProfile` is `cetc-sm4-body-v1`. The plaintext is the GLOBAL literal:

`{"alarmNo":"a1","alarmTime":"2026-01-01 00:00:00","deviceCode":"dev-1","factory":"test","picPath":"https://cetc.example/p","type":"98","videoPath":"https://cetc.example/v"}`

**Why:** That string is the alphabetical, null-omitted JSON of the six `@NotBlank` fields. Freezing it avoids a second Jackson. The host and secret are synthetic.

**Alternatives:** Serialize `EventCollectRequest` at runtime — field order becomes a library detail. Include null optional fields — `JacksonUtils` drops them.

### D2. Published vector

Computed with BouncyCastle 1.80, both `Cipher "SM4/ECB/PKCS5Padding"` (provider `BC`) and `SM4Engine` + `PKCS7Padding`. The two Base64 strings match. The body does not contain `test-cetc-secret`. A shorter key throws `InvalidKeyException` before ciphertext.

| Item | Value |
|------|--------|
| Key | `test-cetc-secret` |
| Plaintext | the D1 literal |
| Body | `{"data":"ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g="}` |

`+`, `/`, and `=` stay raw inside the JSON string. Do not edit this body to fit a failing test.

### D3. `cipher.sm4-ecb` beside the hashers

**Choice:** New node `cipher.sm4-ecb`. Ports: `.in` bytes, `.key` secret, `.out` string. Mode ECB and PKCS5/PKCS7 padding are fixed. `config.encoding` omitted or `base64` emits RFC 4648 Base64 with no line wrap. Any other encoding fails validation. Implementation uses `SM4Engine` and `PaddedBufferedBlockCipher` with `PKCS7Padding`. No `Security.addProvider`. A key whose UTF-8 length is not 16 fails before HTTP, and the failure text does not contain the key. Flow does not encrypt.

**Why:** SM4 here is a block cipher, not a digest and not an HMAC. Naming the mode in the type avoids a CBC switch before a protocol needs an IV. Direct `SM4Engine` matches the SM3 decision and the published vector.

**Alternatives:** `cipher.sm4` plus a `mode` field — invites CBC with no witness. `cipher.cetc` — a vendor node. Reuse `DigestEncoding` — its `hex` values are not this protocol. Register the JCA provider — global state the SM3 node already refused.

### D4. Key edge sink `ENCRYPT`

**Choice:** The `.key` edge's `secretRef` declares sink `ENCRYPT`. `HMAC`, `HASH`, `AUTHORIZATION`, `BODY`, or a missing sink is rejected and emits no ciphertext. A `secretRef` edge into `.in` fails compile. Sink `ENCRYPT` on a concat that supplies the HTTP body stays denied by the existing BODY rule. Do not rewrite the `hasher.md5`, `hasher.sm3`, or wire-body requirements.

**Why:** The key is consumed by the cipher. `HMAC` would call a MAC key a cipher key. `BODY` is the PMS case, where the secret bytes themselves are on the wire.

**Alternatives:** Reuse sink `HMAC` — compiles, and lies. Put the secret in the plaintext concat — the key would be encrypted into the body only if it were plaintext, and a mistaken part would place it on the wire in the clear.

### D5. Two pipelines, because the body port is bytes

**Choice:**

1. Encrypt pipeline: concat of `global.plainLiteral`, then `cipher.sm4-ecb`. The business step binds `cipher.out` to `flow.ciphertext`.
2. Body pipeline: concat, separator empty, parts `{"data":"`, `flow.ciphertext`, `"}`, output port type bytes. This pipeline is `requests.collect.body.pipeline`.
3. The POST step uses the existing passthrough identity pipeline. Replay is `UNSAFE`, `allowAutomaticReplay: false`, `maxAttempts: 1`. The only transition is 200 → `SUCCESS`. There is no authentication flow.

**Why:** Concat cannot take another node's port as a part, and the body pipeline's last output must be bytes. The cipher's string has to land in a variable first. Wenxin already uses `UNSAFE` when the call must not replay.

**Alternatives:** One pipeline whose last node is the cipher — the body port would be a string and validation rejects it. `codec.json` for the envelope — extra node, and it can escape characters the published body leaves raw.

### D6. Stop rather than invent a vendor node

**Choice:** If concat and `cipher.sm4-ecb` cannot produce the D2 body, stop and update this change's spec and design. Do not encrypt in Flow, do not add `cipher.cetc-sm4`, and do not copy a production host or secret into git.

## Risks / Trade-offs

- [JCA PKCS5Padding and lightweight PKCS7Padding diverge] → D2 was computed with both on BC 1.80 and they match. The test asserts that body, not a fresh encryption of a different string.
- [`ENCRYPT` accidentally satisfies the wire-body check] → The BODY rule remains "sink must be BODY". An `ENCRYPT` part on the body concat fails validation.
- [A non-16-byte key throws with the key inside the message] → Catch it and fail with a fixed message that omits the secret. The protocol test asserts `test-cetc-secret` is absent from the exception text.
- [Base64 is JSON-escaped] → The envelope is concat of three literals, not a JSON encoder. `+`, `/`, and `=` must survive.
- [Source comment says the key may be 24 or 32 characters] → SM4 is 128 bits. Those lengths fail before HTTP. They are not a second mode.

## Migration Plan

No runtime migration. The change adds an enum value, a pipeline node, a protocol table, Canonical YAML, and tests. Existing protocol gates stay on `hasher.md5`, `hasher.sm3`, and `signer.hmac-sha256`.

## Open Questions

None.
