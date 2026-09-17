## Why

当前 `api-connector` 是一次 HTTP 代理中枢（Catalog + AuthProvider + Mapping），无法表达 V2.7 要求的 Flow 控制流、Session 协调、Replay 重建和 Secret 边界。V2.7 Runtime Contract 已定版，需要按该合同绿场实现 Phase 0 引擎，而不是在现有编排链上打补丁。

## What Changes

- **BREAKING**：产品定位从「第三方接入中枢」改为「纯 Outbound HTTP Flow & Protocol Execution Engine」。不保留 `system-thirdpart` URL/错误语义、`code3rd` 代理 API、Java Catalog、MappingEngine、Groovy 脚本、Vue 管理台作为 Phase 0 交付物。
- **BREAKING**：认证从请求前 `AuthProvider` 注入，改为 Authentication Flow（可同接口 Challenge、跨接口多轮、Session Generation 单刷新者）。
- 新增 Canonical Definition → Validate → Compile → `ExecutionPlan` → `ExecutionSnapshot` 执行链；Runtime 只执行 Plan。
- 新增结构化 Condition AST、第一匹配 Transition、`StepOutcome` 与 HTTP status 解耦。
- 新增 Pipeline Graph（数据流；默认禁止 Cycle；端口类型编译期检查）。
- 新增 Replay：从 `OriginalRequestTemplate` + 当前变量/Session/Pipeline/Signer 重建请求，禁止 clone 已发字节。
- 新增 Secret 类型体系与 Sink + Destination 校验；脚本沙箱采用 GraalVM Polyglot（排除 Groovy 默认沙箱）。
- 新增 DecisionTrace / Attempt ID；验收以 Mock A–I 为门禁。
- Phase 0 宿主为 in-process `ApiClient`，不实现入站网关、租户、对内业务映射、生产多实例 SessionStore。

## Capabilities

### New Capabilities

- `definition-plan`: Canonical Definition 解析、校验、编译、PlanCache、ExecutionSnapshot、Revision 与 Session 兼容判定
- `flow-runtime`: Flow 控制流、Condition AST、Transition、StepOutcome、变量 Scope 与 StateMutation
- `session-auth`: Authentication Flow、SessionKey/Generation/Coordinator、CookieStore、失败风暴与 Cooldown
- `pipeline-graph`: Pipeline 节点/边/端口类型、Codec/Transformer/DataProcessor/Canonicalizer/Signer、禁 Cycle
- `transport-replay`: HttpTransport、ResponseBody、Replay/Retry/Refresh 分离、UNKNOWN_OUTCOME、StreamBody 不进 Challenge
- `secret-script`: DataValue/SecretValue、SecretProvider/SinkPolicy、GraalVM Capability 与资源限制
- `execution-observability`: DecisionTrace、attempt 层次、Secret 脱敏

### Modified Capabilities

- （无。仓库尚无 `openspec/specs/` 基线。）

## Impact

- 代码：Phase 0 按 V2.7 §54 新建 `core` / `runtime` / `transport` / `config` 模块；现有 11 模块不当改造对象，不在本 change 内迁移旧实现。
- API：对外为 `ApiClient.execute` / `cancel`；不提供 `/integrations/{code3rd}/...` 或 legacy URL。
- 依赖：Java 21、`java.net.http.HttpClient`、GraalVM Polyglot；Condition JSONPath 需预研（受限库或自研子集）。明确不引入 Groovy、Jayway 全功能 JSONPath 作为 Condition 引擎。
- 系统：单进程内存 SessionStore 与内存 Definition Registry（装载默认 PUBLISHED）；无管理台、无 JDBC 发布、无厂商 Catalog。
- 文档权威：`docs/design/api-connector-design-v2_7.md` 为 Runtime Contract；`docs/design/v2.7-greenfield/` 为可测 YAML/求值表/编译拒绝表（含 Mock A–C 与 D/E/H/I），测试期望以该规格包为准。
