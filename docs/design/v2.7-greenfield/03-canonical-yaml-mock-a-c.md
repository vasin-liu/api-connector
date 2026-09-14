# Canonical YAML 草稿：Mock A–C

对应源设计 §47–50。语法约定见 [01-encoding-prerequisites.md](01-encoding-prerequisites.md)。

---

## Mock A — API Key

验收：请求带固定 query/header 密钥材料；200 → `SUCCESS`；trace 里看不到 key。

```yaml
schema:
  version: 1

definition:
  id: mock-a
  revision: 1
  authProfile: api-key-query

credentials:
  apiKey:
    type: secret
    valueRef: secret/mock-a/api-key
    apiId: mock-a

variables:
  baseUrl:
    type: string
    scope: GLOBAL
    value: "https://mock-a.example"

limits:
  maxAuthAttempts: 0
  maxAuthDepth: 0
  transitionLimit: 8
  executionTimeout: 10s

requests:
  getStatus:
    method: GET
    url: "{global.baseUrl}/v1/status"
    query:
      key:
        secretRef: apiKey
        sink: Authorization
    replay:
      replayability: SAFE
      allowAutomaticReplay: false
      maxAttempts: 1

pipelines:
  identity:
    nodes:
      - id: in
        type: passthrough
        ports:
          in: { name: body, type: bytes, required: false }
          out: { name: body, type: bytes }
    edges: []

flows:
  business:
    steps:
      - id: call
        request: getStatus
        pipeline: identity
        transitions:
          - when:
              status: 200
            action: SUCCESS
          - when:
              status:
                from: 500
                to: 599
            action: FAIL
            outcome: RETRYABLE_FAILURE

  authentication:
    steps: []
```

要点：`maxAuthAttempts: 0` 表示本 API 没有认证 Flow。`sink: Authorization` 让 0d 的目的地校验能跑；0a 可以先当「受控 header/query 注入」。5xx 写成 FAIL 而不是 RETRY，避免「5xx 就重试」成为隐式全局策略。

---

## Mock B — 401 → Login → Token → 业务

验收：业务先发、401 触发 AUTH；login 一次；token 进 SESSION；generation 0→1；waiter 复用。

```yaml
schema:
  version: 1

definition:
  id: mock-b
  revision: 1
  authProfile: password-login

credentials:
  account:
    type: username-password
    usernameRef: secret/mock-b/username
    passwordRef: secret/mock-b/password
    apiId: mock-b

variables:
  baseUrl:
    type: string
    scope: GLOBAL
    value: "https://mock-b.example"
  token:
    type: secret
    scope: SESSION
  loginBody:
    type: json
    scope: EXECUTION

session:
  ttl: 30m
  failureCooldown: 5s

limits:
  maxAuthAttempts: 2
  maxAuthDepth: 1
  transitionLimit: 16
  executionTimeout: 15s

requests:
  getData:
    method: GET
    url: "{global.baseUrl}/v1/data"
    headers:
      Authorization:
        secretVar: session.token
        prefix: "Bearer "
        sink: Authorization
    replay:
      replayability: SAFE
      allowAutomaticReplay: true
      maxAttempts: 2

  login:
    method: POST
    url: "{global.baseUrl}/v1/login"
    headers:
      Content-Type: "application/json"
    body:
      pipeline: loginEncode
    replay:
      replayability: UNSAFE
      allowAutomaticReplay: false
      maxAttempts: 1

pipelines:
  identity:
    nodes:
      - id: passthrough
        type: passthrough
    edges: []

  loginEncode:
    nodes:
      - id: json
        type: codec.json
        ports:
          in: { name: object, type: object, required: true }
          out: { name: body, type: bytes }
    edges: []

flows:
  business:
    steps:
      - id: getData
        request: getData
        pipeline: identity
        transitions:
          - when:
              all:
                - status: 401
                - jsonpath:
                    path: $.error
                    equals: UNAUTHORIZED
            action: AUTHENTICATE
            then: REPLAY_REQUEST
          - when:
              status: 200
            action: SUCCESS

  authentication:
    steps:
      - id: buildLogin
        assign:
          execution.loginBody:
            object:
              username: { credential: account.username }
              password: { credential: account.password }
      - id: login
        request: login
        pipeline: loginEncode
        input:
          object: "{execution.loginBody}"
        transitions:
          - when:
              status: 200
            action: CONTINUE
          - when:
              status:
                from: 400
                to: 499
            action: FAIL
            session: AUTH_FAILED
      - id: storeToken
        extract:
          from:
            jsonpath: $.token
            as: secret
          to: session.token
        onCommit:
          sessionStatus: VALID
          generation: increment
```

`then: REPLAY_REQUEST` 表示 AUTH 成功后重建 **原业务 Template**（getData），不是重放 login。login 标 `UNSAFE`，丢连接也不得自动 Replay。

