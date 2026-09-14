# 编码前必须冻结的附件

没有这三份，V2.7 还只是运行时备忘录。建议按 A1 → A2 → A3 写，写完再开 0a。

| 附件 | 作用 | 冻结标准 |
|---|---|---|
| **A1 Canonical Definition JSON Schema** | YAML/UI/API 的唯一输入 | 能表达 Mock A–I；非法配置在 Validate 被拒 |
| **A2 ApiClient / Execution 对外 API** | 宿主怎么驱动引擎 | 同步 invoke、取消、错误、流式预留都有签名 |
| **A3 Error + Trace 字段表** | 可观测与 Secret 边界 | 每个 Decision 有稳定 `decisionId`；Secret 永不入 trace |

Java 签名草图见 [04-java-api-0a.md](04-java-api-0a.md)。YAML 见 [03-canonical-yaml-mock-a-c.md](03-canonical-yaml-mock-a-c.md)。

---

## A1. Canonical Definition：现在缺什么

§47 只证明「有 flows/variables/credentials」，不够编译。按 Mock A–I 反推，schema 至少要有这些块。标了 **缺口** 的是源设计没定语法的。

约定（建议写进 A1 前言，避免三种 YAML 方言）：

- 变量引用：`{scope.name}`，scope 为 `global|session|execution|flow|local|input`
- Secret 只允许 `valueRef`，禁止字面量
- `requests.*` 是 Template；每次发送都经 Pipeline 重建
- `flows.authentication` 为保留名，role 固定为 AUTH
- transition 按列表**第一匹配**；无匹配且 HTTP 2xx → SUCCESS，否则 FAILURE
- jsonpath 仅 Root / Property / Array Index / Existence / Simple Equality
- `schema.version: 1` 表示本草稿的格式版本

### 1. 文档头（已有骨架）

```yaml
schema:
  version: 1          # Canonical 格式版本
definition:
  id: vendor-demo
  revision: 1
```

缺口：`schema.version` 不兼容时是拒绝还是升级转换；`id` 字符集；revision 是整数还是字符串。  
本包冻结：id 建议 `[a-z0-9-]{1,64}`；revision 为字符串；不兼容 schema 直接拒绝（`VAL_SCHEMA_VERSION`）。

### 2. Credentials（部分有）

```yaml
credentials:
  apiKey:
    type: secret
    valueRef: secret/vendor-api-key
    apiId: vendor-demo    # SinkDestination 要用
```

缺口：

- `type` 枚举：`secret` / `username-password` / `private-key` / `certificate` / `multi`
- 多 secret 怎么声明（OAuth client id + secret）
- `credentialRef` 与 SessionKey 的对应名（§16 用字符串，schema 里叫什么）
- `apiId` 默认是否等于 `definition.id`（§14 目的地校验依赖它）

本包冻结：缺省 `apiId = definition.id`；单 credential 时 `credentialRef` 为该 key；多个 credentials 必须显式 `session.credentialRef`。

### 3. Variables（部分有）

缺口：

- 类型枚举是否等于 `DataValue`：`string|number|boolean|bytes|json|object|list|secret|null`
- 未赋值变量是编译失败还是运行时 `NullValue`
- GLOBAL 只读如何写死（禁止 step mutation 指向 GLOBAL）
- secret 变量能否有 `value` 字面量（应禁止，只允许 `valueRef`）

### 4. Requests / Templates（几乎空白，这是最大洞）

§47 写了 `- request: getData`，但没定义 `getData`。至少需要：

```yaml
requests:
  getData:
    method: POST
    url: "{baseUrl}/api/data"
    headers: {}
    query: {}
    body: { pipeline: requestBody }
    replay:
      replayability: SAFE | CONDITIONALLY_SAFE | UNSAFE | UNKNOWN
      allowAutomaticReplay: true
      maxAttempts: 1
      idempotencyKeyStrategy: none
```

缺口：

- URL 模板语法（本包冻结 `{scope.name}`，与 Condition jsonpath 分开）
- body 是字面 JSON、变量引用，还是 pipeline 输出端口
- header/query 的 secret 注入怎么声明（否则 Sink 无法静态检查）
- timeout、followRedirects、HTTP 版本
- `OriginalRequestTemplate` 哪些字段进 snapshot、哪些每次重建（timestamp/nonce 必须每次重建）

### 5. Flows / Steps / Transitions（示例级）

§48 有 `when.status` + `header.exists` + `action: AUTHENTICATE`。缺口：

