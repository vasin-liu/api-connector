# Tasks

## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/qyh-gps.md` for `POST https://qyh-gps.example/zhjt/sportservice/basic-api/gzqyh/open/api/getToken` and `POST https://qyh-gps.example/zhjt/sportservice/basic-api/gzqyh/open/api/queryGps`. Truth source is `QyhGameVehicleGpsClient.login`, `buildHeader`, and `getVehicleGps`. Freeze design D1 identities and D2 vectors: ciphertext `8cd2182b020f1d459a719234394e2f06`, business epoch `1700000000000` before login epoch `1700000001000`, login body, both `X-Sign` values, and the percent-encoded preimage. State SM4/ECB/PKCS5, hex key and hex ciphertext, whole-string percent-encoding, and that `timeOut` is ignored. State the design Non-Goals, including support-vehicle, metro SHA256, CBC/IV, and copying `QyhGameVehicleGpsExtendParam` credentials. Verify the table contains both signatures, the login body, and the ciphertext.

## 2. cipher.sm4-ecb-decrypt

- [x] 2.1 Add `cipher.sm4-ecb-decrypt` with BouncyCastle `SM4Engine` and `PKCS7Padding` in decrypt mode, and no `Security.addProvider`. `.in` is the UTF-8 of lowercase hex ciphertext. `.key` is the UTF-8 of 32 hex characters, decoded to 16 bytes. Output is the plaintext string. No `config.encoding`. An invalid hex key or ciphertext fails before HTTP with a message that omits the key. Verify a pipeline test decrypts `8cd2182b020f1d459a719234394e2f06` under `0123456789abcdeffedcba9876543210` to `symkey-fixture`, rejects an `encoding` config, rejects a direct `secretRef` into `.in` with zero HTTP, and fails a non-32-hex key before HTTP without the key text in the message.

- [x] 2.2 Register `cipher.sm4-ecb-decrypt` in `PipelineGraphValidator` and `PipelineExecutor`. `.key` requires `secretRef` with sink `ENCRYPT`. `HASH`, `HMAC`, `AUTHORIZATION`, `BODY`, or a missing sink emits no plaintext. Do not add a `SecretSink` value. Leave `cipher.sm4-ecb` encrypting with a 16-byte UTF-8 key and Base64 output. Verify the decrypt pipeline test denies sink `HASH` on `.key` with no plaintext, and `cipher.sm4-ecb` still rejects key `0123456789abcdeffedcba9876543210` and encoding `hex`. Verify `CipherSm4EcbPipelineTest` still passes.

## 3. canonicalizer.percent-encode

- [x] 3.1 Add `canonicalizer.percent-encode` with bytes in and bytes out, using the `SortedQueryCanonicalizer.uriEncode` rules (`URLEncoder` UTF-8, then `+` to `%20`, `*` to `%2A`, `%7E` back to `~`). No encoding config. Register it in `PipelineGraphValidator` and `PipelineExecutor`. Verify a pipeline test encodes the D2 canonical preimage to `%7B%22plateNo%22%3A%22A12345%22%7D%26_t%3D1700000000000%26symKey%3Dsymkey-fixture`, encodes `a b~` to `a%20b~`, and encodes `a&b=c` to `a%26b%3Dc`.

## 4. Secret variable admission

- [x] 4.1 Require sink `HASH` on a concat part whose variable holds a `SecretValue`. A missing sink, or any sink other than `HASH`, fails validation and does not copy the secret into the concat. Accept an output binding `flow.symKey: { port, as: secret }` and store it with `ByteSecret`. A string port ref stays a plain string. Verify a pipeline test allows `HASH` on a secret variable without putting the plaintext in DecisionTrace, and rejects an omitted sink with no digest.

## 5. Send-time query sign

