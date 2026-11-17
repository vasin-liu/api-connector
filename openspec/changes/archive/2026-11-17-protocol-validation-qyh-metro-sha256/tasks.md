# Tasks

## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/qyh-metro.md` for `GET https://qyh-metro.example/jtj/in-out`. Truth source is `QyhMetroClient.inOutRecord` and `getSHA256`. Freeze design D1 identities and D2 vectors: query `start_time=2026-01-01T00:00:00&end_time=2026-01-01T00:10:00`, pass id `test-pass`, token `test-metro-token`, first signature `F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5`, second signature `0B3FF5CB28670D23AA4266EF97AF9512DBB7463BE874501E10800789CF45033E`. State that the timestamp is epoch seconds, the nonce is the injectable source, and the digest is uppercase SHA-256. State the design Non-Goals, including Yuhaoban, the live space-separated query time, `Math.random()`, and copying `QyhMetroExtendParam` credentials. Verify the table contains both signatures and both preimages.

## 2. Epoch seconds

- [x] 2.1 Admit `{ now: epochSeconds }` in `DefinitionValidator` and `FlowRuntime`. The value is `clock.millis() / 1000L` as a `NumberValue`. Update the rejection text so unknown forms still fail and `epochSeconds` is no longer one of them. Verify `FlowAssignFunctionsTest` shows instant `1999` ms assigns decimal text `1`, and instant `1700000000000` ms assigns decimal text `1700000000`. Verify `DefinitionValidatorTest.unknownNowForm` rejects `{ now: unix }` with `VAL_ASSIGN_FORM`, and `{ now: epochMillis }` and `{ now: isoOffset }` still validate.

## 3. hasher.sha256

- [x] 3.1 Add `hasher.sha256` beside `Sm3Hasher`, using JDK `MessageDigest` SHA-256 and the existing `DigestEncoding`. No new Maven dependency and no JCA provider. Register it in `PipelineGraphValidator` (`KNOWN`, the digest port case, and every HASH-feeder check that names `hasher.sm3`) and in `PipelineExecutor`. `.in` is bytes. The string result is `hasher.out`. Do not add a `.hex` alias for `hex-upper`. A direct `secretRef` into `.in` fails compile. A concat secret that feeds this node requires sink `HASH`; `HMAC`, `AUTHORIZATION`, `BODY`, or a missing sink emits no digest. Leave `hasher.md5`, `hasher.sm3`, and `signer.hmac-sha256` unchanged. Verify `HasherSha256PipelineTest` hashes the D2 first preimage to `F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5` with `hex-upper`, emits lowercase hex when encoding is omitted, rejects encoding `sha`, rejects a direct `secretRef` into `.in` with zero HTTP, allows sink `HASH` without the token in DecisionTrace, and denies sink `BODY` with no digest. Verify `HasherSm3PipelineTest` still passes.

## 4. Canonical YAML

- [x] 4.1 Add `docs/design/v2.7-protocols/qyh-metro.yaml`, definition id `qyh-metro`, `authProfile` `qyh-metro-sha256-v1`. Token credential `test-metro-token`. Pass id is the literal `test-pass`. Business steps assign `{ now: epochSeconds }` and `{ generate: nonce }`, run the empty-separator concat plus `hasher.sha256` (`encoding: hex-upper`), set `flow.signed` true, then GET. Authentication repeats those assign, nonce, pipeline, and `flow.signed` steps and sends no HTTP. Bind `flow.signature` from `hasher.out`. Headers are `x-tif-paasid`, `timestamp`, `nonce`, and `signature`. Query order is `start_time` then `end_time`. No body. HTTP 503 selects `RETRY_FLOW`. Limits and replay match design D4. Verify the YAML contains `hasher.sha256` and `epochSeconds`, and contains no `hasher.qyh-sha256`.

- [x] 4.2 Add `ProtocolDefinitions.qyhMetro()` and verify the definition compiles, `planId` is stable across two compiles, and differs from `ProtocolDefinitions.qyhGps()`.

## 5. FakeTransport gate

- [x] 5.1 Add `TrafficQyhMetroProtocolTest` with `ScriptedClock` ticks `1700000000000` then `1700000002000`, and nonce `1` then `2`. Verify the first GET is `https://qyh-metro.example/jtj/in-out` with the D2 query, no body, `x-tif-paasid` `test-pass`, `timestamp` `1700000000`, `nonce` `1`, and `signature` `F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5`. Verify HTTP 503 runs authentication with zero HTTP and the next GET keeps the query, with `timestamp` `1700000002`, `nonce` `2`, and `signature` `0B3FF5CB28670D23AA4266EF97AF9512DBB7463BE874501E10800789CF45033E`. Verify HTTP 200 with body `{"ok":true}` returns SUCCESS and those same bytes. Verify `test-metro-token` is absent from query, headers, body, DecisionTrace, and exception messages, and `test-pass` is absent from DecisionTrace and exception messages. Verify `hasher.qyh-sha256` fails compile with zero HTTP. FakeTransport MUST NOT call the public internet.

- [x] 5.2 If `hasher.sha256` and `{ now: epochSeconds }` cannot produce both D2 signatures, stop and update this change's spec and design. Verify the tree has no `hasher.qyh-sha256`, no Flow-side SHA-256, and no production host, pass id, or token from `QyhMetroExtendParam`.

## 6. Existing gates

- [x] 6.1 Re-run the Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, Artemis POST, PMS, Keytop, Shuyun, CETC, Dahua TCPL, and Qyh GPS protocol tests. Verify they still pass, including the Qyh GPS published `X-Sign` values and the CETC published body.
