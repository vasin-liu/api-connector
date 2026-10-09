## Why

Hikvision Artemis AK/SK HMAC-SHA256 Base64 (`X-Ca-*`) is the next Wave-1 L3 gap after DaHua. The public `SignUtil` formula is reproducible without the SDK, but today's catalog cannot emit it: `signer.hmac-sha256` and `hasher.md5` are **hex-only**, and Accurio-style hex HMAC is not Artemis-equivalent.

## What Changes

- Unlock pipeline: `signer.hmac-sha256` and `hasher.md5` accept `config.encoding` `hex` (default) or `base64` (RFC 4648, no wrap). Omitted encoding keeps Gaode / IDPS / DaHua hex. Unknown encoding fails compile.
- Protocol gate: table + Canonical YAML + FakeTransport for a **frozen GET** Artemis request (SDK 1.1.x `doGetArtemis` defaults). Compose StringToSign with existing `canonicalizer.concat` + `canonicalizer.sorted-query` — **do not** add `canonicalizer.artemis-*` or bind `artemis-http-client`.
- FakeTransport: 503 → `RETRY_FLOW` rebuilds nonce, epoch-millis timestamp, and Base64 signature; SK never on the wire or in traces.
- **不做**：Host / Catalog / AuthProvider / Groovy；真外网；SDK 依赖；动态 `x-ca-*` 前缀扫描；POST form `query∪body` TreeMap；`Content-MD5` 进本 gate（hasher `base64` 只作引擎解锁）；讯飞 WebSocket。

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `pipeline-graph`: HMAC and MD5 nodes emit hex or Base64 from `config.encoding`; default hex; Flow still MUST NOT sign or hash.
- `protocol-validation`: Hikvision Artemis GET AK/SK Canonical Definition + FakeTransport gate (StringToSign + Base64 HMAC-SHA256 + `X-Ca-*`).

## Impact

- Runtime: `HmacSha256` / `Md5Hasher` / `PipelineExecutor` / `PipelineGraphValidator`; FakeTransport protocol tests.
- Docs: `docs/design/v2.7-protocols/hikvision-artemis.md` + `hikvision-artemis.yaml`.
- Existing Gaode / IDPS / Huawei / DaHua definitions must keep compiling and passing.
- Credentials: fixtures `test-ak` / `test-sk` only; no production Artemis secrets in git.
