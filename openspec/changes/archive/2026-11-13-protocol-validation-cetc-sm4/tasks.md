# Tasks

## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/cetc.md` for POST `https://cetc.example/detect/third/traffic/event/collect`, truth source `CetcClient.postRequest` and `CetcUtils.sm4Encrypt`. Freeze the UTF-8 plaintext `{"alarmNo":"a1","alarmTime":"2026-01-01 00:00:00","deviceCode":"dev-1","factory":"test","picPath":"https://cetc.example/p","type":"98","videoPath":"https://cetc.example/v"}` and the body `{"data":"ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g="}`. Record `Content-Type: application/json;charset=UTF-8`, key `test-cetc-secret` (16 bytes), SM4/ECB/PKCS5Padding, standard Base64, and a plaintext 200 response. State that the design Non-Goals are out of scope. Verify the plaintext and the published body both appear in the table.

## 2. cipher.sm4-ecb

- [x] 2.1 Add `SecretSink.ENCRYPT` and `cipher.sm4-ecb` using BouncyCastle `SM4Engine` with PKCS7 padding and no `Security.addProvider`. Omitted encoding or `base64` emits RFC 4648 Base64 with no line wrap. A key whose UTF-8 length is not 16 fails before HTTP with a message that omits the key. Verify `Sm4EcbCipherTest` shows the frozen plaintext and `test-cetc-secret` encode to the published Base64, that encoding `hex` is rejected, and that a non-16-byte key fails without the key in the message.
- [x] 2.2 Register `cipher.sm4-ecb` in `PipelineGraphValidator` and `PipelineExecutor`. The `.key` edge requires sink `ENCRYPT`; `HMAC`, `HASH`, `AUTHORIZATION`, `BODY`, or a missing sink is rejected. A `secretRef` edge directly into `.in` fails compile. Sink `ENCRYPT` on a body concat stays denied. Leave the `hasher.md5`, `hasher.sm3`, and wire-body BODY rules unchanged. Verify `CipherSm4EcbPipelineTest` covers the published Base64, the direct-secret compile failure, the `hex` encoding failure, ENCRYPT success with no secret in DecisionTrace, HMAC denial, and ENCRYPT-on-body denial. Verify `HasherSm3PipelineTest` and `HasherMd5PipelineTest` still pass.

## 3. Canonical YAML

- [x] 3.1 Add `docs/design/v2.7-protocols/cetc.yaml`, definition id `cetc`, `authProfile` `cetc-sm4-body-v1`. Encrypt pipeline: concat of `global.plainLiteral`, then `cipher.sm4-ecb` `encoding: base64`, key edge `secretRef: appSecret` sink `ENCRYPT`, bound to `flow.ciphertext`. Body pipeline: empty-separator concat of `{"data":"`, `flow.ciphertext`, and `"}`, output type bytes. POST step uses passthrough. Replay `UNSAFE`, `allowAutomaticReplay: false`, `maxAttempts: 1`. The only transition is 200 → `SUCCESS`. No authentication flow. Verify the YAML contains `cipher.sm4-ecb` and no `cipher.cetc-sm4`.
- [x] 3.2 Add `ProtocolDefinitions.cetc()` and verify the definition compiles, `planId` is stable across two compiles, and differs from `ProtocolDefinitions.shuyun()`.

## 4. FakeTransport gate

- [x] 4.1 Add `TrafficCetcProtocolTest`. Verify the POST path, `Content-Type`, and body equal the published vector and not the plaintext. Verify 200 `{"code":200,"msg":"ok"}` yields SUCCESS and those response bytes are unchanged. Verify `test-cetc-secret` is absent from query, headers, body, DecisionTrace, and exception messages, and `cipher.cetc-sm4` fails compile with zero HTTP. FakeTransport MUST NOT call the public internet.
- [x] 4.2 If concat and `cipher.sm4-ecb` cannot produce the published body, stop and update this change's spec and design. Verify the tree has no `cipher.cetc-sm4`, no Flow-side SM4, and no production host or secret.

## 5. Existing gates

- [x] 5.1 Re-run the Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, Artemis POST, PMS, Keytop, and Shuyun protocol tests. Verify they still pass, including the published Shuyun signatures, the Keytop `key`, the PMS token, and both Artemis signatures.
