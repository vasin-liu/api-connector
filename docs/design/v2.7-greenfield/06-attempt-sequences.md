# Attempt 时序：Mock B / Mock C

一次 `ExecuteCommand` 内部的 ID 树。对应源设计 §7–8、§17–18、§42–44、§49。

---

## L. Mock B：401 → login → replay GET

调用：

```text
execute(apiId=mock-b, flowId=business, input={})
```

初始：无 Session，generation 未创建。冻结：**先发业务**（YAML 无 pre-auth step）。

### L1. 主路径

```text
Execution e1
 snapshot: {apiId:mock-b, revision:1, planId:P}
 │
 ├─ flowExecution fe-business
 │    └─ step getData
 │         ├─ attempt a1   AUTH_TRIGGER=SESSION_MISSING 仍先发业务
 │         │    requestAttempt r1  GET /v1/data  无 token
 │         │    HTTP 401 UNAUTHORIZED
 │         │    decision d1 CONDITION match[0] AUTHENTICATE
 │         │    outcome 对本 step 而言 = CHALLENGE/AUTH 入口，不是 FAILURE
 │         │
 │         ├─ flowExecution fe-auth   AuthInvocation(trigger=SESSION_MISSING, depth=1)
 │         │    step buildLogin     attempt a2  无 HTTP
 │         │    step login          attempt a3
 │         │         requestAttempt r2  POST /v1/login
 │         │         HTTP 200 {token}
 │         │         decision d2 CONDITION CONTINUE
 │         │    step storeToken     attempt a4  extract → session.token
 │         │         session generation 0 → 1  VALID
 │         │         decision d3 SESSION
 │         │
 │         └─ attempt a5   then=REPLAY_REQUEST
 │              requestAttempt r3  GET /v1/data  Authorization: Bearer …
 │              HTTP 200
 │              decision d4 CONDITION match[1] SUCCESS
 │
 └─ result SUCCESS  http=200
```

**ID 约束：**

- 整个过程 **一个** `executionId`
- `fe-business` 与 `fe-auth` 两个 `flowExecutionId`
- `getData` 只有一个 `stepExecutionId`，下面多个 `attemptId`（a1, a5）
- login 是另一个 `stepExecutionId`
- `r1` 与 `r3` 是不同 `requestAttemptId`；r3 必须是 Template 重建，Authorization 只出现在 r3
- `depth=1`；`authAttemptCount=1`

### L2. 100 并发 Session 过期（§49.3）

已有 Session generation=10，全部 401/`TOKEN_EXPIRED`（若把 B 的 jsonpath 改成过期码；逻辑相同）。

```text
100 × execute
  100 × r_i  业务 401
  SessionCoordinator
     Owner 1 × authentication   generation 10 → 11
     99 waiter 阻塞
  99 × 重建业务请求  带新 token
认证 HTTP 次数 = 1
```

waiter 必须 **重新读 Session** 再 Replay，禁止用进入等待前的 token 快照。

### L3. 认证失败风暴（Mock G）

login 返回 401。Owner 失败 → generation 不变或进 FAILED → Cooldown。99 waiter 得到同一失败。Cooldown 内第 101 次 **不得**再打 login。

Trace：一条 `SESSION` decision `reasonCode=AUTH_FAILED`，waiter 的 decision `reasonCode=SHARED_FAILURE`。

### L4. 错误 401 体（表 B3）

只发 r1，不进入 fe-auth。`authAttemptCount=0`。

---

## M. Mock C：Challenge + 重建

调用：`execute(apiId=mock-c, flowId=business, input={ data: {...} })`

Clock：可注入。t0=1000，认证后 t1=1001。

### M1. 主路径（§49.1）

