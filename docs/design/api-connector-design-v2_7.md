# api-connector V2.7 设计方案

> 应用名称：`api-connector`\
> 版本：**V2.7**\
> 定位：**HTTP API Flow & Protocol Execution Engine（纯 Outbound
> 引擎）**\
> 技术基线：**Java 21 LTS+**

## 0. V2.7 变更说明（相对 V2.6）

V2.6 已是 Runtime Contract 定版版本，V2.7 不新增概念模型，只补齐编码前必须明确的技术细节：

| 编号 | 变更点 | 章节 |
|---|---|---|
| 1 | `SecretSinkPolicy` 增加"Sink 目的地"维度，不再只按 Sink 类型判断 | §14 |
| 2 | Script 沙箱技术选型重新明确为 GraalVM Polyglot | §39 |
| 3 | `JSONPath Profile` 明确技术选型方向（受限模式库 or 自研最小解析器），列入 Phase 0 技术预研 | §32 |
| 4 | Definition Revision 与 Session 兼容性判断给出具体规则 | §5 |
| 5 | `RETRY_FLOW` 与 `REPLAY_REQUEST` 的选择时机补充具体示例 | §21 |
| 6 | 重申 `StreamBody` 不进入 Challenge/Auth 判定管线 | §36 |

## 1. 版本定位

V2.6 是 `api-connector` 的 **Runtime Contract 定版版本**。

V2.5 已完成 Outbound 边界、统一 Flow、Definition → Validate → Compile →
ExecutionPlan、Replay、Pipeline Graph、结构化 Condition DSL、Variable
Scope、DataValue/Script Result
等核心收敛。fileciteturn22file0L12-L31

V2.6 不再以扩展概念为主，而是补齐以下实现级契约：

1.  Authentication Trigger / Re-entry / Attempt；
2.  Session Generation 与单次刷新协调；
3.  SecretProvider / CredentialResolver；
4.  DataValue / SecretValue 类型体系；
5.  Step Outcome 与 HTTP Response 解耦；
6.  Retry / Refresh / Replay 分离；
7.  ExecutionSnapshot；
8.  Pipeline Port / Type / Cycle；
9.  GLOBAL 默认只读；
10. Definition Revision 与 Session / Plan 隔离；
11. CookieStore、Extension 生命周期；
12. Attempt ID / DecisionTrace；
13. Streaming 架构预留；
14. Phase 0 验证基线。

**目标：核心 Runtime 可以直接进入 Java 21
实现，不再因基础执行语义反复重构。**

------------------------------------------------------------------------

# 2. 项目定位与边界

`api-connector` 是：

> **面向标准及私有 HTTP/HTTPS API 的配置驱动、Flow 驱动、可扩展 API
> 连接与协议执行引擎。**

核心目标：

``` text
标准能力       → 配置化
复杂能力       → 内置组件化
厂商特殊逻辑   → 受控脚本化
极端/高性能逻辑 → Trusted Java Extension
```

HTTP/HTTPS 是第一类 Transport；Authentication 是第一类 Flow。V2.5 已明确
`api-connector` 只承担 Outbound，不承担内部 Gateway
语义。fileciteturn22file0L12-L31

不负责：

-   内部调用方认证；
-   Tenant / CallerIdentity；
-   GatewayRequest / GatewayResponse；
-   内部业务参数映射；
-   API Gateway 路由；
-   对内权限与限流。

------------------------------------------------------------------------

# 3. 核心原则

## 3.1 单一执行模型

``` text
Flow
├── Request Flow
├── Authentication Flow
└── Future Flow
```

业务请求、认证、Challenge、Token Refresh、跨接口登录统一由 Flow Runtime
执行。fileciteturn22file3L281-L325

## 3.2 Definition 不直接执行

``` text
Definition
 ↓ Parse
 ↓ Normalize
 ↓ Validate
 ↓ Compile
 ↓ ExecutionPlan
 ↓ ExecutionSnapshot
 ↓ Execute
```

Runtime 只执行 `ExecutionPlan`。

## 3.3 一次 Execution 绑定固定版本

Definition 发布新 Revision 不影响已经运行的 Execution。

## 3.4 Secret 默认不可观察

Secret 不允许通过普通 String、Log、Trace 或 Generic Script Output 泄露。

## 3.5 Replay 必须重新构造

``` text
OriginalRequestTemplate
 ↓ Current Variables
 ↓ Current Session
 ↓ Dynamic Values
 ↓ Pipeline
 ↓ Signer
 ↓ Actual Request
```

V2.5 已明确禁止简单 Clone 已发送
Request。fileciteturn22file3L331-L353

------------------------------------------------------------------------

# 4. 总体架构

