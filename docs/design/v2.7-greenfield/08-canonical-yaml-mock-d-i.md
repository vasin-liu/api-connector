# Canonical YAML 草稿：Mock D / E / H / I

Mock A–C 见 [03-canonical-yaml-mock-a-c.md](03-canonical-yaml-mock-a-c.md)。  
F 不另写 definition：用 Mock C 的 `PERMISSION_DENIED` 分支（表 J2.C4）。  
G 不另写 definition：用 Mock B YAML，FakeTransport 使 login 恒失败。

语法约定同 03 / [01-encoding-prerequisites.md](01-encoding-prerequisites.md)。

---

## Mock D — 跨接口多轮认证

验收：业务 GET 先发；401 后依次 hopA → hopB → hopC 取 token；再重建业务 GET。depth=1，三跳都在 authentication flow 内。

```yaml
schema:
  version: 1

definition:
  id: mock-d
  revision: 1
  authProfile: multi-hop-login

credentials:
  account:
    type: username-password
    usernameRef: secret/mock-d/username
    passwordRef: secret/mock-d/password
    apiId: mock-d

variables:
  baseUrl:
    type: string
    scope: GLOBAL
    value: "https://mock-d.example"
  token:
    type: secret
    scope: SESSION
  ticket:
    type: string
    scope: FLOW
  code:
    type: string
    scope: FLOW

limits:
  maxAuthAttempts: 2
  maxAuthDepth: 1
  transitionLimit: 24
  executionTimeout: 20s

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

  hopA:
    method: POST
    url: "{global.baseUrl}/v1/auth/start"
    replay:
      replayability: UNSAFE
      allowAutomaticReplay: false
      maxAttempts: 1

  hopB:
    method: POST
    url: "{global.baseUrl}/v1/auth/challenge"
    headers:
      X-Ticket: "{flow.ticket}"
    replay:
      replayability: UNSAFE
      allowAutomaticReplay: false
      maxAttempts: 1

  hopC:
    method: POST
    url: "{global.baseUrl}/v1/auth/token"
    headers:
      X-Code: "{flow.code}"
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

flows:
  business:
    steps:
      - id: getData
        request: getData
        pipeline: identity
        transitions:
          - when:
              status: 401
            action: AUTHENTICATE
            then: REPLAY_REQUEST
          - when:
              status: 200
            action: SUCCESS

  authentication:
    steps:
      - id: hopA
        request: hopA
        pipeline: identity
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
      - id: takeTicket
        extract:
          from:
            jsonpath: $.ticket
          to: flow.ticket
      - id: hopB
        request: hopB
        pipeline: identity
        transitions:
          - when:
              status: 200
            action: CONTINUE
      - id: takeCode
        extract:
          from:
            jsonpath: $.code
          to: flow.code
      - id: hopC
        request: hopC
        pipeline: identity
        transitions:
          - when:
              status: 200
            action: CONTINUE
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

---

## Mock E — Cookie + Token

验收：login 的 `Set-Cookie` 只进入 Session CookieStore；业务请求同时带 CookieStore 中的 cookie 与 Bearer token。不得从「上一次 RawResponse」私自抄 cookie。

```yaml
schema:
  version: 1

definition:
  id: mock-e
  revision: 1
  authProfile: cookie-token

credentials:
  account:
    type: username-password
    usernameRef: secret/mock-e/username
    passwordRef: secret/mock-e/password
    apiId: mock-e

variables:
  baseUrl:
    type: string
    scope: GLOBAL
    value: "https://mock-e.example"
  token:
    type: secret
    scope: SESSION

session:
  ttl: 30m
  failureCooldown: 5s
  cookies: true

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
    cookies: fromStore
    replay:
      replayability: SAFE
      allowAutomaticReplay: true
      maxAttempts: 2

  login:
    method: POST
    url: "{global.baseUrl}/v1/login"
    cookies: acceptSetCookie
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

