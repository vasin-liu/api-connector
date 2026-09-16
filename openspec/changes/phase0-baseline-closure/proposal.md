## Why

V2.7 Phase 0 greenfield Runtime（Mock A–I）已在 `feat/v2.7-greenfield-runtime` 落地并归档，但尚未成为可依赖的 `main` 基线：无开放 PR、根 README / `.planning` / `docs/` 入口仍描述已删除的旧中枢产品、Cookie 与 `planId` 的 Open Questions 未写成 as-implemented 笔记。现在收口，才能安全进入文心 OAuth + 高德交通 HMAC 的真实协议打靶。

## What Changes

- 将 `feat/v2.7-greenfield-runtime`（含本 change 的文档提交）以 **单一 PR** 合入 `main`，确认 `.github/workflows/v2-7-runtime.yml` 绿灯。
- 重写根 `README.md`，反映当前六模块引擎（core / runtime / transport / config + parent / dependencies）与 in-process `ApiClient` 宿主；移除已不存在的旧 11 模块与 `api-connector-app` Fat JAR 运行说明。
- 对 README 直接链接的入口（`docs/README.md`、`docs/DEVELOPER.md`）加 historical / pre-V2.7 横幅，不重写迁移长文。
- 将 `.planning/PROJECT.md` / `ROADMAP.md` 标为 superseded（横幅指向 V2.7 / OpenSpec）；对 GSD 注入的 `.planning/codebase/STACK.md` 与 `ARCHITECTURE.md` 同样加 historical 横幅。不手改 `CLAUDE.md`（由 PROJECT/STACK 生成）。
- 仅在 `api-connector-config/README.md` 补 CookieStore 与 `planId` 的 as-implemented 笔记；GraalVM 节已存在，只核对无矛盾表述。不新增 ADR。
- **不做**：Runtime 语义变更、Host/代理 API、管理台、厂商 Definition、文心/高德打靶（留给后续 change）。

## Capabilities

### New Capabilities

- （无。本 change 不引入新的行为能力。）

### Modified Capabilities

- （无。Phase 0 Runtime 合同已在 `openspec/specs/` baseline；本 change 不修改 requirement。）

本 change 在 `.openspec.yaml` 设置 `skip_specs: true`：纯基线合入、文档与平台笔记，无 spec 级行为变化。

## Impact

- Git / GitHub：单一 PR 合入 `main`；CI job `v2-7-runtime`。
- 文档：`README.md`、`docs/README.md`、`docs/DEVELOPER.md`、`.planning/PROJECT.md`、`.planning/ROADMAP.md`、`.planning/codebase/STACK.md`、`.planning/codebase/ARCHITECTURE.md`、`api-connector-config/README.md`。
- 代码：预期无 Runtime 行为 diff；若合入前发现阻断 bug，仅修阻断项并在 tasks 中记录。
- 后续：收口完成后可开启 `protocol-validation-wenxin-gaode`（文心 OAuth + 高德交通 HMAC 打靶）。
