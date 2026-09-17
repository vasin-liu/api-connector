# Phase 0 增量：0a–0d

源设计 §51 的 0.1–0.7 是能力清单。下面按依赖和 Mock 验收重切。每段都有：**做 / 不做 / 退出验收**。

```text
预研(JSONPath, GraalVM, Schema)
        ↓
      0a  线性 Runtime（单请求能跑完 Outcome）
        ↓
      0b  Session 并发与认证回绕
        ↓
      0c  Pipeline Graph + Compile + Replay 重建
        ↓
      0d  Secret / Script / Trace 收口
        ↓
      Mock A–I 全绿 = Phase 0 完成
```

0b/0c 可部分并行，但 0c 的 Replay 重建依赖 0a 的 Template，0b 的 Challenge 回绕依赖 0a 的 Transition。不要四人同时铺满 0.1–0.7。

跨阶段纪律：每个阶段只加自己的 Mock/切片，**禁止删除上一阶段的测试**。最终 CI 门槛就是源设计 §52：Unit / Integration / Concurrency / Replay / Security + Mock A–I。

并发测试（49.3 / Mock G）用虚拟线程 + 内存 SessionStore 即可，不要等 JMeter。

---

## 0a — 线性 Runtime 合同

**目标：** 一次 Definition 能被 Validate（浅）→ Compile（线性 plan）→ Execute。HTTP 成功/失败变成 `StepOutcome`，不是直接把 status 当结果。

**做：**

- `DataValue` sealed 类型（Secret 先占位，真正 sink 放到 0d）
- `ExecutionSnapshot` / `ExecutionContext` / `StateMutation` commit/discard
- 线性 Flow：只有 `request` + `assign`/`extract`（header/status）
- Condition AST 的 `status` / `header exists` / `all|any|not`（jsonpath 等预研结论）
- `HttpTransport` + `BytesBody`/`EmptyBody`（`StreamBody` 类型先定义，不实现读取）
- `OriginalRequestTemplate` 存下来，但 0a 只发一次，不 Replay
- in-process `ApiClient.execute`

**不做：** SessionCoordinator、Pipeline Graph、GraalVM、SecretProvider、DecisionTrace 全量、流式执行。

**退出验收：**

| Mock | 0a 要做到 |
|---|---|
| A | API Key 作为普通 header 变量发出，200 → SUCCESS |
| F 的一半 | 403 + PermissionDenied → FAILURE，**不**进 AUTH |
| I 的一半 | 连接断开 → TIMEOUT/UNKNOWN，**不**自动重发 |

还要有：GLOBAL 只读；FAILURE/CANCELLED/TIMEOUT discard mutation；新 revision 不影响已创建的 Snapshot。

0a 的 `SecretValue.use` 可以先只支持「写入指定 header/query」。0d 再换成 SinkPolicy。

0a 明确不出现的类型（不要先做空实现以免语义漂）：

- `SessionCoordinator` / `SessionLease` / `SessionGeneration`
- `AuthInvocation` / `CookieStore`
- `SecretProvider` / `SecretSinkPolicy` / `SinkDestination`
- `ReplayPolicy` 执行器（字段可以挂在 Template 上，0a 只读 `allowAutomaticReplay=false`）
- `ScriptRuntime` / `CapabilityContext`
- 完整 `DecisionTrace`（0a 可用结构化占位，但 A3 字段名现在就定掉）

**Mock C fixture：** 0a 可 Parse+Validate+Compile 出 Plan，但 `capabilities` 含 AUTH/REPLAY → 0a `ApiClient.execute` 返回 `PLAN_CAPABILITY_UNSUPPORTED`。Compile 遇到 `AUTHENTICATE` 不要半套 Session。

0a 第一个可测闭环：

1. 解析 Mock A YAML → `ApiDefinition`
2. Validate（未知 action、GLOBAL 写入、空 URL）→ Compile → `ExecutionPlan`
3. `ApiClient.execute` + 内存 FakeTransport 返回 200
4. 断言：发出的 query 含 key 占位（0a 可先明文注入，0d 再收口）；outcome=SUCCESS；snapshot.planId 稳定

第二个测试：FakeTransport 返回 403/`PERMISSION_DENIED` 形状 → FAILURE，且 transport 只调用一次。

---

## 0b — Session + 认证回绕

**目标：** Authentication 是 Flow；并发只有一个 Refresh Owner；失败共享 + Cooldown。

**做：**

- `SessionKey(apiId, definitionRevision, authProfile, credentialRef)`
- 兼容规则按 V2.7：仅 `authProfile`+`credentialRef` 不变才复用
- `SessionCoordinator.acquire`；generation；REFRESHING；Failure Cooldown
- `AuthTrigger` / `AuthInvocation`；`authAttemptCount` 与 `depth` 双限制
- transition `AUTHENTICATE` / `REFRESH_SESSION`
- `CookieStore` 作为 Session 内权威源（Mock E）
- 同接口 Challenge 的控制流（提取 nonce 的数据变换可先写死为 extract step，HMAC 放到 0c）