``` text
Application
    ↓
ApiClient
    ↓
DefinitionRegistry
    ↓
ExecutionRuntime
    │
    ├── FlowRuntime
    │     ├── StepExecutor
    │     ├── TransitionEvaluator
    │     └── AuthInvocation
    │
    ├── StateRuntime
    │     ├── VariableRuntime
    │     └── StateMutation
    │
    ├── SessionRuntime
    │     ├── SessionCoordinator
    │     └── SessionStore
    │
    ├── PipelineRuntime
    │     ├── Codec
    │     ├── Transformer
    │     ├── DataProcessor
    │     ├── Canonicalizer
    │     └── Signer
    │
    ├── TransportRuntime
    ├── ScriptRuntime
    └── PolicyRuntime
          ├── Retry
          ├── Replay
          ├── Timeout
          ├── Cancellation
          └── Execution Limits
```

外围：

``` text
Canonical Definition
 ↓
Normalizer
 ↓
Validator
 ↓
PlanCompiler
 ↓
PlanCache
 ↓
ExecutionPlan
```

------------------------------------------------------------------------

# 5. ExecutionSnapshot

一次执行必须绑定不可变 Snapshot：

``` java
public record ExecutionSnapshot(
    String executionId,
    String apiId,
    String definitionRevision,
    String planId,
    Instant startedAt
) {}
```

规则：

1.  创建后不可修改；
2.  Execution 全程使用同一 `planId`；
3.  Definition 新 Revision 不影响当前 Execution；
4.  Trace 必须记录 Snapshot；
5.  Session 必须能够判断 Revision 是否兼容。

> **具体判定规则（V2.7）**：`authProfile` 与 `credentialRef`（见 §16 `SessionKey`）未变化，则 Session 视为跨 Revision 兼容，可以直接复用；只要这两者之一发生变化，旧 Session 一律判定为不兼容，必须重新走 Authentication Flow。Pipeline/Codec/Variable 等其他字段的变化不影响 Session 兼容性判断——这类变化只影响后续请求如何构造，不影响"当前是否已经通过厂商认证"这件事本身。

------------------------------------------------------------------------

# 6. Step Outcome

HTTP Response 与 Step Outcome 分离。

``` java
public enum StepOutcomeType {
    SUCCESS,
    FAILURE,
    CHALLENGE,
    RETRYABLE_FAILURE,
    CANCELLED,
    TIMEOUT
}
```

示例：

``` text
200
→ Transport Success
→ Step Success

403 + Challenge
→ Transport Success
→ Step Challenge

403 + PermissionDenied
→ Transport Success
→ Step Failure

503
→ Transport Success
→ Step RetryableFailure

Socket Timeout
→ UnknownOutcome / Timeout
```

HTTP status 不直接等价于 Flow 行为。

------------------------------------------------------------------------

# 7. Authentication Flow

Authentication 是普通 Flow 的一种。

``` java
public enum AuthTrigger {
    SESSION_MISSING,
    SESSION_EXPIRED,
    AUTH_CHALLENGE,
    EXPLICIT
}
```

Invocation：

``` java
public record AuthInvocation(
    String authFlowId,
    AuthTrigger trigger,
    int depth,
    int maxDepth
) {}
```

必须同时限制：

``` text
authAttemptCount <= maxAuthAttempts
depth <= maxDepth
```

`transitionLimit` 不能代替 Authentication 专用限制。

------------------------------------------------------------------------

# 8. 同接口与跨接口多轮认证

同接口：

``` text
POST /api/data
 ↓
403 + X-Challenge
 ↓
Extract nonce
 ↓
HMAC(secret, nonce + timestamp)
 ↓
Rebuild POST /api/data
 ↓
Authorization
 ↓
200
```

跨接口：

``` text
A
 ↓ 401
B
 ↓ 403
C
 ↓ Token
D
 ↓
Business API
```

V2.5 已覆盖这两类模型。fileciteturn22file4L431-L474

V2.6 进一步要求：

-   Authentication 不允许无限自重入；
-   Auth Attempt 独立追踪；
-   Auth 失败可被等待请求共享；
-   Refresh 完成后必须通过 Session Generation
    判断是否已经被其他请求刷新。

------------------------------------------------------------------------

# 9. State Runtime

Scope：

``` text
GLOBAL
SESSION
EXECUTION
FLOW
LOCAL
```

推荐：

  Scope       生命周期     默认可变性
  ----------- ------------ ------------
  GLOBAL      Definition   Read-only
  SESSION     Session      Mutable
  EXECUTION   Execution    Mutable
  FLOW        Flow         Mutable
  LOCAL       Step         Mutable

GLOBAL 表示同一 `ApiDefinition` 内共享的固定配置，不表示 Engine
全局共享。V2.5 已完成这一语义修正。fileciteturn22file1L93-L112

------------------------------------------------------------------------

# 10. StateMutation

Step 不直接修改共享状态：

``` text
Step
 ↓
Local Mutation
 ↓
Step Outcome
 ↓
Commit / Discard
```

