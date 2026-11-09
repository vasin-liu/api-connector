# Proposal

## Why

`RETRY_FLOW` 在运行时会进入 authentication flow；没有该 flow 时执行以 `FAILURE` 结束，且不再发第二次 HTTP。`flow-runtime` 只写了重试后 nonce 会变，没有写这条路由。Artemis POST 的设计曾因此写成「不要 authentication flow」，实现时才发现 503 直接失败。PMS 用的是另一条路：`RETRY_REQUEST` 只重渲染当前请求。两条动作需要成为可测试的契约，而不是 YAML 惯例。

## What Changes

- 把现有行为写入 `flow-runtime`：`RETRY_FLOW` 在下一次业务发送前运行已声明的 authentication flow；flow 缺失或没有步骤时终态为 `FAILURE`，且不再发送 HTTP。
- 同时写明 `RETRY_REQUEST`：在回放策略允许时重渲染当前请求，不启动 authentication flow。
- 用一条最小 FakeTransport 反例锁住「无 flow → 一次 HTTP + FAILURE」。正向路径继续由现有 Artemis POST 门禁证明（第二次签名变化）。PMS 门禁继续证明 `RETRY_REQUEST` 在无 authentication flow 时仍会第二次发送。
- **不改** `FlowRuntime` 的路由。若新测试与上述行为不一致，先停下来改本变更的 spec，而不是改运行时去迎合另一套语义。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `flow-runtime`: `RETRY_FLOW` 必须先运行 authentication flow；缺失时 `FAILURE` 且无额外 HTTP。`RETRY_REQUEST` 重渲染当前请求且不启动 authentication flow。

## Impact

- Spec: `openspec/specs/flow-runtime/spec.md` 增加两条 requirement（`RETRY_FLOW` 路由、`RETRY_REQUEST` 不启动 authentication）。既有 nonce / 401 `CONTINUE` requirement 不变。
- Tests: 新增一个只覆盖缺失 authentication flow 的 FakeTransport 测试。回归 Artemis POST 与 PMS 门禁。
- Runtime: 无预期代码改动。