flows:
  business:
    steps:
      - id: getData
        request: getData
        pipeline: identity
        transitions:
          - when:
              status: 401
            action: AUTHENTICATE
            then: REPLAY_REQUEST
          - when:
              status: 200
            action: SUCCESS

  authentication:
    steps:
      - id: login
        request: login
        pipeline: identity
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

Cookie 名/Domain/Path 由 FakeTransport 的 `Set-Cookie` 提供；运行时只允许 `cookiesFor(uri)` 出自 CookieStore。

---

## Mock H — Definition Revision 与 Session

同一 `id: mock-h`，三次装载。lookup 不得用「含 revision 的 SessionKey 全等」。

**H1 — revision 1（基线，与 Mock A 同类 API Key，无 AUTH）**

```yaml
schema:
  version: 1
definition:
  id: mock-h
  revision: 1
  authProfile: api-key-query
credentials:
  apiKey:
    type: secret
    valueRef: secret/mock-h/api-key
    apiId: mock-h
variables:
  baseUrl:
    type: string
    scope: GLOBAL
    value: "https://mock-h.example"
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
  authentication:
    steps: []
```

**H2 — revision 2：只改 pipeline 节点 id（authProfile + credentialRef 不变）**

将 `pipelines.identity.nodes[0].id` 改为 `passthrough`。执行 H1 成功后装载 H2，**MUST 复用** H1 的 session（若 H 后续改为有 SESSION 的 Mock B 形态，本条仍适用：改 codec 不改 profile）。

对本 API Key 无 SESSION 的退化：H2 验证的是 **planId 变、lookup 不因 revision 误杀**；若实现尚无 session，至少断言 execute(H2) 不要求重新解析 credential 失败。有 Session 的变体用 H2b：在 Mock B 上只改 `pipelines.identity` 节点 id，token 必须复用。

**H2b — 带 Session 的 pipeline-only revision（推荐 4.1 使用）**

复制 Mock B YAML，仅将 `definition.revision` 改为 `2`，并将 `pipelines.identity.nodes[0].id` 从 `passthrough` 改为 `passthrough-v2`。`authProfile: password-login` 与 `credentials.account` 保持不变。先用 revision 1 登录成功，再 execute revision 2：**禁止**再发 login，generation 不变。

**H3 — authProfile 变更必须重新认证**

复制 H2b，`revision: 3`，`authProfile: password-login-v2`。execute 必须跑 Authentication Flow，不得复用 revision 2 的 session。

---

## Mock I — UNKNOWN_OUTCOME / Connection Drop

验收：POST 已写出后断连 → UNKNOWN_OUTCOME；HTTP 次数 = 1；不得 Replay。TIMEOUT 仅用于「未写出就到期」（另测，不用本 YAML）。

```yaml
schema:
  version: 1

definition:
  id: mock-i
  revision: 1
  authProfile: none

variables:
  baseUrl:
    type: string
    scope: GLOBAL
    value: "https://mock-i.example"
  order:
    type: object
    scope: EXECUTION

limits:
  maxAuthAttempts: 0
  maxAuthDepth: 0
  transitionLimit: 8
  executionTimeout: 10s

policy:
  onUnknownOutcome: FAIL

requests:
  createOrder:
    method: POST
    url: "{global.baseUrl}/orders"
    headers:
      Content-Type: "application/json"
    body:
      pipeline: jsonBody
    replay:
      replayability: UNKNOWN
      allowAutomaticReplay: false
      maxAttempts: 1

pipelines:
  jsonBody:
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
      - id: create
        request: createOrder
        pipeline: jsonBody
        input:
          object: "{execution.order}"
        transitions:
          - when:
              status: 201
            action: SUCCESS
  authentication:
    steps: []
```

FakeTransport：标记 `completed=false` 且已接受 body。断言 invoke 次数 = 1，outcome = UNKNOWN_OUTCOME。
