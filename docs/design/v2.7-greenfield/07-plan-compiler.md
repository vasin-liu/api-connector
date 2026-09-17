# PlanCompiler 编译产物与拒绝表

对应源设计 §2.2、§27–28、§33–34、§45。Runtime **只吃** `ExecutionPlan`，不再读 YAML。

---

## P. 编译管线阶段

```text
YAML
  → Parse            Canonical 对象图（失败 = 语法/schema）
  → Normalize        填默认、展开引用、固定顺序
  → Validate         静态语义（失败不得出 Plan）
  → Compile          不可变 ExecutionPlan
  → PlanCache        key = definitionId + revision
```

规则：

- `planId = sha256(utf8(canonicalNormalizedJson))` 的 hex 小写。Normalize 后再哈希，键顺序稳定。
- revision 不变且 Normalize 结果不变 → 缓存命中，**禁止**因时钟/随机数导致 planId 变化。
- Validate 错误一次返回 **全部** violation（不要 fail-fast 只报第一条），便于编辑器。
- Compile 阶段不应再发现「用户配置错误」；若发生，算编译器 bug。例外：0a 对 `AUTHENTICATE` 可标 `capability=0b`，Plan 仍产出，execute 再拒——见下。

---

## Q. Normalize 必须做的事

| 输入 | Normalize 结果 |
|---|---|
| 缺 `flowId` 的 execute | 不在 Plan 里；ApiClient 默认 `"business"` |
| 未写 `replay.maxAttempts` | `1` |
| 未写 `allowAutomaticReplay` | `false` |
| 未写 `replayability` | `UNKNOWN` |
| header 名 | 存原始大小写，另存 canonical 小写键 |
| transition 无 `id` | 赋 `t0..tN` 按列表序 |
| step 无 `id` | 非法，Validate 失败（不自动生成，避免 Trace 不稳定） |
| `credentials.*.apiId` 缺省 | `definition.id` |
| 空 `authentication.steps` | 删除 authentication flow，视为无 AUTH |
| `{global.x}` 与等价全称 | 统一成 `Scope.GLOBAL / name=x` |
| pipeline 线性短写 `- codec: json` | 展开成单节点图 + 隐式 in/out |

Normalize **禁止**：解析 Secret 材料、打网络、读时钟。

**GLOBAL `baseUrl` 内联与否：** Compile **不内联** GLOBAL，只解析为 `VarRef(GLOBAL, baseUrl)`。为了 Replay 与 Trace 可读，冻结为保留 VarRef。GLOBAL 已含在 Normalize JSON 里，内联与引用对 planId 应等价；实现选 VarRef。

---

## R. `ExecutionPlan` 形状

```java
public record ExecutionPlan(
        String planId,
        String definitionId,
        String definitionRevision,
        String authProfile,
        String credentialRef,              // SessionKey 用；多 credential 时见 R3
        Limits limits,
        SessionPolicy sessionPolicy,
        ExecutionPolicy executionPolicy,
        Map<String, CompiledRequest> requests,
        Map<String, CompiledPipeline> pipelines,
        CompiledFlow business,
        Optional<CompiledFlow> authentication,
        Set<PlanCapability> capabilities
) {}

public enum PlanCapability {
    LINEAR_FLOW,
    AUTH_FLOW,
    SESSION,
    REPLAY,
    PIPELINE_GRAPH,
    SCRIPT
}
```

Mock A 的 `capabilities = {LINEAR_FLOW}`。  
Mock C 的 `capabilities = {LINEAR_FLOW, AUTH_FLOW, SESSION, REPLAY, PIPELINE_GRAPH}`。

0a runtime 若 plan 含未实现 capability → `PLAN_CAPABILITY_UNSUPPORTED`，不要半执行。

### R1. CompiledFlow / Step / Transition

```java
public record CompiledFlow(
        String flowId,
        FlowRole role,                     // BUSINESS | AUTHENTICATION
        List<CompiledStep> steps,
        int transitionLimit
) {}

public enum StepKind { REQUEST, ASSIGN, EXTRACT, PIPELINE, AUTHENTICATE_OP }

public record CompiledStep(
        String stepId,
        StepKind kind,
        Optional<String> requestId,
        Optional<String> pipelineId,
        List<CompiledAssign> assigns,
        Optional<CompiledExtract> extract,
        List<CompiledTransition> transitions,
        EnumSet<StepOutcomeType> extraCommitOn,
        Optional<SessionCommit> sessionCommit
) {}

public record CompiledTransition(
        String transitionId,
        Condition condition,
        TransitionAction action,
        Optional<TransitionAction> thenAction,
        Optional<StepOutcomeType> outcomeOverride,
        Optional<SessionFailureReason> sessionFailure,
        Optional<String> retryFromStepId,
        int maxRequestAttempts
) {}
```

