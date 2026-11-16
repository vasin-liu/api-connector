# Tasks

## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/dahua-tcpl.md` for `GET https://dahua-tcpl.example/jg/AuthPlatform/accounts/publicKey`, `POST https://dahua-tcpl.example/jg/AuthPlatform/accounts/login`, and stub `GET /jg/tcpl/stub`. Truth source is `DahuaTcplClient.publicKey`, `login`, and `getSecurityPassword`. Freeze `s1` `380e5dc89564f30713ad54bf06aacea8`, `s2` `cb2cb0b9f11b040b2a169e768d266db8`, the D1 SPKI, the D1 PKCS#8, `test-user`, `test-pass`, `test-session-id`, and `test-token`. State RSA/ECB/PKCS1Padding, unchunked Base64, login field order `userName`, `password`, `publicKey`, `subjectSessionId`, header `X-Subject-SessionId`, and business header `X-Subject-Token`. State that the design Non-Goals are out of scope, including video-cloud `encryptType=RSA` and keepalive `PUT`. Verify the table contains both digests and both keys.

## 2. cipher.rsa-pkcs1

- [x] 2.1 Add `cipher.rsa-pkcs1` using JDK `Cipher` `RSA/ECB/PKCS1Padding` and no `Security.addProvider`. `.key` bytes are the UTF-8 of an X.509 SPKI Base64 string. Omitted encoding or `base64` emits RFC 4648 Base64 with no line wrap. Invalid SPKI fails before HTTP with a message that omits the key text. Verify a unit test encrypts the UTF-8 of `s2` under the D1 public key, decrypts with the D1 private key back to `s2`, rejects encoding `hex`, and fails an invalid key without the key text in the message. Two encryptions are not required to match.

- [x] 2.2 Register `cipher.rsa-pkcs1` in `PipelineGraphValidator` and `PipelineExecutor`. `.in` and `.key` are bytes. A `secretRef` edge into either port fails compile. Do not add a `SecretSink` value, and leave the `cipher.sm4-ecb` `ENCRYPT` rule unchanged. Verify a pipeline test covers decrypt-to-`s2`, the direct-secret compile failure, the `hex` encoding failure, and an HMAC-sink denial on `cipher.sm4-ecb.key` that emits no SM4 ciphertext. Verify `CipherSm4EcbPipelineTest`, `HasherMd5PipelineTest`, and `HasherSm3PipelineTest` still pass.

## 3. Canonical YAML

- [x] 3.1 Add `docs/design/v2.7-protocols/dahua-tcpl.yaml`, definition id `dahua-tcpl`, `authProfile` `dahua-tcpl-rsa-v1`. Credentials `tcplUser` and `tcplPass`. EXECUTION variables in order `userName`, `password`, `publicKey`, `subjectSessionId` only. FLOW `s1`, `s2`, and `passwordCipher`. Pipelines: HASH concat plus `hasher.md5` for `s1` and `s2`; concat of `flow.s2` into `cipher.rsa-pkcs1.in`; concat of `execution.publicKey` into `.key`; `codec.json` login body. Business `GET /jg/tcpl/stub` uses `X-Subject-Token` sink `HEADER`, replay `SAFE` / `allowAutomaticReplay: true` / `maxAttempts: 2`. Public-key GET and login POST are `UNSAFE` / `allowAutomaticReplay: false` / `maxAttempts: 1`. Auth flow follows design D6. Verify the YAML contains `cipher.rsa-pkcs1` and no `cipher.dahua-rsa`.

- [x] 3.2 Add `ProtocolDefinitions.dahuaTcpl()` and verify the definition compiles, `planId` is stable across two compiles, and differs from `ProtocolDefinitions` for traffic-dahua.

## 4. FakeTransport gate

- [x] 4.1 Add `TrafficDahuaTcplProtocolTest`. Verify the public-key GET, the login path, `Content-Type`, `X-Subject-SessionId`, and JSON field order. Verify `password` decrypts with the D1 private key to `s2`, and a second login's `password` also decrypts to `s2`. Verify the first business 401 runs one public-key GET and one login POST, stores `test-token`, and replays `X-Subject-Token: test-token`. Verify a second execute records zero public-key calls, zero login calls, and one business call. Verify a 200 without `publicKey` stores no token and a follow-up during cooldown does not storm login. Verify `test-pass` is absent from query, headers, body, DecisionTrace, and exception messages, `test-user` is absent from DecisionTrace and exception messages and present as `userName`, and `cipher.dahua-rsa` fails compile with zero HTTP. FakeTransport MUST NOT call the public internet.

- [x] 4.2 If concat, `hasher.md5`, and `cipher.rsa-pkcs1` cannot produce a `password` that decrypts to `s2`, stop and update this change's spec and design. Verify the tree has no `cipher.dahua-rsa`, no Flow-side RSA or MD5, and no production host or the `DahuaTcplExpectations` key.

## 5. Existing gates

- [x] 5.1 Re-run the Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, Artemis POST, PMS, Keytop, Shuyun, and CETC protocol tests. Verify they still pass, including the traffic-dahua nested-MD5 signature, the CETC published body, and the Shuyun signatures.
