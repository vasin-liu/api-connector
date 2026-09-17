# Condition / Transition 求值表

对应源设计 §6、§21–23、§31–32。测试应直接抄本表，不要凭实现反推。

---

## H. Condition 求值规则

求值对象是 **上一次 Transport 的 `RawHttpResponse` + 当前 VariableRuntime**。没有 response 的 step（`assign` / `extract` 之前）不能用 `status` / `header` / `jsonpath(from response)`。

### H1. 原子条件

| 节点 | 输入 | true | false | 编译失败 |
|---|---|---|---|---|
| `status: 403` | httpStatus | 精确相等 | 其他数字 | 无 status（UNKNOWN/未发请求）在运行时视为 false，不抛 |
| `status: {from:500,to:599}` | httpStatus | 闭区间 | 区间外 | from>to |
| `header.exists` | 头名 | 至少有一个非空值 | 缺头或值为空串 | 头名为空 |
| `header.equals` | 头名+字面量 | 任一值大小写敏感相等 | 否则 | — |
| `jsonpath.path` + `equals` | BytesBody JSON | 路径存在且标量相等 | 路径不存在或不等 | 路径含 Filter/`..`/`()` → Validate 失败 |
| `jsonpath.path` + `exists: true` | JSON | 路径有值且非 null | 否则 | 同上 |
| `variable: {scope,name,equals}` | VariableRuntime | 已赋值且相等 | 未赋值或不等 | GLOBAL 只读无关；未声明变量 → Validate 失败 |
| `variable.exists` | VariableRuntime | 非 `NullValue` 且已 commit | 未定义/`NullValue` | 未声明变量 → Validate 失败 |

补充：

- HTTP 头名匹配 **大小写不敏感**，值 **敏感**。
- `jsonpath` 只对 `BytesBody`。`EmptyBody` / 非 JSON → 条件 false。`StreamBody` **不进入** Classifier/Condition（源设计 §36），该 step 不得配置 response 条件。
- `equals` 只比标量（string/number/boolean/null）。比到 object/array → false，不递归。
- Secret 不能出现在 `equals` 右值；左值若是 Secret → Validate 失败（防 trace 泄漏）。

### H2. 组合

```text
all  : 空列表非法（Validate 拒绝，避免无意义匹配）
any  : 空列表非法（Validate 拒绝）
not  : 恰好一个子条件
嵌套深度 ≤ 8
```

短路：`all` 遇 false 停止；`any` 遇 true 停止。DecisionTrace 仍记录**已求值**的子节点，未求值的标 `skipped`。

### H3. 无 Response 时

| 条件 | 行为 |
|---|---|
| status / header / response jsonpath | 运行时 false |
| variable | 正常求值 |
| 整个 `when` 因此为 false | 该 transition 不匹配 |

---

## I. Transition 匹配表

规则：

1. 只看当前 step 的 `transitions` 列表，**第一匹配胜出**。
2. 无 `when` 的 transition 非法（Validate 失败）。不允许隐式 else。
3. `action` 决定控制流；`then` 只允许挂在 `AUTHENTICATE` / `REFRESH_SESSION` 上。
4. 无匹配时的默认（写进 runtime，YAML 不能改）：

| Transport | HTTP | 默认 action | outcome |
|---|---|---|---|
| 完成 | 2xx | SUCCESS | SUCCESS |
| 完成 | 4xx/5xx | FAIL | FAILURE（5xx 且未声明时仍是 FAILURE，不是自动 RETRY） |
| 未完成（写出后断连） | — | FAIL | 映射为 UNKNOWN_OUTCOME → 默认不 Replay |
| 超时未写出 | — | FAIL | TIMEOUT |

5xx 自动 `RETRY_REQUEST` **必须写在 YAML**。这是为了 Mock C 的 503 与 Mock I 的 POST 断连不会被同一套默认逻辑误伤。

### I1. Action 语义

| action | 0a | 0b+ | 含义 |
|---|---|---|---|
| `SUCCESS` | 有 | 有 | 结束 Execution，commit，outcome=SUCCESS |
| `FAIL` | 有 | 有 | 结束，discard（除非另有 commitOn），outcome 用 `outcome:` 或默认 FAILURE |
| `CONTINUE` | 有 | 有 | 下一步；SUCCESS 语义的 commit |
| `RETRY_REQUEST` | 字段可解析，执行拒绝 | 有 | 同一 Template 重建再发；不跑 Auth Flow |
| `RETRY_FLOW` | 拒绝 | 有 | 从指定 step（默认当前 flow 起点或 `from:`）重跑；丢弃 FLOW 变量 |
| `REPLAY_REQUEST` | 拒绝 | 有 | 只重建**业务** OriginalRequestTemplate |
| `AUTHENTICATE` | Compile 可留，execute 报 UNSUPPORTED | 有 | 进入 authentication flow |
| `REFRESH_SESSION` | 拒绝 | 有 | Coordinator 单 Owner 刷新，成功后按 `then` |

非法组合（Validate 失败）：

- `then` 出现在非 AUTH/REFRESH 上
- `AUTHENTICATE` 但 definition 无 authentication flow 或 steps 为空
- `REPLAY_REQUEST` 作为独立 action 且当前 step 不是业务 request（Replay 目标必须是 `OriginalRequestTemplate`）
- `RETRY_FLOW` 带 `then`
- `SUCCESS` + `session: AUTH_FAILED`

`AUTHENTICATE` 的合法写法只有：

```yaml
action: AUTHENTICATE
then: REPLAY_REQUEST | CONTINUE | FAIL
```