`Condition` 在 Compile 后是 Java sealed AST，**禁止** Plan 里残留 YAML Map。Compiler 保证列表序 = 优先级，禁止按 action 类型重排。

### R2. CompiledRequest / Template

```java
public record CompiledRequest(
        String requestId,
        String method,
        UrlTemplate url,
        List<HeaderTemplate> headers,
        List<QueryTemplate> query,
        Optional<BodyBinding> body,
        ReplayPolicy replay,
        EnumSet<SecretSink> declaredSinks
) {}

public record OriginalRequestTemplate(
        String requestId,
        String method,
        UrlTemplate uri,
        List<HeaderTemplate> headers,
        BodyBinding body,
        RequestMetadata metadata
) {}
```

Runtime Replay 持有 `OriginalRequestTemplate`（模板），每次用当前 Variable + Session + Pipeline 渲染。Plan 里不要存「示例渲染结果」。

### R3. SessionKey 的 `credentialRef`

单 credential：`credentialRef = "apiKey"`（Mock A/C）。  
Mock B 的 `account`：`credentialRef = "account"`。  
多个 credentials：Definition 必须显式 `session.credentialRef`，否则 `VAL_SESSION_CREDENTIAL_AMBIGUOUS`（下表 U 用此码）。

---

## S. Mock A 编译产物（期望快照）

```text
ExecutionPlan
  definitionId: mock-a
  revision: "1"
  authProfile: api-key-query
  credentialRef: apiKey
  capabilities: {LINEAR_FLOW}
  authentication: empty
  limits.maxAuthAttempts: 0
  requests.getStatus:
    method: GET
    url: VarRef(GLOBAL, baseUrl) + LIT "/v1/status"
    query: [{name:key, secretRef:apiKey, sink:Authorization}]
    replay: {SAFE, allowAutomaticReplay:false, maxAttempts:1}
  pipelines.identity: passthrough bytes→bytes
  business.steps:
    [0] stepId=call kind=REQUEST requestId=getStatus pipelineId=identity
        transitions:
          t0  StatusEq(200) → SUCCESS
          t1  StatusRange(500,599) → FAIL outcome=RETRYABLE_FAILURE
```

Mock A 无 AUTH：`maxAuthAttempts=0` + `authentication=empty`。出现 `AUTHENTICATE` action → Validate 失败。

---

## T. Mock C 编译产物（期望快照）

```text
ExecutionPlan
  definitionId: mock-c
  authProfile: hmac-challenge
  credentialRef: apiKey
  capabilities: {LINEAR_FLOW, AUTH_FLOW, SESSION, REPLAY, PIPELINE_GRAPH}

  requests.getData:
    headers:
      Content-Type: literal application/json
      X-Timestamp: VarRef(EXECUTION, timestamp)
      Authorization: template "HMAC-SHA256 {flow.authSignature}"
                   when VarRef(FLOW, signed)
                   sink Authorization
    body: pipeline jsonBody port json.out
    replay: CONDITIONALLY_SAFE, allowAutomaticReplay=true, maxAttempts=2

  pipelines.jsonBody:
    nodes: [json: codec.json  object→bytes]
    edges: []
    cycle: false

  pipelines.challengeHmac:
    nodes:
      canonical: concat(secret apiKey, FLOW.nonce, EXECUTION.timestamp) → bytes
      hmac: signer.hmac-sha256  in=bytes  key=secret  out=string
    edges:
      canonical.out → hmac.in
      secretRef apiKey → hmac.key  sink=HMAC
    cycle: false
    typeCheck: OK

  business.steps:
    stamp   ASSIGN  execution.timestamp = NowEpochMillis
    getData REQUEST
      t0 all(StatusEq(403), HeaderExists("x-challenge"))
         → AUTHENTICATE then REPLAY_REQUEST
      t1 all(StatusEq(403), JsonPathEq("$.error","PERMISSION_DENIED"))
         → FAIL outcome=FAILURE
      t2 StatusEq(200) → SUCCESS
      t3 StatusEq(503) → RETRY_REQUEST

  authentication.steps:
    readNonce  EXTRACT header X-Challenge → FLOW.nonce
               extraCommitOn={CHALLENGE}
    newTimestamp ASSIGN execution.timestamp = NowEpochMillis
    sign  PIPELINE challengeHmac  output FLOW.authSignature ← hmac.hex
    markSigned ASSIGN FLOW.signed=true
               sessionCommit {VALID, generationIncrement}
```

Compile 必须解析出：

