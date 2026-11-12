# Tasks

## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/shuyun.md` for POST `https://shuyun.example/sznszhddpt/rest/event/list`, truth source `ShuyunClient.sign` and `getBusiHeader`. Freeze UTF-8 material `test-app-idtest-app-secret1000n1` → `bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb` and `test-app-idtest-app-secret1001n2` → `c6261c5b5b0eb59b92993bcb8954aec24e5436e1c50b8246d80765e9733aa882`. Mark the uppercase r1 digest as MUST NOT. Record headers `x-app-id`, `nonce`, `timestamp`, `signature`, `Content-Type: application/json;charset=UTF-8`, and the four operation literals `1` / `idps` / `idps` / `jtdn`. State that the body `{"eventType":"200170001"}` is not hashed, fixtures are `test-app-id` / `test-app-secret`, and the design Non-Goals are out of scope. Verify both digests appear in the table.

## 2. hasher.sm3

- [x] 2.1 Add `hasher.sm3` using BouncyCastle `SM3Digest` and the existing `DigestEncoding` (`hex`, `hex-upper`, `base64`; omitted means lowercase hex). Verify `Sm3HasherTest` shows UTF-8 bytes of `test-app-idtest-app-secret1000n1` encode to the r1 digest, that `hex-upper` is not that lowercase digest, and that `base64` is not that hex string.
- [x] 2.2 Register `hasher.sm3` in `PipelineGraphValidator` and `PipelineExecutor`. A concat that feeds it requires sink `HASH` on each `secretRef` part; `HMAC`, `AUTHORIZATION`, `BODY`, or a missing sink is rejected. Keep the existing `hasher.md5` feeder rule. A `secretRef` edge directly into `hasher.sm3.in` fails compile. An encoding outside the three values fails validation. Verify `HasherSm3PipelineTest` covers lowercase hex, the direct-secret compile failure, the unknown encoding, HASH success with no secret in DecisionTrace, and BODY denial. Verify `HasherMd5PipelineTest` still passes.

## 3. Canonical YAML

- [x] 3.1 Add `docs/design/v2.7-protocols/shuyun.yaml`, definition id `shuyun`, `authProfile` `shuyun-sm3-header-v1`. Business flow stamps `{ now: epochMillis }` and `{ generate: nonce }`, then empty-separator concat of `global.appId`, app secret sink `HASH`, timestamp, and nonce, into `hasher.sm3` `encoding: hex` bound to `flow.signature`. Body pipeline is a concat of the frozen JSON, not `codec.json`. Replayability `CONDITIONALLY_SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`, `onStaleFlowBinding: RETRY_FLOW`, 503 → `RETRY_FLOW`, 200 → `SUCCESS`. Authentication repeats the stamp and sign steps and has no request step. `maxAuthAttempts` at least 2. Verify the YAML contains `hasher.sm3` and no `hasher.shuyun-sm3`.
- [x] 3.2 Add `ProtocolDefinitions.shuyun()` and verify the definition compiles, `planId` is stable across two compiles, and differs from `ProtocolDefinitions.keytop()`.

## 4. FakeTransport gate

- [x] 4.1 Add `TrafficShuyunProtocolTest` with `ScriptedClock(1000, 1001, 1002)` and `ScriptedNonce("n1", "n2", "n3")`. Verify the first POST path, `x-app-id`, `timestamp` `1000`, `nonce` `n1`, `signature` equal to the r1 digest and not the uppercase digest, the four operation headers, and body `{"eventType":"200170001"}`. Verify 503 then 200 yields a second new request with `timestamp` `1001`, `nonce` `n2`, `signature` equal to the r2 digest, and the same body bytes. Verify authentication performs zero HTTP calls, `test-app-secret` is absent from query, headers, body, and DecisionTrace, and `hasher.shuyun-sm3` fails compile with zero HTTP. FakeTransport MUST NOT call the public internet.
- [x] 4.2 If concat, `hasher.sm3`, or the stamp-only authentication flow cannot produce those two signatures, stop and update this change's spec and design. Verify the tree has no `hasher.shuyun-sm3`, no Flow-side SM3, and neither the legacy test host nor its app secret.

## 5. Existing gates

- [x] 5.1 Re-run the Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, Artemis POST, PMS, and Keytop protocol tests. Verify they still pass, including the published Keytop `key`, the PMS token, and both Artemis signatures.