Session 未就绪是否先发业务：V2.7 的 `SESSION_MISSING` 允许「先发再 401」或「发前 AUTH」。本 YAML 没有 pre-auth step，**冻结为先发业务**。要先登录，应在 business flow 加显式 authenticate step。

---

## Mock C — 同接口 403 Challenge + HMAC + 重建

验收对应源设计 §49.1：第一次 403；提取 challenge；**新 timestamp**；**新 HMAC**；Session 更新；第二次 200；Trace 可解释。一次性 nonce 失败必须 `RETRY_FLOW`，不能只重发最后一包。

```yaml
schema:
  version: 1

definition:
  id: mock-c
  revision: 1
  authProfile: hmac-challenge

credentials:
  apiKey:
    type: secret
    valueRef: secret/mock-c/api-key
    apiId: mock-c

variables:
  baseUrl:
    type: string
    scope: GLOBAL
    value: "https://mock-c.example"
  nonce:
    type: string
    scope: FLOW
  timestamp:
    type: number
    scope: EXECUTION
  authSignature:
    type: string
    scope: FLOW
  signed:
    type: boolean
    scope: FLOW
    value: false

limits:
  maxAuthAttempts: 3
  maxAuthDepth: 1
  transitionLimit: 16
  executionTimeout: 15s

policy:
  onUnknownOutcome: FAIL
  onStaleFlowBinding: RETRY_FLOW   # nonce 已消费

requests:
  getData:
    method: POST
    url: "{global.baseUrl}/api/data"
    headers:
      Content-Type: "application/json"
      X-Timestamp: "{execution.timestamp}"
      Authorization:
        template: "HMAC-SHA256 {flow.authSignature}"
        sink: Authorization
        when: "{flow.signed}"
    body:
      pipeline: jsonBody
    replay:
      replayability: CONDITIONALLY_SAFE
      allowAutomaticReplay: true
      maxAttempts: 2
      idempotencyKeyStrategy: none

pipelines:
  jsonBody:
    nodes:
      - id: json
        type: codec.json
        ports:
          in: { name: object, type: object, required: true }
          out: { name: body, type: bytes }
    edges: []

  challengeHmac:
    nodes:
      - id: canonical
        type: canonicalizer.concat
        config:
          parts:
            - { secretRef: apiKey }
            - { var: flow.nonce }
            - { var: execution.timestamp }
          separator: ""
        ports:
          in_secret: { type: secret, required: true }
          in_nonce: { type: string, required: true }
          in_ts: { type: number, required: true }
          out: { name: canonical, type: bytes }
      - id: hmac
        type: signer.hmac-sha256
        ports:
          in: { name: canonical, type: bytes, required: true }
          key: { name: key, type: secret, required: true }
          out: { name: hex, type: string }
    edges:
      - from: canonical.out
        to: hmac.in
      - from:
          secretRef: apiKey
        to: hmac.key
        sink: HMAC

flows:
  business:
    steps:
      - id: stamp
        assign:
          execution.timestamp: { now: epochMillis }
      - id: getData
        request: getData
        pipeline: jsonBody
        transitions:
          - when:
              all:
                - status: 403
                - header:
                    name: X-Challenge
                    exists: true
            action: AUTHENTICATE
            then: REPLAY_REQUEST
          - when:
              all:
                - status: 403
                - jsonpath:
                    path: $.error
                    equals: PERMISSION_DENIED
            action: FAIL
            outcome: FAILURE
          - when:
              status: 200
            action: SUCCESS
          - when:
              status: 503
            action: RETRY_REQUEST

  authentication:
    steps:
      - id: readNonce
        extract:
          from:
            header: X-Challenge
          to: flow.nonce
        commitOn: CHALLENGE
      - id: newTimestamp
        assign:
          execution.timestamp: { now: epochMillis }
      - id: sign
        pipeline: challengeHmac
        output:
          flow.authSignature: hmac.hex
      - id: markSigned
        assign:
          flow.signed: true
        onCommit:
          sessionStatus: VALID
          generation: increment
```

`commitOn: CHALLENGE` 对应源设计 §10：Challenge 只提交显式允许的 mutation（nonce）。`newTimestamp` 保证 Replay 不是 clone。

---

## 0a 还需要的两个切片

**F 切片（错误 403 不得认证）** — 可在 Mock C 的 `PERMISSION_DENIED` 分支上单测，不必另写 definition。

**I 切片（UNKNOWN_OUTCOME）**

```yaml
requests:
  createOrder:
    method: POST
    url: "{global.baseUrl}/orders"
    replay:
      replayability: UNKNOWN
      allowAutomaticReplay: false
      maxAttempts: 1
policy:
  onUnknownOutcome: FAIL
```

Transport 模拟「请求已写出、连接断开」→ `UNKNOWN_OUTCOME`，断言 HTTP 次数 = 1。