- `getData` 是业务 `OriginalRequestTemplate` 的唯一 Replay 目标（该 AUTHENTICATE 的 then）
- `challengeHmac` 的 secret 边 `sink=HMAC`，Authorization header 的 sink 是 `Authorization` 且 `apiId=mock-c`
- `FLOW.nonce` 的 def 在 `readNonce`，use 在 `challengeHmac`；两步都在 authentication flow → 0c 浅分析通过
- `execution.timestamp` def 在 `stamp` 与 `newTimestamp`（两次赋值合法）；业务第一次请求前必须经过 `stamp`

J2.C2 在 Plan 上的路径：

```text
plan.business.step["getData"].transitions[0]
  condition = All[ StatusEq(403), HeaderExists("x-challenge") ]
  action = AUTHENTICATE
  thenAction = REPLAY_REQUEST
```

---

## U. Validate 拒绝表（用户错误）

代码：`VALIDATION_FAILED`，每条带 `path`（JSON Pointer）+ `code`。一次返回全部 violation。

### U1. Schema / 引用

| code | 条件 |
|---|---|
| `VAL_SCHEMA_VERSION` | `schema.version` ≠ 1 |
| `VAL_ID_EMPTY` | `definition.id` 空或非法字符（建议 `[a-z0-9-]{1,64}`） |
| `VAL_REVISION_EMPTY` | revision 空 |
| `VAL_STEP_ID_MISSING` | step 无 id |
| `VAL_STEP_ID_DUP` | 同 flow 内 id 重复 |
| `VAL_REQUEST_UNKNOWN` | `request:` 指向不存在的 requests.* |
| `VAL_PIPELINE_UNKNOWN` | step/body 引用不存在的 pipeline |
| `VAL_FLOW_BUSINESS_MISSING` | 无 `flows.business` 或 steps 空 |
| `VAL_CREDENTIAL_UNKNOWN` | secretRef 无对应 credentials |
| `VAL_VAR_UNDECLARED` | 引用未声明变量 |
| `VAL_AUTH_FLOW_REQUIRED` | 存在 `AUTHENTICATE` 但无 authentication steps |
| `VAL_SESSION_CREDENTIAL_AMBIGUOUS` | 多个 credentials 且未声明 `session.credentialRef` |

### U2. Scope / 类型 / Secret

| code | 条件 |
|---|---|
| `VAL_GLOBAL_WRITE` | assign/extract `to` 指向 GLOBAL |
| `VAL_SECRET_LITERAL` | secret 变量或 credential 出现字面 value |
| `VAL_SECRET_IN_CONDITION` | condition equals 比较 Secret |
| `VAL_SINK_MISSING` | secret 进入 header/query/body 未声明 sink |
| `VAL_SINK_API_MISMATCH` | Authorization sink 的 apiId ≠ credential.apiId（静态能确定时） |
| `VAL_INPUT_SCOPE` | Execute input 映射到 SESSION/GLOBAL |

### U3. Transition / Action

| code | 条件 |
|---|---|
| `VAL_WHEN_MISSING` | transition 无 when |
| `VAL_EMPTY_ALL` / `VAL_EMPTY_ANY` | 空组合 |
| `VAL_NOT_ARITY` | `not` 不是恰好 1 个子节点 |
| `VAL_CONDITION_DEPTH` | 嵌套 > 8 |
| `VAL_JSONPATH_FORBIDDEN` | 含 `?(` `..` 函数调用 |
| `VAL_THEN_NOT_ALLOWED` | `then` 出现在非 AUTHENTICATE/REFRESH |
| `VAL_THEN_MISSING` | AUTHENTICATE 无 `then` |
| `VAL_AUTH_ON_EMPTY` | AUTHENTICATE 但 maxAuthAttempts=0 |
| `VAL_REPLAY_ON_NON_REQUEST` | REPLAY_REQUEST 的当前 step 不是 REQUEST |
| `VAL_RETRY_FLOW_THEN` | RETRY_FLOW 带 then |
| `VAL_STATUS_RANGE` | from > to |
| `VAL_STREAM_CONDITION` | 声明 StreamBody 的 request 上配置了 response condition |

### U4. Pipeline

| code | 条件 |
|---|---|
| `VAL_PIPE_CYCLE` | 存在环 |
| `VAL_PIPE_TYPE` | 边两端类型不兼容 |
| `VAL_PIPE_PORT_REQUIRED` | required 输入未连接 |
| `VAL_PIPE_UNKNOWN_NODE` | type 不在内置目录 |
| `VAL_PIPE_CARDINALITY` | 单入端口多条入边 |
| `VAL_CODEC_AS_SIGNER` | codec 节点当 signer 用（职责吞并） |

