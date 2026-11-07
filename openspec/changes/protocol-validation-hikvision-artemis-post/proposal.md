## Why

Hikvision Artemis GET proved Base64 HMAC over a StringToSign that omits `Content-MD5`. `hasher.md5` `encoding: base64` already matches the HTTP Content-MD5 form, but only in unit tests. A frozen POST is the next catalog hole: the signature must include Base64(MD5(body bytes)), and those bytes must be the body actually sent.

## What Changes

- Protocol gate: table + Canonical YAML + FakeTransport for a **frozen POST** (`doPostString` / raw JSON bytes). `Content-MD5` is `hasher.md5` with `encoding: base64` over the same UTF-8 body that goes on the wire. StringToSign inserts that value between `Accept` and `Content-Type`. HMAC stays `signer.hmac-sha256` `encoding: base64`. Compose with existing `canonicalizer.concat` + `canonicalizer.sorted-query`. **Do not** add `canonicalizer.artemis-*` or bind `artemis-http-client`.
- New definition id `hikvision-artemis-post`. Leave the published GET definition `hikvision-artemis` and its plan unchanged.
- FakeTransport: 503 → `RETRY_FLOW` rebuilds nonce, epoch-millis timestamp, and Base64 signature. The frozen body and `Content-MD5` stay the same across the two sends. SK never on the wire or in traces.
- **不做**：Host / Catalog / AuthProvider / Groovy；真外网；SDK 依赖；动态 `x-ca-*` 前缀扫描；POST form `query∪body` TreeMap；`Date` 头；把 `codec.json` 的执行期对象当作被签名的 body；讯飞 WebSocket。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: Hikvision Artemis POST Canonical Definition + FakeTransport gate (frozen JSON body, `Content-MD5` Base64 MD5, StringToSign includes that header, Base64 HMAC-SHA256, `X-Ca-*`).

## Impact

- Docs: `docs/design/v2.7-protocols/hikvision-artemis-post.md` + `hikvision-artemis-post.yaml`. GET table stays the scope boundary it already is.
- Runtime: expected catalog change is none (`hasher.md5` base64 and concat body pipelines already exist). If the wire body cannot be the hasher input without a new node, stop and update this change instead of hashing in Flow.
- Tests: new FakeTransport protocol test; existing Wenxin, Gaode, IDPS, Huawei, DaHua, and Artemis GET gates stay green.
- Credentials: fixtures `test-ak` / `test-sk` only; no production Artemis secrets in git.