``` java
public interface StateMutation {
    void set(String name, DataValue value);
    void remove(String name);
}
```

建议：

``` text
SUCCESS
→ Commit

CHALLENGE
→ 只提交显式允许的 Mutation

FAILURE
→ 默认 Discard

CANCELLED
→ Discard

TIMEOUT
→ Discard
```

------------------------------------------------------------------------

# 11. DataValue / SecretValue

统一：

``` java
public sealed interface DataValue
        permits StringValue,
                NumberValue,
                BooleanValue,
                BytesValue,
                JsonValue,
                ObjectValue,
                ListValue,
                SecretValue,
                NullValue {
}
```

Secret：

``` java
public non-sealed interface SecretValue
        extends DataValue {
    SecretMetadata metadata();
}
```

Secret 必须避免通用：

``` text
String reveal()
```

而通过受控 Consumer / Capability 使用。

------------------------------------------------------------------------

# 12. Secret Provider

``` java
public interface SecretProvider {

    SecretValue resolve(
        SecretRef ref,
        SecretAccessContext context
    );
}
```

可支持：

``` text
Environment
File
Vault
KMS
HSM
External Secret Service
```

Definition 只保存：

``` yaml
credentials:
  apiKey:
    type: secret
    valueRef: secret/vendor-api-key
```

不保存真实 Secret。

------------------------------------------------------------------------

# 13. CredentialResolver

Credential 与 Secret Material 分离：

``` text
Credential Definition
 ↓
CredentialResolver
 ↓
SecretProvider
 ↓
Credential Material
```

``` java
public interface CredentialResolver {

    Credential resolve(
        String credentialRef,
        CredentialResolveContext context
    );
}
```

这样可统一支持：

``` text
API Key
Username / Password
Private Key
Certificate
Multiple Secrets
```

------------------------------------------------------------------------

# 14. Secret Sink Policy

``` java
public interface SecretSinkPolicy {

    void check(
        SecretValue value,
        SecretSink sink,
        SinkDestination destination
    );
}
```

``` java
public record SinkDestination(
    String targetHost,
    String targetApiId
) {}
```

建议：

``` text
HMAC            ALLOW
Signer          ALLOW
AES             ALLOW
Authorization   CONDITIONAL
Trace           DENY
Log             DENY
Generic String  DENY
```

> **调整（V2.7）**：仅按 Sink 类型判断不足以防止密钥误用/泄露——如果一个 `SecretValue` 被判定送往 `Authorization` 这类"CONDITIONAL"的 Sink，还必须比对 `SecretRef` 声明的归属方与 `SinkDestination` 是否一致。**规则**：`Authorization` Sink 只有在 `destination.targetApiId` 与该 `SecretValue` 所属 Credential 声明的 `apiId` 完全一致时才允许，否则一律 `DENY`。这可以防止被篡改或写错的 Definition/脚本把厂商 A 的密钥错误地带入对厂商 B 的请求中——这类误用光靠"Sink 类型是否是 Authorization"无法拦截，必须同时看目的地。

------------------------------------------------------------------------

# 15. Session

``` text
Session
├── AuthState
├── CredentialState
├── CookieContext
└── Metadata
```

状态：

``` text
CREATED
REFRESHING
VALID
EXPIRED
INVALID
REVOKED
FAILED
```

Session 与 Cookie State 的分离原则沿用
V2.5。fileciteturn22file1L145-L167

------------------------------------------------------------------------

# 16. SessionKey

``` java
public record SessionKey(
    String apiId,
    String definitionRevision,
    String authProfile,
    String credentialRef
) {}
```

不包含：

``` text
tenantRef
callerRef
```

内部调用方隔离属于 Gateway / Adapter。

------------------------------------------------------------------------

# 17. Session Generation

``` java
public record SessionSnapshot(
    SessionKey key,
    long generation,
    SessionStatus status,
    Instant expiresAt
) {}
```

刷新：

``` text
generation=10
 ↓
Authentication
 ↓
generation=11
```

并发请求发现：

``` text
request generation = 10
current generation = 11
```

则说明已有请求完成刷新，不应重复认证。

------------------------------------------------------------------------

# 18. SessionCoordinator

``` java
public interface SessionCoordinator {

    SessionLease acquire(
        SessionKey key,
        SessionRequirement requirement
    );
}
```

并发模型：

``` text
Request A ─┐
Request B ─┼→ SessionCoordinator
Request C ─┘
               ↓
        Single Refresh Owner
               ↓
          Session Updated
               ↓
      Waiting Requests Continue
```

要求：

1.  同一 Session 同一时刻最多一个 Refresh Owner；
2.  Waiter 必须重新读取 Session；
3.  Refresh 成功后共享新 Generation；
4.  Refresh 失败时共享失败结果；
5.  连续失败进入 Failure Cooldown；
6.  Cooldown 内不得再次产生认证风暴。

------------------------------------------------------------------------