- [x] 5.1 On each queryGps send, after the session rebind, run pipeline `querySign` only when `session.str` is present. Bind `flow.symKey` as a secret and `flow.xSign` as a string, then render headers. When `session.str` is absent, skip `querySign` and omit `X-Access-Token` and `X-Sign`. A present but empty `session.str` fails decrypt before HTTP without the key in the message. Do not add conditional step syntax, and do not decrypt inside the authentication flow. Verify a runtime test sends one request with no `session.str` and no `X-Sign`, then one send after `session.str` is bound whose pipeline output is the published first `X-Sign`.

## 6. Canonical YAML

- [x] 6.1 Add `docs/design/v2.7-protocols/qyh-gps.yaml`, definition id `qyh-gps`, `authProfile` `qyh-gps-sm4-v1`. Credentials for the username and the agreed key. Business flow assigns `execution.businessT` with `{ now: epochMillis }`, then sends queryGps. Authentication assigns `execution.loginT`, signs with `hasher.sm3` over a `HASH` concat, posts getToken, and on `success` true plus `$.result.str` extracts `session.token` as secret and `session.str` as a string, then commits VALID. Otherwise `AUTH_FAILED`. `querySign` decrypts `session.str` with sink `ENCRYPT`, admits `flow.symKey` with sink `HASH`, percent-encodes, and hashes. Login body and query body are concats, not `codec.json`. queryGps replay is `SAFE` / `allowAutomaticReplay: true` / `maxAttempts: 2`. getToken is `UNSAFE` / `allowAutomaticReplay: false` / `maxAttempts: 1`. `failureCooldown` is 5s. Verify the YAML contains `cipher.sm4-ecb-decrypt` and `canonicalizer.percent-encode`, and contains no `cipher.qyh-sm4`.

- [x] 6.2 Add `ProtocolDefinitions.qyhGps()` and verify the definition compiles, `planId` is stable across two compiles, and differs from `ProtocolDefinitions` for CETC.

## 7. FakeTransport gate

- [x] 7.1 Add `TrafficQyhGpsProtocolTest` with `ScriptedClock` ticks `1700000000000`, `1700000001000`, then `1700000002000` on the second execute. Verify the first queryGps has no `X-Access-Token` and its 401 runs one getToken whose body equals the D2 login body. Verify the replay path, query, body, `X-Time` `1700000000000`, and `X-Sign` `8da4c1ef6d160c49f7e5494c8caa76b02c677f0d795e77a914a74b81029a3647`. Verify the session contains `test-token` and `8cd2182b020f1d459a719234394e2f06` and does not contain `symkey-fixture`. Verify a second execute calls getToken zero times and sends one queryGps with `X-Time` `1700000002000` and `X-Sign` `9b4464d0f66c0b9de9d640bcfe9a5fc8126772f4533985198b85b88ea6f1675c`. Verify `success` false and a 200 without `result.str` store no token, sign no queryGps, and a follow-up during cooldown sends no HTTP. Verify `0123456789abcdeffedcba9876543210` and `symkey-fixture` are absent from query, headers, body, DecisionTrace, and exception messages; `test-user` is `X-Username` and absent from DecisionTrace and exception messages; `test-token` is `X-Access-Token` and absent from DecisionTrace and exception messages. Verify `cipher.qyh-sm4` fails compile with zero HTTP. FakeTransport MUST NOT call the public internet.

- [x] 7.2 If `hasher.sm3`, `cipher.sm4-ecb-decrypt`, and `canonicalizer.percent-encode` cannot produce the D2 login body and both `X-Sign` values, stop and update this change's spec and design. Verify the tree has no `cipher.qyh-sm4`, no Flow-side SM3, SM4, or percent-encoding, no `sm-crypto` dependency, and no production host, app id, or key from `QyhGameVehicleGpsExtendParam`.

## 8. Existing gates

- [x] 8.1 Re-run the Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, Artemis POST, PMS, Keytop, Shuyun, CETC, and Dahua TCPL protocol tests. Verify they still pass, including the CETC published body, the traffic-dahua nested-MD5 signature, and the Dahua TCPL password decrypting to `s2`.
