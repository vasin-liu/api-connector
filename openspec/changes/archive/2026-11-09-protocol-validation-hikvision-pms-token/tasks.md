# Tasks

## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/hikvision-pms.md` with frozen endpoint POST `/getParkingInfo`, body literal `{"appkey":"test-ak"}`, `Content-Type: application/json;charset=UTF-8`, query `token` = uppercase hex MD5(uri + body + secret), truth source `HikvisionPmsClient.withToken` + `BaseClient.postHttpRequest`, stub host `https://hikvision-pms.example`, fixtures `test-ak`/`test-sk`, D4 vectors, second-endpoint generality row, and out-of-scope notes (production appkey composition gap, `/ImageServer*` binary download, live portal verification, no production secrets, Accurio retired). Verify the table's token is `EDAA4B3136EA75DF9A435C95B920DD9E` and the lowercase hex `edaa4b3136ea75df9a435c95b920dd9e` is marked MUST-NOT.
- [x] 1.2 Leave `hikvision-artemis.md`, `hikvision-artemis.yaml`, `hikvision-artemis-post.md`, and `hikvision-artemis-post.yaml` unchanged. Verify the diff does not touch those four files.

## 2. hex-upper catalog encoding

- [x] 2.1 Add `HEX_UPPER` to `DigestEncoding` (`parse` accepts `hex-upper`; `format` emits uppercase hex) and keep `HEX`/`BASE64` byte-identical. Verify `Md5HasherTest`/`HmacSha256Test` gain a hex-upper case asserting uppercase output, non-equality to lowercase hex, and that `hasher.hex`/`hmac.hex` bindings stay lowercase, and that default `hex` YAML output is unchanged.
- [x] 2.2 Verify unknown encoding values still fail compile (extend the validator test with a rejected value other than `hex`/`hex-upper`/`base64`) and that the full suite stays green.

## 3. Canonical YAML + definition

- [x] 3.1 Add `docs/design/v2.7-protocols/hikvision-pms.yaml` with definition id `hikvision-pms`: GLOBAL `bodyJson` literal is the only body source; body pipeline = one `canonicalizer.concat` of that variable; token pipeline = concat `[uriPath, bodyJson, secretRef secretKey sink HASH]` → `hasher.md5` `encoding: hex-upper` → `execution.token`; query `token`; replayability `SAFE`; 503 → `RETRY_REQUEST`; 200 → `SUCCESS`; no `authentication` flow. Verify the YAML contains no `codec.json`, no vendor `canonicalizer.pms-*`, and no `signer.*` node.
- [x] 3.2 Add `ProtocolDefinitions.hikvisionPms()` and verify compile succeeds, `planId` is stable across two compiles, and differs from `ProtocolDefinitions.hikvisionArtemis()` and `hikvisionArtemisPost()`.

## 4. FakeTransport protocol gate

- [x] 4.1 Add `TrafficHikvisionPmsProtocolTest`: compile test (pipelines/node types/encodings, planId stability); r1 query `token` equals `EDAA4B3136EA75DF9A435C95B920DD9E`, body equals the literal, `token` = uppercase hex MD5(path + wire body + secret) recomputed in-test, not the lowercase hex, not Base64; 503 → `RETRY_REQUEST` sends a second request whose body and `token` equal r1 while being a new instance; SK absent from query/headers/body and DecisionTrace; unknown vendor node (`canonicalizer.pms-token`) fails compile with zero HTTP. Verify the test passes with FakeTransport and no public HTTP.
- [x] 4.2 Verify Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, and Artemis POST protocol tests still pass, including both published Artemis signatures and the published POST `Content-MD5`.

## 5. Gap discipline

- [x] 5.1 If the wire body cannot remain the `hasher.md5` input without a new node, stop and update this change's specs and design instead of hashing or uppercasing in Flow or signing `codec.json` output. Verify the resulting tree has no `*ConnectorCatalog` types, no vendor SDK dependency, and that production appkey composition remains recorded as out of scope.