线性短写只允许类型可接的链；否则 `VAL_PIPE_TYPE`。跨 pipeline 的边非法。

### U5. 浅数据流（Phase 0 子集，不是完整 §33）

| code | 条件 |
|---|---|
| `VAL_VAR_DEF_MISSING` | 某 REQUEST/PIPELINE 使用的非可选 var，在同 flow 所有路径上从未 ASSIGN/EXTRACT |
| `VAL_NONCE_SCOPE` | Challenge nonce 声明为 GLOBAL 或 SESSION（Mock C 必须是 FLOW） |

**不做（推迟）：** 跨分支「可能未初始化」的完整 definite assignment、循环 flow、脚本内赋值分析。

---

## V. Compile / Execute 拒绝（非用户 YAML 错）

| code | 阶段 | 何时 |
|---|---|---|
| `PLAN_CAPABILITY_UNSUPPORTED` | execute 0a | Plan 含 AUTH_FLOW/REPLAY 等 |
| `PLAN_CACHE_MISS_COMPILE_BUG` | runtime | cache 键命中但 planId 不同 |
| `SECRET_UNRESOLVABLE` | execute | provider 没有 ref |
| `SECRET_SINK_DENIED` | execute | 0d 目的地动态 DENY |
| `DEFINITION_NOT_PUBLISHED` | execute | 生命周期；0a 内存可默认 PUBLISHED |

---

## W. Pipeline 类型检查用例（Mock C + 故意破坏）

内置端口（0c 最小目录，完整目录仍待 0c 开工前补附件）：

| node type | in | out |
|---|---|---|
| `passthrough` | bytes? | bytes |
| `codec.json` | object | bytes |
| `canonicalizer.concat` | 各 part：string/number/secret → 各自 in | bytes |
| `signer.hmac-sha256` | bytes + secret key | string（hex） |

| # | 改动 | 期望 |
|---|---|---|
| P0 | Mock C 原文 | Compile OK |
| P1 | `hmac.in` ← `json.out` 且 json 在另一图 | 跨 pipeline 边非法 |
| P2 | `hmac.in` 接 `hmac.hex`（string→bytes 无 codec） | `VAL_PIPE_TYPE` |
| P3 | 去掉 `canonical → hmac.in` | `VAL_PIPE_PORT_REQUIRED` |
| P4 | 边 `hmac → canonical` 形成环 | `VAL_PIPE_CYCLE` |
| P5 | key 端口接 `FLOW.nonce`（string） | `VAL_PIPE_TYPE` |
| P6 | hmac 输出接到 HTTP body（getData 要 bytes） | `VAL_PIPE_TYPE` |
| P7 | 线性短写 `codec.json` 再 `signer.hmac` 无边 | 类型不可接则 `VAL_PIPE_TYPE` |

P4 是源设计 49.8 的代表用例：必须在 Validate/Compile 失败，不得运行时死循环。

---

## X. 0a vs 完整 Plan 的测试策略

**0a 只 Compile Mock A + 无 AUTH 的负例：**

| 测试 | 期望 |
|---|---|
| Mock A YAML | plan.capabilities={LINEAR_FLOW}，authentication.empty |
| 在 Mock A 加一条 AUTHENTICATE | `VAL_AUTH_FLOW_REQUIRED` |
| GLOBAL assign | `VAL_GLOBAL_WRITE` |
| jsonpath `$.a[?(@.b)]` | `VAL_JSONPATH_FORBIDDEN` |
| step 无 id | `VAL_STEP_ID_MISSING` |
| 同 YAML 编译两次 | planId 相同 |

**Mock C fixture：** 0a 可 Parse+Validate+Compile 出 Plan，但 `capabilities` 含 AUTH/REPLAY → 0a `ApiClient.execute` 返回 `PLAN_CAPABILITY_UNSUPPORTED`。

**planId 金样：** 对 Normalize JSON 做哈希；测试夹具用固定 YAML，断言 hex 全匹配。不要用时间戳进 Normalize。

---

## Z. 第一批测试顺序（仍按 TDD 开工时使用）

1. `ConditionEvaluator` ← [05](05-condition-transition-tables.md) 表 H/J  
2. `DefinitionValidator` ← 表 U 每条一个 YAML 碎片  
3. `PlanCompiler.compile(Mock A)` ← 节 S + planId 稳定  
4. `PlanCompiler.compile(Mock C)` ← 节 T + `transitions[0]` 顺序 + 节 W P0–P7  
5. 0a `ApiClient` + FakeTransport ← 仅 Mock A 的 A1/A2/A4  
6. 0a execute(Mock C) ← `PLAN_CAPABILITY_UNSUPPORTED`