- step 种类完整枚举：`request` / `extract` / `transform` / `script` / `assign` / `authenticate` / 是否还有 `wait`
- `action` 枚举：`AUTHENTICATE` / `RETRY_REQUEST` / `RETRY_FLOW` / `REPLAY_REQUEST` / `REFRESH_SESSION` / `FAIL` / `GOTO` / `SUCCESS` / `CONTINUE`
- transition 是「第一匹配」还是「全匹配」→ **冻结第一匹配**
- 默认失败策略（无匹配 transition 时）→ 见 [05-condition-transition-tables.md](05-condition-transition-tables.md)
- `maxDepth` / `maxAuthAttempts` / `transitionLimit` 写在 flow 还是 definition
- 跨接口认证如何引用另一个 request（Mock D）

`AUTHENTICATE` 的合法写法只有：

```yaml
action: AUTHENTICATE
then: REPLAY_REQUEST | CONTINUE | FAIL
```

建议 Mock B/C 都用 `then: REPLAY_REQUEST`。

### 6. Condition AST（有概念无 YAML）

```yaml
when:
  all:
    - status: 403
    - header:
        name: X-Challenge
        exists: true
    - jsonpath:
        path: $.code
        equals: TOKEN_EXPIRED
```

缺口：status 是精确值还是区间；header 匹配大小写；jsonpath 只允许 §32 子集；`not`/`any` 嵌套深度限制。  
本包：头名大小写不敏感、值敏感；嵌套深度 ≤ 8；空 `all`/`any` Validate 拒绝。

### 7. Pipeline Graph（有线性示例，无图示例）

线性：

```yaml
pipeline:
  - codec: json
  - processor: gzip
  - signer: hmac-sha256
```

图（Mock C 的 HMAC 需要）缺口：

- node id、type、ports、edges 的 YAML
- 端口类型如何写（`bytes` / `string` / `secret`）
- 哪个端口接到 HTTP body、哪个接到 header
- 禁止 Cycle 的具体检测时机（Validate vs Compile）→ Validate

### 8. Auth profile / Session（概念有，schema 无）

缺口：

- `authProfile` 在 definition 里的字段名（SessionKey 依赖它）
- 哪个 flow 是 authentication flow（命名约定 `authentication:` 还是显式 `role: AUTH`）→ 保留名 `authentication`
- Session TTL、Failure Cooldown 时长写在哪
- CookieStore 的 domain/path 策略是 definition 配置还是 Transport 默认 RFC

### 9. Script / Extension（概念有，schema 无）

缺口：script 语言（js?）、源码位置、capability 白名单声明、resourceLimits 数值、extension class 名与 scope。

### 10. 明确不进 Canonical Definition 的东西

- tenant / caller
- 对内业务字段映射
- 入站鉴权、限流
- 真实 secret 材料

### 人工拍板（本包默认）

| # | 问题 | 建议默认 |
|---|---|---|
| 1 | URL/header 模板是 `{scope.name}` 还是 JsonPath | `{scope.name}`，与 Condition jsonpath 分开 |
| 2 | `AUTHENTICATE` + `then: REPLAY_REQUEST` 是一条 transition 还是两个 action | 一条 transition |
| 3 | `now: epochMillis` 是内置函数还是 Clock capability | 内置 Deterministic Clock，测试可注入 |
| 4 | login body 里 username/password 如何进 json codec 而不变成 String | `ObjectValue` 内嵌 `SecretValue`；codec.json 对 secret 字段走 ALLOW sink |
| 5 | 无 `authentication` steps 的 API，401 怎么办 | 当 FAILURE，禁止隐式 AUTH |
| 6 | `planId` 生成 | `sha256(definitionId + revision + canonicalNormalizedJson)` 的 hex 小写；实际实现见 07，以 Normalize JSON 哈希为准 |

---

## A2. ApiClient：文档架构图画了，签名完全没有

Phase 0 最小宿主 API（名字可改，语义不要漂）：

```java
public interface ApiClient {
    ExecutionHandle execute(ExecuteCommand command);
    void cancel(String executionId);
}
```

完整类型见 [04-java-api-0a.md](04-java-api-0a.md)。

还必须冻结的决策：

