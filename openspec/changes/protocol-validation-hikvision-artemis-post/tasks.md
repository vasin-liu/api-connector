## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/hikvision-artemis-post.md` with POST path `/artemis/api/demo`, `Accept: */*`, `Content-Type: application/json;charset=UTF-8`, frozen body `{"pageNo":1,"pageSize":20}`, query sort `size=20&start=0`, headers `Content-MD5` and `X-Ca-*`, D3 StringToSign, D4 vectors, `{ now: epochMillis }` + `{ generate: nonce }`, two secrets `test-ak`/`test-sk`, stub `https://hikvision.example`; cite SignUtil raw-body POST; state form `query∪body`, `Date`, and live charset spacing as out of scope; no production secrets. Verify the table's r1 `Content-MD5` is `jiion4rNY0nKP5xj4NxZ2w==` and r1 `X-Ca-Signature` is `3Cct0Tx+S72OvWBlot3ziFhqIoCmaqQM75kvVHUEI0U=`.
- [x] 1.2 Leave `hikvision-artemis.md` and `hikvision-artemis.yaml` unchanged. Verify the diff does not touch those two files.

## 2. Canonical YAML

- [x] 2.1 Add `docs/design/v2.7-protocols/hikvision-artemis-post.yaml` with definition id `hikvision-artemis-post`: GLOBAL `bodyJson` is the only body source; body pipeline is one `canonicalizer.concat` of that variable; a separate pipeline concats the same variable into `hasher.md5` `encoding: base64`; StringToSign uses concat + `signer.hmac-sha256` `encoding: base64` and `canonicalizer.sorted-query` `encoding: none`; 503 → `RETRY_FLOW`. Verify the YAML contains no `codec.json` body and no `canonicalizer.artemis-*` node.
- [x] 2.2 Add `ProtocolDefinitions.hikvisionArtemisPost()` and verify compile succeeds, `planId` is stable across two compiles, and that plan id differs from `ProtocolDefinitions.hikvisionArtemis()`.

## 3. FakeTransport protocol gate

- [x] 3.1 Add `TrafficHikvisionArtemisPostProtocolTest`: 503 → `RETRY_FLOW`; r1 `X-Ca-Signature` and `Content-MD5` equal the table; r2 differs in timestamp, nonce, and signature while body and `Content-MD5` stay equal; wire body equals the published literal and does not contain the timestamp; `Content-MD5` equals Base64(MD5(wire body)) and is not the hex MD5; HMAC input is the full StringToSign (digest ≠ HMAC of query alone and ≠ HMAC of body alone); hex HMAC MUST NOT be `X-Ca-Signature`; SK absent from wire and DecisionTrace. Verify the test passes with FakeTransport and no public HTTP.
- [x] 3.2 Verify Wenxin, Gaode, IDPS, Huawei, DaHua, and Artemis GET protocol tests still pass, including the published GET signature.

## 4. Gap discipline

- [x] 4.1 If the wire body cannot be the `hasher.md5` input without a new node, stop and update this change's specs and design instead of hashing in Flow or signing `codec.json` output. Verify the resulting tree has no `*ConnectorCatalog` types and no `artemis-http-client` dependency.