# 19. Session Failure Reason

``` text
EXPIRED
REMOTE_REJECTED
CREDENTIAL_CHANGED
DEFINITION_CHANGED
MANUAL_INVALIDATION
AUTH_FAILED
REFRESH_FAILED
```

例如：

``` text
403 TOKEN_EXPIRED
→ EXPIRED

403 ACCOUNT_DISABLED
→ REMOTE_REJECTED / INVALID
```

------------------------------------------------------------------------

# 20. CookieStore

Cookie 数据必须只有一个权威来源：

``` text
Session
 ↓
CookieContext
 ↓
CookieStore
```

``` java
public interface CookieStore {

    void save(SetCookie cookie);

    List<Cookie> cookiesFor(URI uri);

    void clear();
}
```

必须遵守：

``` text
Domain
Path
Secure
HttpOnly
SameSite
Expires
```

------------------------------------------------------------------------

# 21. Retry / Refresh / Replay

三者必须严格分离：

``` text
Failure
 ↓
Policy Decision
 ├── NO_ACTION
 ├── REFRESH_SESSION
 ├── REPLAY_REQUEST
 ├── RETRY_REQUEST
 ├── RETRY_FLOW
 └── FAIL
```

例如：

``` text
403 TOKEN_EXPIRED
→ REFRESH_SESSION
→ REPLAY_REQUEST

503
→ RETRY_REQUEST

Connection Lost
→ UNKNOWN_OUTCOME
→ 默认不 Replay
```

> **`RETRY_FLOW` 与 `REPLAY_REQUEST` 的选择时机（V2.7）**：两者不是任选其一的同级选项，判断依据是**失败发生的位置**。如果失败发生在最终业务请求本身、且 Flow 内部状态（Session、变量）仍然有效（例如业务请求 503），只需要 `REPLAY_REQUEST` 重发这一个请求；如果失败导致 Flow 内部某个中间产物已经失效（例如同接口 Challenge 认证链路里提取的 `nonce` 只能用一次，一旦这次尝试失败，`nonce` 已经不能复用），必须走 `RETRY_FLOW`，从对应的认证 Step 重新开始整条 Flow，而不能只重发最后一次业务请求——重发只会带着一个已经失效的 `nonce`，必然再次失败。

------------------------------------------------------------------------

# 22. ReplayPolicy

``` java
public record ReplayPolicy(
    Replayability replayability,
    boolean allowAutomaticReplay,
    int maxAttempts,
    String idempotencyKeyStrategy
) {}
```

``` java
public enum Replayability {
    SAFE,
    CONDITIONALLY_SAFE,
    UNSAFE,
    UNKNOWN
}
```

Replay 不能仅根据 HTTP Method 判断，还应考虑：

``` text
Idempotency-Key
厂商业务语义
服务端是否可能已执行
请求副作用
```

------------------------------------------------------------------------

# 23. UNKNOWN_OUTCOME

``` text
HTTP Response
=
服务端明确返回结果

UNKNOWN_OUTCOME
=
客户端无法判断服务端是否已经执行
```

例如：

``` text
POST
 ↓
Request sent
 ↓
Connection Lost
```

默认：

``` text
禁止自动 Replay
```

除非显式 ReplayPolicy 允许。

------------------------------------------------------------------------

# 24. OriginalRequestTemplate

``` java
public record OriginalRequestTemplate(
    HttpMethod method,
    URI uri,
    Headers headers,
    DataValue body,
    RequestMetadata metadata
) {}
```

Replay：

``` text
Template
 ↓
Variables
 ↓
Session
 ↓
Dynamic Values
 ↓
Pipeline
 ↓
Signature
 ↓
Actual Request
```

V2.5 已将这一机制作为核心 Replay 模型。fileciteturn22file4L478-L521

------------------------------------------------------------------------

# 25. Pipeline：Data Flow

``` text
Flow
=
Control Flow

Pipeline
=
Data Flow
```

Pipeline 负责：

``` text
Encode
Decode
Transform
Encrypt
Decrypt
Hash
HMAC
Compress
Canonicalize
Sign
```

不负责：

``` text
Branch
Loop
Retry
Authentication
Challenge
```

------------------------------------------------------------------------

# 26. Pipeline Graph

``` text
Pipeline Graph
├── Node
├── Edge
├── Input Port
└── Output Port
```

简单场景仍可：

``` yaml
pipeline:
  - codec: json
  - processor: gzip
  - processor: aes
  - processor: base64
  - signer: hmac-sha256
```

复杂场景：

``` text
             ┌── SHA256(body) ──→ Header
             │
Body ────────┼── AES → Base64 ──→ Request Body
             │
Timestamp ───┴──────────────────→ Sign
```

V2.5 已采用 Graph 以支持多分支数据处理。fileciteturn22file4L525-L558

------------------------------------------------------------------------