| 问题 | 若不冻结的后果 | 本包冻结 |
|---|---|---|
| 宿主传入的 input 能否写 SESSION/GLOBAL | 破坏 scope 模型 | 只能进 EXECUTION |
| 未 PUBLISHED 的 revision 能否 execute | 生命周期形同虚设 | 否 |
| 同步 `executeAndWait` 是否提供 | 测试和宿主会分叉 | Phase 0 可用 `handle.result().toCompletableFuture().join()` |
| 流式：`StreamBody` 从哪条 API 出来 | 和 §36 冲突或漏掉 | 同一 `ExecutionResult.body`；Classifier 跳过 |
| 取消是协作式还是打断 HTTP | TIMEOUT vs CANCELLED 分不清 | 协作式；已在途 HTTP 尽量 abort |
| 多实例 SessionStore SPI 是否 Phase 0 | 单测用内存，生产假设会偷跑 | Phase 0 仅内存 |
| 错误：引擎异常 vs Step FAILURE | 调用方无法区分 4xx 配置错误和厂商 403 | 引擎错误码 vs `StepOutcome` 分离 |

Phase 0 **不要**做 REST `/integrations/{code3rd}/...`。那是 Host 产品，不是 Runtime。测试用 in-process `ApiClient` + Mock Transport 即可。

规则：`allowReplay=true` 也不能覆盖 `replayability=UNSAFE|UNKNOWN` 的默认禁止。`input` 出现 Secret 字面量 → 拒绝。

---

## A3. Error / Trace

V2.7 §42–43 列了 id，没列字段。

### Execution Trace

- `executionId`, `apiId`, `definitionRevision`, `planId`, `startedAt`, `endedAt`
- `flowExecutionId`, `stepExecutionId`, `attemptId`, `requestAttemptId`

### Decision 记录（每条）

- `decisionId`
- `type`: `CONDITION` / `CHALLENGE` / `RETRY` / `REPLAY` / `SESSION` / `PIPELINE` / `AUTH_TRIGGER`
- `inputRef`：status、header 名、jsonpath、generation（值可以记，secret 不行）
- `matched` / `action`
- `reasonCode`：如 `TOKEN_EXPIRED` / `PERMISSION_DENIED` / `CONNECTION_LOST`

`all`/`any` 短路：Trace 仍记录**已求值**的子节点，未求值的标 `skipped`。

Policy 改写 action 时记两步：`CONDITION`（匹配了哪条）+ `RETRY`/`REPLAY`/`SESSION`（Policy 是否改写）。

### 硬性脱敏规则（写进附件，不要靠 code review）

- SecretValue 只记录 `secretRef` + sink 决定（ALLOW/DENY）
- header `Authorization`、query token、cookie 值默认 redacted
- script stdout 若类型是 Secret 则整段丢弃
- 异常 message 不得包含 credential 材料

### 引擎级错误码（配置/运行时，不是厂商 HTTP）

`DEFINITION_NOT_FOUND`、`REVISION_NOT_PUBLISHED`、`VALIDATION_FAILED`、`PLAN_COMPILE_FAILED`、`PLAN_CAPABILITY_UNSUPPORTED`、`SECRET_UNRESOLVABLE`、`SECRET_SINK_DENIED`、`SESSION_INCOMPATIBLE`、`AUTH_ATTEMPT_EXCEEDED`、`TRANSITION_LIMIT`、`CANCELLED`、`TIMEOUT`、`UNKNOWN_OUTCOME_NO_REPLAY`。

Validate 细码见 [07-plan-compiler.md](07-plan-compiler.md) 表 U。

---

## 预研门禁（附件之外，但阻塞 0a 编码）

| Spike | 通过标准 | 失败则改设计 |
|---|---|---|
| JSONPath Profile | 受限库可关 Filter/Script，或自研只覆盖 Root/Property/Index/Exists/Eq | 不得把 Jayway 默认模式当 Condition 引擎 |
| GraalVM Polyglot | 在选定 JDK 上 `resourceLimits` 能打断死循环；`HostAccess.EXPLICIT` 不能反射逃逸 | 换运行时基线，或 Script 降级为 Phase 1 |
| Cookie RFC | `CookieStore` 对 Domain/Path/Secure/HttpOnly/SameSite 有明确实现选择 | Mock E 无法验收 |

这三件建议 **0 预研周** 做完，结论写进 A1/A2，不要边实现边赌。

## 建议的冻结顺序

1. 写 A1 schema 能表达 Mock A、B、C、F、I（先覆盖线性 + 同接口 challenge）  
2. 写 A2 `ApiClient` + `ExecutionResult`  
3. 写 A3 错误码与 Decision 字段  
4. 做 JSONPath / GraalVM / Cookie 三个 spike，结论写回 A1  
5. 再开 0a  

Mock D/E/G/H 的 schema 可以在 0b 前补第二版 schema。**A2/A3 不要在 0a 之后再改语义**，否则 Snapshot 和 Trace 会返工。
