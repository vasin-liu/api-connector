## Why

Phase 0 Runtime passed Mock A–I and is on `main`, but those fixtures are synthetic. V2.7 §59 requires proving Session, Challenge/Replay, and Secret boundaries on real vendor protocols before Host or Visual Editor. 百度文心 (OAuth token in query) and 高德交通 (sorted-query HMAC) were chosen as the first pair: they cover the Mock B and Mock C axes without bringing back the old hub.

## What Changes

- Add Canonical Definition YAML for `BAIDU_WENXIN` (client-credentials token → `access_token` query) and `GAODE_TRAFFIC` (HMAC digest over sorted query), plus protocol tables (URLs, param names, canonical string) sourced from remaining inventory docs / `system-thirdpart` audit — **no production secrets in git**.
- Add FakeTransport (or recorded-fixture) tests that assert HTTP call counts, query/header diffs across replay, session reuse/generation, and redaction.
- Extend the built-in Pipeline catalog with a **sorted-query canonicalizer** if existing `canonicalizer.concat` cannot express Gaode digest without putting sort logic in Flow.
- **不做**：Host / 代理 API / 管理台；IDPS header AKSK；文心 SSE 产品化；真实外网打生产；悄悄改 Flow/Session 语义。若 YAML 无法表达协议，停下来改 spec，不在 apply 里发明隐式行为。

## Capabilities

### New Capabilities

- `protocol-validation`: Phase 0 之后的真实协议门禁——文心 OAuth 与高德交通 HMAC 的 Canonical Definition + FakeTransport 验收，缺口必须显式升级 spec 而非绕过 Runtime。

### Modified Capabilities

- `pipeline-graph`: 内置 canonicalizer 覆盖「query 参数按键排序后拼接再 HMAC」；未知节点仍拒绝编译。

## Impact

- 测试与文档：`docs/design/v2.7-protocols/`（YAML + 协议表）；`api-connector-runtime`（及必要时 `config`）测试夹具。
- Runtime：仅当 Gaode 需要新节点时增加 pipeline 实现；不改 `ApiClient` 宿主契约。
- 凭证：SecretRef / 测试用假值；禁止提交真实 `API Key` / `Secret Key` / `clientKey`。
- 后续：打靶通过后再谈 Host；下一协议对不在本 change。