# 27. Pipeline Port / Type

``` java
public record PipelinePort(
    String name,
    DataType type,
    boolean required
) {}
```

Node 必须声明：

``` text
Input Ports
Output Ports
Input Types
Output Types
Cardinality
Capabilities
```

Validator 编译期检查：

``` text
A.output.type
      ↓
B.input.type
```

不兼容直接拒绝 Definition。

------------------------------------------------------------------------

# 28. Pipeline Cycle

Pipeline 默认禁止任意 Cycle。

原因：

``` text
Flow
=
Control Flow

Pipeline
=
Data Flow
```

循环属于 Flow。

如果未来需要循环数据处理，应使用显式 Loop Processor，而不是允许任意
Graph Cycle。

------------------------------------------------------------------------

# 29. Codec / Transformer / DataProcessor

### Codec

``` text
Object ↔ JSON
Object ↔ XML
Object ↔ Form
Object ↔ Multipart
```

### Transformer

``` text
String → String
Map → Map
List → List
Object → Object
```

### DataProcessor

``` text
Encrypt
Decrypt
Compress
Decompress
Hash
HMAC
Base64
Vendor Encode
Vendor Decode
```

三者职责不得互相吞并。

------------------------------------------------------------------------

# 30. Canonicalizer / Signer

``` text
Request
 ↓
Canonicalizer
 ↓
Canonical Representation
 ↓
Signer
 ↓
Signature
```

例如：

``` text
HTTP Method
+
URI
+
Sorted Headers
+
Timestamp
+
Body Hash
```

必须先 Canonicalize，再 Sign。

------------------------------------------------------------------------

# 31. Condition DSL

使用结构化 AST：

``` text
Condition
├── StatusCondition
├── HeaderCondition
├── JsonPathCondition
├── VariableCondition
├── AllCondition
├── AnyCondition
└── NotCondition
```

流程：

``` text
YAML
 ↓
Condition AST
 ↓
Validate
 ↓
Compile
 ↓
ConditionEvaluator
```

禁止：

``` text
SpEL
OGNL
MVEL
```

------------------------------------------------------------------------

# 32. JSONPath Profile

默认只支持受控子集：

``` text
Root
Property
Array Index
Existence
Simple Equality
```

不支持：

``` text
Script
Function
Recursive Evaluation
复杂 Filter Expression
```

目标是保证 Condition 可预测、可验证、可审计。

> **技术选型（V2.7，列入 Phase 0 技术预研）**：这条限制不能只停留在策略声明层面——主流 JSONPath 库（如 Jayway JsonPath）默认支持 Filter 表达式（`$.items[?(@.price < 10)]`），部分实现的 Filter 语法本身具备一定求值能力，不是简单配置就能安全关闭的。Phase 0 启动前需要先做一次技术预研，二选一：（a）确认某个现成库存在真正的"受限模式"且经过验证确实无法绕过；（b）如果找不到满足条件的现成库，自行实现一个只覆盖 Root/Property/Array Index/Existence/Simple Equality 这几种能力的最小解析器——考虑到需求本身很窄，自研可能比适配第三方库的隐藏功能更可控、更容易审计。

------------------------------------------------------------------------

# 33. Variable Data Flow Validation

Validator 至少实现：

``` text
Control Flow Graph
+
Definite Assignment Analysis
+
Type Analysis
+
Scope Analysis
+
Secret Flow Analysis
```

检查：

``` text
变量是否定义
变量是否可能未初始化
Scope 是否正确
类型是否匹配
Secret 是否被非法消费
分支变量是否安全跨路径使用
```

------------------------------------------------------------------------

# 34. Definition Validation 与 Runtime Validation

静态验证：

``` text
Schema
Reference
Type
Scope
Pipeline Graph
Condition AST
Script Capability
Extension
Cycle
```

运行时验证：

``` text
Secret 是否可获取
Session 是否存在
Token 是否过期
Remote Challenge
HTTP Response
Remote State
```

两者必须明确分离。

------------------------------------------------------------------------

# 35. Transport Runtime

默认：

``` text
java.net.http.HttpClient
```

抽象：

``` java
public interface HttpTransport {

    RawHttpResponse execute(
        RawHttpRequest request,
        TransportContext context
    );
}
```

未来允许：

``` text
JdkHttpTransport
ApacheHttpTransport
NettyHttpTransport
```

------------------------------------------------------------------------

# 36. ResponseBody 架构预留

Phase 0 可只实现 Bytes：

``` java
public sealed interface ResponseBody
        permits BytesBody,
                StreamBody,
                EmptyBody {
}
```

后续可支持：

``` text
Stream
SSE
Large File
Chunked
```

避免在核心 API 中把 Body 永久锁死为 `byte[]`。

