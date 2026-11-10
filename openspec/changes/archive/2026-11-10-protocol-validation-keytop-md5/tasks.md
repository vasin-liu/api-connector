# Tasks

## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/keytop.md` for POST `https://keytop.example/api/wec/GetFreeSpaceNum`, truth source `KeytopSignUtil.paramsSign` plus `KeytopClient` / `BaseClient.postHttpRequest`. Freeze the javadoc signed material and `key` `B19F7863ADCC8B5442A757AC7B90F6AC`, mark lowercase `b19f7863adcc8b5442a757ac7b90f6ac` as MUST-NOT, and state that the body is the javadoc sample plus `key`, not the `getFreeSpaceNum` field set. Record headers `Content-Type: application/json;charset=UTF-8`, `version: 1.0.0`, `accept-language: zh-CN`, sample secret `EED96C219E83450A`, and the out-of-scope notes from design Non-Goals. Verify the table's `key` matches that digest.

## 2. Canonical YAML

- [x] 2.1 Add `docs/design/v2.7-protocols/keytop.yaml`, definition id `keytop`: `sorted-query` `encoding: none` excluding `appId` and `key`, bind `execution.canonical`; concat separator `&` of that variable plus `secretRef` sink HASH into `hasher.md5` `encoding: hex-upper` bound to `execution.key`; wire body is concat of a frozen JSON prefix, `execution.key`, and a suffix. Replayability `SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`, 503 → `RETRY_REQUEST`, 200 → `SUCCESS`, no `authentication` flow, `maxAuthAttempts` at least 1. Verify the YAML contains no `codec.json` and no vendor `canonicalizer.keytop-*`.
- [x] 2.2 Add `ProtocolDefinitions.keytop()` and verify the definition compiles, `planId` is stable across two compiles, and differs from `ProtocolDefinitions.hikvisionPms()`.

## 3. FakeTransport gate

- [x] 3.1 Add `TrafficKeytopProtocolTest`. Verify the first POST path is `/api/wec/GetFreeSpaceNum`, the body contains the published fields including `appId`, JSON field `key` equals `B19F7863ADCC8B5442A757AC7B90F6AC` and is not the lowercase hex, and a 503 then 200 yields a second request whose body bytes equal the first while being a new instance. Verify the secret is absent from query, headers, body, and DecisionTrace, and that `canonicalizer.keytop-sign` fails compile with zero HTTP. FakeTransport MUST NOT call the public internet.
- [x] 3.2 If the catalog cannot produce that body, stop and update this change's spec and design. Verify the tree has no new pipeline node, no vendor SDK, and no Flow-side MD5.

## 4. Existing gates

- [x] 4.1 Re-run the Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, Artemis POST, and PMS protocol tests. Verify they still pass, including the published PMS token and both Artemis signatures.