`CONTINUE` 表示认证后接着业务 flow 的**下一个 step**，不重建刚才那一发。Mock B/C 用 `then: REPLAY_REQUEST`。

---

## J. firstMatch 用例表

表中「匹配」= 选中的 transition 下标（0-based）。`body` 均为 JSON `BytesBody`。

### J1. Mock A `getStatus`

transitions：`200 → SUCCESS`；`500-599 → FAIL RETRYABLE_FAILURE`

| # | status | body | 匹配 | action | outcome | HTTP 次数 |
|---|---|---|---|---|---|---|
| A1 | 200 | `{}` | 0 | SUCCESS | SUCCESS | 1 |
| A2 | 403 | `{"error":"DENIED"}` | 无 | 默认 FAIL | FAILURE | 1 |
| A3 | 503 | `{}` | 1 | FAIL | RETRYABLE_FAILURE | 1 |
| A4 | 断连 | — | 无 | FAIL | UNKNOWN_OUTCOME | 1，禁止第 2 次 |

A3 故意 **不** RETRY：Mock A 没写 `RETRY_REQUEST`。

### J2. Mock C `getData`（业务 step）

transitions 顺序：

0. 403 + `X-Challenge` exists → `AUTHENTICATE` then `REPLAY_REQUEST`
1. 403 + `$.error == PERMISSION_DENIED` → `FAIL` FAILURE
2. 200 → `SUCCESS`
3. 503 → `RETRY_REQUEST`

| # | status | headers | body | 匹配 | action | 说明 |
|---|---|---|---|---|---|---|
| C1 | 200 | — | `{}` | 2 | SUCCESS | — |
| C2 | 403 | `X-Challenge: abc` | `{}` | 0 | AUTHENTICATE | header 优先于 error json |
| C3 | 403 | `X-Challenge: abc` | `{"error":"PERMISSION_DENIED"}` | 0 | AUTHENTICATE | **第一匹配**；Challenge 赢 |
| C4 | 403 | — | `{"error":"PERMISSION_DENIED"}` | 1 | FAIL | 不得进 AUTH（Mock F） |
| C5 | 403 | — | `{"error":"OTHER"}` | 无 | FAIL FAILURE | 未声明的 403 |
| C6 | 503 | — | `{}` | 3 | RETRY_REQUEST | Session 不变 |
| C7 | 401 | — | `{}` | 无 | FAIL | 无 AUTH 隐式触发 |
| C8 | 403 | `x-challenge: abc` | `{}` | 0 | AUTHENTICATE | 头名大小写不敏感 |
| C9 | StreamBody 200 | — | stream | — | 不进表 | 不分类，不 Challenge |

C3 是回归点：同时有 Challenge 头和 Permission JSON 时，必须认证而不是 FAIL。若产品想让 Permission 优先，必须把 transition 1 挪到 0——**顺序即优先级**。

Compiler 禁止按 action 类型重排。加一条编译后快照测试：`transitions[0].action == AUTHENTICATE`。

### J3. Mock B `getData`

0. 401 + `$.error == UNAUTHORIZED` → AUTHENTICATE then REPLAY  
1. 200 → SUCCESS

| # | status | body | 匹配 | action |
|---|---|---|---|---|
| B1 | 200 | `{"ok":true}` | 1 | SUCCESS |
| B2 | 401 | `{"error":"UNAUTHORIZED"}` | 0 | AUTHENTICATE |
| B3 | 401 | `{"error":"ACCOUNT_DISABLED"}` | 无 | FAIL（REMOTE_REJECTED 由 0b 映射，0a 仅 FAILURE） |
| B4 | 401 | 非 JSON | 无 | FAIL |
| B5 | 403 | `{"error":"UNAUTHORIZED"}` | 无 | FAIL；**不因 jsonpath 单独触发 AUTH** |

B5 卡住「只看 body 不看 status」的错误实现。

### J4. `all` / `any` / `not`

给定 status=403，头 `X-Challenge=n1`，body `{"error":"TOKEN_EXPIRED"}`。

| when | 结果 |
|---|---|
| `all: [status:403, header X-Challenge exists]` | true |
| `all: [status:403, jsonpath $.error equals PERMISSION_DENIED]` | false |
| `any: [status:200, status:403]` | true |
| `not: { status: 200 }` | true |
| `all: [status:403, not: { header X-Challenge exists }]` | false |

---

## K. Policy 在 Condition 之后

Transition 选出 action 后，Policy 还可以**降级** action，不能升级成更危险的自动重发。

```text
Transition action
    ↓
ReplayPolicy / UNKNOWN_OUTCOME / stale nonce
    ↓
Final action
```

| 候选 action | 阻挡条件 | 最终 |
|---|---|---|
| RETRY_REQUEST / REPLAY_REQUEST | `replayability=UNSAFE` | FAIL |
| RETRY_REQUEST / REPLAY_REQUEST | `UNKNOWN_OUTCOME` 且非显式允许 | FAIL |
| REPLAY_REQUEST | FLOW 绑定已失效（nonce 已消费且这次仍失败） | RETRY_FLOW |
| AUTHENTICATE | `authAttemptCount >= max` 或 `depth >= maxDepth` | FAIL AUTH_ATTEMPT_EXCEEDED |
| AUTHENTICATE | authentication steps 空 | Validate 已拒；运行时不应发生 |
| RETRY_REQUEST | 超过 request `maxAttempts` | FAIL |

DecisionTrace 记两步：`CONDITION`（匹配了哪条）+ `RETRY`/`REPLAY`/`SESSION`（Policy 是否改写）。