> **重申（V2.7）**：`StreamBody` 类型的响应**不进入** `ResponseClassifier`/`ChallengeDetector` 判定路径——认证握手场景的响应体通常很小，天然应该走 `BytesBody`；一旦某个响应被判定/声明为 `StreamBody`，直接进入业务层处理，不参与 Challenge 识别、也不参与 Authentication 状态判断。这条规则在更早的版本里明确讨论过，此处正式写入文档，避免后续版本迭代时再次遗漏。

------------------------------------------------------------------------

# 37. Script Runtime

Script 是受控扩展。

默认允许：

``` text
Approved Variables
Approved Request/Response View
Deterministic Clock
Deterministic Random
Approved Crypto Utility
```

默认禁止：

``` text
FileSystem
Network
Process
Database
ClassLoader
Reflection
```

------------------------------------------------------------------------

# 38. Script Capability

``` text
Script
 ↓
Capability Context
 ├── Variable Read
 ├── Request Read
 ├── Response Read
 ├── Crypto
 ├── Time
 └── Approved Utility
```

Script 不直接获得完整 `ExecutionContext`。

------------------------------------------------------------------------

# 39. Script 安全边界

同 JVM：

``` text
Capability Isolation
+
Host API Isolation
+
Resource Limits
```

不承诺：

``` text
绝对安全 Sandbox
```

强隔离需求使用：

``` text
Independent Process
Container
External Worker
```

> **技术选型重申（V2.7）**：上述能力边界的内置参考实现采用 **GraalVM Polyglot Context**——`Resource Limits` 依托 GraalVM 的 `Context.Builder.resourceLimits`（基于语句执行计数的可安全中断限制，而非依赖 `Thread.interrupt()`，后者无法打断纯计算死循环）；`Host API Isolation` 依托 `HostAccess.EXPLICIT`（脚本只能访问显式暴露的 Capability API，不能任意反射宿主 Java 对象）。明确排除 Nashorn（已废弃）、Rhino、Groovy 的默认沙箱模式作为内置实现，这些方案历史上多次出现类加载器逃逸/反射逃逸导致的沙箱绕过。"不承诺绝对安全 Sandbox"这句话描述的是"同进程沙箱终究有理论上限"这一事实，不代表可以不指定具体技术方案——GraalVM Polyglot 是目前同进程方案里隔离能力最强的选择，这一点仍然是明确的技术决定。

------------------------------------------------------------------------

# 40. Extension

Trusted Java Extension 用于：

``` text
高性能逻辑
复杂第三方 SDK
JVM 原生能力
难以脚本化的算法
```

``` java
public enum ExtensionScope {
    SINGLETON,
    PER_EXECUTION
}
```

默认：

``` text
SINGLETON
+
必须 Thread Safe
```

------------------------------------------------------------------------

# 41. ExecutionContext 与 Capability Context

Runtime 内部：

``` text
ExecutionContext
```

Extension / Script 获得：

``` text
CapabilityContext
```

关系：

``` text
ExecutionContext
       │
       └── CapabilityFactory
               ├── ScriptContext
               └── ExtensionContext
```

避免把全部 Runtime 能力暴露给扩展。

------------------------------------------------------------------------

# 42. Observability

每次 Execution 至少记录：

``` text
executionId
definitionRevision
planId
flowId
stepId
attemptId
requestAttemptId
decisionId
```

层次：

``` text
Execution
 ├── Flow Execution
 │    └── Step Execution
 │          └── Request Attempt
 └── Decision Trace
```

Secret 内容不得进入 Trace。

------------------------------------------------------------------------

# 43. DecisionTrace

必须能够解释：

``` text
Challenge Decision
Condition Decision
Retry Decision
Replay Decision
Session Decision
Pipeline Decision
```

示例：

``` text
403
 ↓
ChallengeDetector
 ↓
matched TOKEN_EXPIRED
 ↓
REFRESH_SESSION
 ↓
generation 10 → 11
 ↓
REPLAY_REQUEST
```

DecisionTrace 是未来 Visual Debugger 的基础。

------------------------------------------------------------------------

# 44. Attempt Model

一次 Execution 可能：

``` text
Request #1
 ↓
403
 ↓
Auth #1
 ↓
Replay #1
 ↓
503
 ↓
Retry #2
 ↓
200
```

必须区分：

``` text
executionId
flowExecutionId
stepExecutionId
attemptId
```

------------------------------------------------------------------------

# 45. Definition Revision / Plan Cache

推荐：

``` yaml
schema:
  version: 1

definition:
  id: vendor-demo
  revision: 12
```

含义：

``` text
schema.version
=
Canonical Definition 格式版本

definition.revision
=
具体配置版本
```

Plan Cache：

``` text
DefinitionId + Revision
 ↓
PlanCache
 ↓
ExecutionPlan
```

Revision 不变 → Plan 可复用。\
Revision 改变 → 新 Plan。

------------------------------------------------------------------------

# 46. Definition Lifecycle

建议：