```text
Execution e1
 │
 ├─ fe-business
 │    step stamp     a1  execution.timestamp=1000
 │    step getData   stepExecution S
 │         attempt a2
 │           requestAttempt r1
 │           POST /api/data
 │           X-Timestamp: 1000
 │           无 Authorization（flow.signed=false）
 │           body = Pipeline(jsonBody) @ t=1000 变量
 │           HTTP 403  header X-Challenge: nonce-1
 │           d1 CONDITION match[0] AUTHENTICATE
 │
 │         fe-auth  trigger=AUTH_CHALLENGE depth=1
 │           readNonce   a3  flow.nonce=nonce-1  commitOn=CHALLENGE
 │           newTimestamp a4  execution.timestamp=1001
 │           sign        a5  pipeline challengeHmac
 │             secret → HMAC sink ALLOW
 │             不写 log
 │           markSigned  a6  flow.signed=true  generation 0→1
 │
 │         attempt a7  REPLAY_REQUEST
 │           requestAttempt r2
 │           POST /api/data
 │           X-Timestamp: 1001          ← 必须变
 │           Authorization: HMAC-SHA256 <new hex>
 │           body 按 Template+当前变量重建
 │           HTTP 200
 │           d2 SUCCESS
 │
 └─ result SUCCESS
```

**禁止的实现（测试必须抓）：**

| 错误 | 断言 |
|---|---|
| clone r1 字节再发 | r2 的 timestamp/signature 仍为 1000 / 空 |
| Replay 不经 Pipeline | body 与 r1 完全相同但变量已变时仍应重建；Mock C body 若仅 json 且 input 不变，body 可以相同，但 **header 必须不同** |
| nonce 进 GLOBAL | Validate 失败 |
| 403 Permission 走 AUTH | 见 J2.C4，无 fe-auth |

DecisionTrace 最小可读链：

```text
d1 CONDITION  403 + X-Challenge  → AUTHENTICATE
d3 CHALLENGE  header X-Challenge → nonce bound
d4 PIPELINE   hmac-sha256        → sink HMAC ALLOW
d5 SESSION    generation 0→1
d6 REPLAY     template getData   → requestAttempt r2
d2 CONDITION  200                → SUCCESS
```

### M2. 一次性 nonce 失败 → RETRY_FLOW

r2 仍 403 + 新 `X-Challenge: nonce-2`（或 403 且旧 nonce 失效）。

```text
Policy: REPLAY_REQUEST 非法（flow.nonce 已消费）
     → RETRY_FLOW authentication from readNonce
r3 不得带着 nonce-1 的 HMAC 再发
fe-auth 第二次：nonce-2，timestamp=1002，新签名
authAttemptCount=2
```

若写成 `RETRY_REQUEST`/`REPLAY_REQUEST` 单测应失败。

### M3. 503 与断连对照

| 事件 | action | 新认证 | 新 timestamp |
|---|---|---|---|
| r2 完成后 503 | RETRY_REQUEST | 否 | **冻结为 RETRY_REQUEST 不重跑 `stamp` step**，只用当前变量重建。若签名含 timestamp 且服务端窗口极短，应在 YAML 用 RETRY_FLOW。Mock C 的 503 不改 timestamp。 |
| r1 写出后断连 | UNKNOWN 默认 FAIL | 否 | 无 r2 |
| r2 写出后断连 | 默认 FAIL（CONDITIONALLY_SAFE 仍挡 UNKNOWN） | 否 | — |

503 是明确 HTTP 结果；断连不是（源设计 §21 与 §23）。

---

## N. 0a 测试能直接抄的断言

0a 不跑 AUTH，但同一张表可以裁剪：

**Mock A fixture**

- A1：outcome=SUCCESS，transport invocations=1，snapshot 不变
- A2：FAILURE，invocations=1
- A4：UNKNOWN_OUTCOME，invocations=1

**Condition 单元（无 Transport）**

- J2.C2 / C4 / C8 用纯 `TransitionEvaluator` + 伪造 `RawHttpResponse`
- B5：401 才 AUTH 的条件，403+同 body 不匹配 Mock B transition 0

**默认无匹配**

- 业务 418 且无 transition → FAILURE，不是 SUCCESS

0b 再打开 L1、M1 作为集成测试；0c 打开 M1 的 header 不等断言和 M2。
