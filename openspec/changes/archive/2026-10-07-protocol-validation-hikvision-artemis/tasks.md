## 1. Engine unlock — HMAC and MD5 encoding

- [x] 1.1 Extend `signer.hmac-sha256` with `config.encoding` `hex` (default) | `base64` (RFC 4648 padding, no wrap); keep `.hex` alias only for hex; add `.base64` when base64; unknown encoding fails validate; verify unit tests: omitted encoding matches existing hex vector, D4 r1 Base64 `8vM5Z2n43HSpDRpHRbKJNQVgHbgWP9E+1ocZIzjhE0o=` for the published StringToSign, and unknown encoding fails compile
- [x] 1.2 Extend `hasher.md5` with the same `encoding` flag (default hex so DaHua stays green); verify unit tests: hex unchanged, `base64` is Base64 of raw MD5 not hex, unknown encoding fails compile

## 2. Protocol table and YAML

- [x] 2.1 Write `docs/design/v2.7-protocols/hikvision-artemis.md` with GET path `/artemis/api/demo`, defaults Accept / Content-Type, frozen StringToSign, query sort `size=20&start=0`, headers `X-Ca-*`, D4 vectors, `{ now: epochMillis }` + `{ generate: nonce }`, two secrets `test-ak`/`test-sk`, stub `https://hikvision.example`; cite SignUtil; no production secrets
- [x] 2.2 Add `docs/design/v2.7-protocols/hikvision-artemis.yaml` per D3/D5/D6 (sorted-query then concat+HMAC base64, RETRY_FLOW on 503), wire `ProtocolDefinitions.hikvisionArtemis()`, verify compile + stable `planId`

## 3. FakeTransport protocol gate

- [x] 3.1 Add `TrafficHikvisionArtemisProtocolTest`: 503 → RETRY_FLOW; r1 `X-Ca-Signature` equals table Base64 and `X-Ca-Key=test-ak`; r2 differs in timestamp, nonce, and signature; SK absent from wire and DecisionTrace; HMAC input is full StringToSign (digest ≠ HMAC of query alone); hex digest MUST NOT be sent as `X-Ca-Signature`
- [x] 3.2 Verify Wenxin, Gaode, IDPS, Huawei, and DaHua protocol tests still pass

## 4. Gap discipline

- [x] 4.1 If StringToSign cannot be expressed without Script/Groovy/Catalog, or if encoding requires a vendor `canonicalizer.artemis-*` / SDK JAR, stop and update this change's specs/design rather than adding AuthProvider or implicit Flow HMAC, and verify no `*ConnectorCatalog` types and no `artemis-http-client` dependency were introduced