``` text
DRAFT
 ↓
VALIDATED
 ↓
PUBLISHED
 ↓
DISABLED
```

Runtime 只执行 `PUBLISHED`。

发布新 Revision 不影响已有 ExecutionSnapshot。

------------------------------------------------------------------------

# 47. API Definition 示例

``` yaml
schema:
  version: 1

definition:
  id: vendor-demo
  revision: 1

credentials:
  apiKey:
    type: secret
    valueRef: secret/vendor-api-key

variables:
  vendorApiVersion:
    type: string
    scope: GLOBAL
    value: "v2"

  nonce:
    type: string
    scope: FLOW

  token:
    type: secret
    scope: SESSION

  timestamp:
    type: number
    scope: EXECUTION

flows:
  business:
    steps:
      - request: getData

  authentication:
    steps:
      - request: challenge
      - request: login
```

------------------------------------------------------------------------

# 48. 私有 403 Challenge 示例

``` yaml
flows:
  business:
    steps:
      - request: getData
        transitions:
          - when:
              status: 403
              header:
                X-Challenge: exists
            action: AUTHENTICATE

  authentication:
    steps:
      - extract:
          from: response.header.X-Challenge
          to: nonce

      - transform:
          type: hmac-sha256
          input:
            - secret: apiKey
            - variable: nonce
            - variable: timestamp
          output: authSignature
```

执行：

``` text
Business Request
 ↓
403 Challenge
 ↓
AUTHENTICATE
 ↓
Extract
 ↓
HMAC
 ↓
Session Update
 ↓
Replay OriginalRequestTemplate
 ↓
200
```

------------------------------------------------------------------------

# 49. 核心验收场景

## 49.1 同接口 403 Challenge

验证：

-   第一次返回 403；
-   Challenge 正确提取；
-   第二次重新构造 Request；
-   Timestamp 重新生成；
-   Signature 重新生成；
-   Session 更新；
-   DecisionTrace 可解释。

## 49.2 跨接口多轮认证

``` text
A → 401 → B → 403 → C → Token → D
```

## 49.3 100 并发 Session Refresh

``` text
100 requests
 ↓
Session Expired
 ↓
1 Authentication Owner
 ↓
generation +1
 ↓
99 waiters continue
```

认证次数必须为 1。

## 49.4 认证失败风暴

``` text
100 requests
 ↓
Authentication Failed
```

要求：

``` text
1 Authentication
+
共享失败
+
Failure Cooldown
```

## 49.5 错误 403

``` text
403 + PermissionDenied
```

不得自动触发 Authentication。

## 49.6 UNKNOWN_OUTCOME

``` text
POST
 ↓
Connection Lost
```

默认不得自动 Replay。

## 49.7 Definition Revision

Revision 1 的 Session 不得被 Revision 2 错误复用。

## 49.8 Pipeline Type

类型不匹配必须在 Validate/Compile 阶段拒绝。

------------------------------------------------------------------------

# 50. Mock Vendor 集合

``` text
Mock A
API Key

Mock B
401 → Login → Token

Mock C
403 → Challenge → HMAC → Replay

Mock D
跨接口多轮认证

Mock E
Cookie + Token

Mock F
403 PermissionDenied

Mock G
100 并发认证失败风暴

Mock H
Definition Revision Change

Mock I
UNKNOWN_OUTCOME / Connection Drop
```

------------------------------------------------------------------------

# 51. Phase 0

## 0.1 Core Runtime Contract

``` text
ApiDefinition
DefinitionRevision
ExecutionPlan
ExecutionSnapshot
ExecutionContext
StepOutcome
StateMutation
```

## 0.2 Flow + State

``` text
FlowRuntime
Condition AST
TransitionEvaluator
VariableRuntime
StateMutation
```

## 0.3 HTTP + Replay

``` text
HttpTransport
OriginalRequestTemplate
ResponseBody
ReplayPolicy
Request Builder
```

## 0.4 Session + Concurrency

``` text
SessionStore
SessionCoordinator
SessionGeneration
CookieStore
Failure Cooldown
```

## 0.5 Pipeline

``` text
Pipeline Graph
Port Type
Codec
Transformer
DataProcessor
Canonicalizer
Signer
```

## 0.6 Script + Security

``` text
Script Runtime（GraalVM Polyglot 参考实现）
Capability
ResourceLimits
SecretProvider
SecretSinkPolicy（含 SinkDestination 校验）
JSONPath Profile 技术预研（受限库 or 自研最小解析器，见 §32）
```

## 0.7 Observability

``` text
DecisionTrace
Attempt ID
Execution Trace
Secret Redaction
```

------------------------------------------------------------------------

# 52. Phase 0 完成标准

必须完成：

``` text
Unit Test
Integration Test
Concurrency Test
Replay Test
Security Test
```

并通过：

``` text
Mock A ~ Mock I
```

特别关注：