**不做：** 完整 Pipeline Graph；Script；跨进程 SessionStore。

**退出验收：**

| Mock | 必须 |
|---|---|
| B | 401 → login → token → 业务成功；generation +1 |
| D | A→B→C→D 跨接口多轮；depth/attempt 超限失败 |
| E | Cookie + Token，cookie 只从 CookieStore 出 |
| G | 100 并发失败 = 1 次认证 + 共享失败 + Cooldown 内不再打 |
| 49.3 | 100 并发过期 = 1 Owner，99 waiter 用 generation+1 |
| H | authProfile/credentialRef 变了不得复用 Session；只改 pipeline 的 revision 可以复用 |

0b 结束时 Challenge 可以还是「假 HMAC」（测试里 extract 后写死 header）。真签名重建是 0c。

0b 开工前还缺：**SessionCoordinator 状态机表**（本包有意未写，见 README）。

---

## 0c — Pipeline Graph + Compile + Replay 重建

**目标：** 数据流与控制流分开；Replay 必须从 Template 重建；Validate/Compile 能拒绝类型错误。

**做：**

- Pipeline node/edge/port；默认禁 Cycle
- Codec / Transformer / DataProcessor 职责分离；先做 json、bytes、base64、hmac-sha256、canonicalizer 最小集
- Port 类型检查：不匹配 → Compile 失败（Mock 49.8）
- Request Builder：Template + 当前变量 + Session + Pipeline + Signer → Actual Request
- Policy：`NO_ACTION` / `REFRESH_SESSION` / `REPLAY_REQUEST` / `RETRY_REQUEST` / `RETRY_FLOW` / `FAIL`
- `RETRY_FLOW` vs `REPLAY_REQUEST` 按失败位置（一次性 nonce → RETRY_FLOW）
- `ReplayPolicy`；`UNKNOWN_OUTCOME` 默认禁止自动 Replay
- `StreamBody` 跳过 Classifier/ChallengeDetector（可用 fixture 声明，不必真 SSE）

**不做：** 任意厂商 encode；Visual Editor；完整 §33 数据流编译器（只做：未定义变量、类型不匹配、GLOBAL 写入、明显 Cycle、secret 进非 ALLOW sink 的静态拒绝）。

**退出验收：**

| Mock | 必须 |
|---|---|
| C | 403 Challenge → extract nonce → HMAC(secret, nonce+ts) → **重建** POST（新 timestamp、新 signature）→ 200 |
| 49.1 | 禁止 clone 第一次请求字节 |
| 49.6 / I | POST 丢连接默认不 Replay；显式 SAFE+allow 才可 |
| 49.8 | pipeline 类型不匹配 Compile 拒绝 |
| 503 | RETRY_REQUEST 不刷新 Session |
| nonce 失效 | RETRY_FLOW 而不是重放最后一包 |

0c 开工前还缺：**内置 Pipeline 节点目录**（本包有意未写，见 README）。最小四节点可先用 Mock C：passthrough / json / concat / hmac。

---

## 0d — Secret / Script / Observability 收口

**目标：** Secret 默认不可观察；Script 只有 Capability；Trace 能讲完 49.1 那条链。

**做：**

- `SecretProvider` / `CredentialResolver`
- `SecretSinkPolicy.check(value, sink, destination)`；Authorization 必须 `targetApiId` 一致
- 禁通用 `reveal()`；受控 consumer（HMAC/Signer/Authorization）
- GraalVM Polyglot：`HostAccess.EXPLICIT` + `resourceLimits`；无 FS/Net/Process/Reflection
- CapabilityContext ≠ ExecutionContext
- DecisionTrace + attempt 层次；全路径 redaction 测试
- Extension SPI 可只留接口 + `SINGLETON` thread-safe 约定，内置实现可空

**不做：** Vault/KMS 生产适配（Environment + File 足够）；独立进程沙箱；Visual Debugger UI。

**退出验收：**

- Secret 不出现在 toString、log、trace、script 普通输出、异常 message
- 错误 apiId 的 Authorization sink → DENY，请求发不出去
- 死循环脚本被 resourceLimits 打断
- 49.1 的 DecisionTrace 能读出：`403 → Challenge → TOKEN_EXPIRED → REFRESH/AUTH → generation 10→11 → REPLAY_REQUEST`
- Mock A–I **在 0d 结束后必须全部绿**（A–I 是 Phase 0 总验收，不是 0d 私有）

---

## 与源设计 Phase 0 能力清单的映射

| 源设计 | 落在 |
|---|---|
| 0.1 Core Runtime Contract | 0a |
| 0.2 Flow + State | 0a（线性）+ 0b（Auth Flow） |
| 0.3 HTTP + Replay | 0a（单发）+ 0c（Replay 重建） |
| 0.4 Session + Concurrency | 0b |
| 0.5 Pipeline | 0a passthrough/json + 0c Graph |
| 0.6 Script + Security | 0d（JSONPath 预研在 0 之前） |
| 0.7 Observability | 0a 占位字段 + 0d 收口 |
