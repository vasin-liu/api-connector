## Why

Wenxin OAuth and Gaode sorted-query HMAC are on `main`, but IDPS header AK/SK — the next real-protocol gate — cannot be expressed yet: canonical query is RFC3986 encode-then-sort, the HMAC envelope is seven `\n` lines, timestamp is ISO-8601 with offset, and snowflake is client-generated. V2.7 still forbids Host until that shape is proven on Canonical YAML + FakeTransport.

## What Changes

- Add protocol table + Canonical YAML for IDPS `aksk_hmac_sha256` (headers `X-Auth-Key` / `Algorithm` / `Signature` / `Timestamp` / `SnowflakeID`) sourced from `system-thirdpart` `IdpsUtils` + `IdpsClient` — **no production secrets in git**.
- Extend `canonicalizer.sorted-query` with `encoding: rfc3986` (default `none` so Gaode stays green).
- Extend Flow assign with `{ now: isoOffset }` and `{ generate: nonce }` (injectable clock / nonce source). Compose envelope HMAC as **two pipelines** (RFC3986 query → concat `\n` + HMAC); do **not** add a vendor `canonicalizer.aksk-*` node.
- FakeTransport tests: 503 → `RETRY_FLOW` rebuilds timestamp, nonce, and digest; wire query stays **unencoded** (IdpsClient); canonical string contains percent-encoding; SK never appears in traces.
- **不做**：Host / 管理台 / Java Catalog / AuthProvider；`IdpsUtils2` / 网关入站 SHA256 / `getSign()` MD5；Hutool snowflake 位布局；真实外网；POST body 进签名。若 YAML 仍无法表达表规则，停下来改 spec。

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `protocol-validation`: IDPS header AK/SK Canonical Definition + FakeTransport 验收（RFC3986 query、七行 envelope、ISO 时间、客户端 nonce、RETRY_FLOW 重建）。
- `pipeline-graph`: sorted-query 支持 `encoding: none | rfc3986`；Flow 仍禁止排序；HMAC 必须签 envelope 而非 query 串。
- `flow-runtime`: assign 支持 `{ now: isoOffset }` 与 `{ generate: nonce }`（可注入），RETRY_FLOW 必须换新时间戳与 nonce。

## Impact

- 测试与文档：`docs/design/v2.7-protocols/idps-aksk.md` + `idps-aksk.yaml`；`api-connector-runtime` 节点单测与 FakeTransport 协议测。
- Runtime：`SortedQueryCanonicalizer` encoding；`FlowRuntime.resolveValue` 的 `now` / `generate`；可注入 `NonceSource`。不改 `ApiClient` 宿主契约。
- 凭证：SecretRef + 夹具 `test-ak` / `test-sk`。禁止提交 `BrainApiTool` 或生产 AK/SK。
- 高德回归：`encoding` 缺省 `none`，现有 Gaode 向量不得变红。