``` text
多轮认证
Session 并发
403 Challenge
Replay
UNKNOWN_OUTCOME
Pipeline Graph
Secret Boundary
Script Capability
Definition Revision
```

------------------------------------------------------------------------

# 53. Java 21 基线

采用：

``` text
Java 21 LTS
```

推荐：

``` text
record
sealed interface
pattern matching
switch expression
virtual threads（适合 I/O 场景时）
java.net.http.HttpClient
```

不得为了使用语言特性而增加无必要复杂度。

------------------------------------------------------------------------

# 54. Module 建议

Phase 0 保守：

``` text
api-connector/
├── core
├── runtime
├── transport
└── config
```

package 内：

``` text
flow
state
session
pipeline
crypto
script
extension
policy
observability
```

SPI 稳定后再考虑进一步拆 Maven Module。

------------------------------------------------------------------------

# 55. Visual Editor 预留

未来：

``` text
Visual Editor
 ↓
Canonical Definition
 ↓
Validator
 ↓
ExecutionPlan
```

UI 不直接操作 Runtime。

可视化元素：

``` text
Flow Node
Transition
Condition
Pipeline Node
Variable
Credential Reference
Script
Policy
```

YAML、UI、API 共享同一 Canonical Definition。

------------------------------------------------------------------------

# 56. V2.6 核心执行模型

``` text
                    Execution
                       │
                       ↓
              ExecutionSnapshot
                       │
                       ↓
                 FlowRuntime
                       │
                ┌──────┴──────┐
                ↓             ↓
           RequestStep    Auth Flow
                │             │
                ↓             ↓
         RequestTemplate   Session
                │             │
                └──────┬──────┘
                       ↓
                 PipelineRuntime
                       ↓
                 TransportRuntime
                       ↓
                  RawResponse
                       ↓
              ResponseClassifier
                       │
          ┌────────────┼────────────┐
          ↓            ↓            ↓
       Normal       Challenge     Failure
          │            │            │
          ↓            ↓            ↓
       Decode       StateMutation  Policy
                       │            │
                       ↓            ↓
                    AuthFlow      Decision
                       │
                       ↓
                    Session
                       │
                       ↓
               Rebuild Request
                       │
                       ↓
                    Replay
```

------------------------------------------------------------------------

# 57. 最终 Runtime Contract

核心链路：

``` text
Definition
    ↓
Validation
    ↓
Compilation
    ↓
Plan
    ↓
ExecutionSnapshot
    ↓
Execution
```

运行时：

``` text
Request
 ↓
Response
 ↓
Outcome
 ↓
Decision
 ↓
State Mutation
 ↓
Session
 ↓
Retry / Refresh / Replay
 ↓
Rebuild
 ↓
Request
```

核心边界：

``` text
Flow
=
Control Flow

Pipeline
=
Data Flow

Session
=
Authentication State

Policy
=
Retry / Replay / Safety Decision
```

------------------------------------------------------------------------

# 58. V2.6 与 V2.5 主要变化

  编号   V2.6 变更
  ------ ----------------------------------------------------------
  1      ExecutionSnapshot
  2      Authentication Trigger / Re-entry
  3      Authentication Attempt / Depth
  4      Session Generation
  5      SessionCoordinator 单刷新者语义
  6      SecretProvider
  7      CredentialResolver
  8      SecretSinkPolicy
  9      DataValue / SecretValue 类型体系
  10     Step Outcome 与 HTTP Response 解耦
  11     Retry / Refresh / Replay 分离
  12     UNKNOWN_OUTCOME
  13     ReplayPolicy
  14     Pipeline Port / Type
  15     Pipeline 默认禁止 Cycle
  16     GLOBAL 默认只读
  17     SessionKey 增加 Definition Revision
  18     CookieStore 权威数据源明确
  19     Extension Scope / Thread Safety
  20     ResponseBody Streaming 预留
  21     Attempt ID
  22     DecisionTrace 强化
  23     Definition Lifecycle
  24     PlanCache
  25     Phase 0 增加并发失败风暴、Revision、UNKNOWN_OUTCOME 验证

------------------------------------------------------------------------

# 59. V2.7 最终结论

> **V2.6 已完成 Runtime Contract 定版；V2.7 补齐了 Secret Sink 目的地校验、Script 沙箱技术选型、JSONPath 技术预研方向、Definition Revision/Session 兼容判定规则、Retry/Replay 选择依据、StreamBody 边界重申这六处编码前必须明确的细节，可以进行 Phase 0 编码验证。**

后续不建议继续大规模增加概念模型。

优先使用真实厂商协议验证：

``` text
多轮认证
Session 并发
403 Challenge
Replay
UNKNOWN_OUTCOME
Pipeline Graph
Secret Boundary
Script Capability
Definition Revision
```

只有核心 Runtime 经过这些场景验证后，再扩展更多标准认证、厂商协议和
Visual Editor。
