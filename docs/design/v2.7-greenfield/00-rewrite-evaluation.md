# V2.7 绿场重写评估

前提：完全按 `docs/design/api-connector-design-v2_7.md` 实现；不考虑历史兼容与旧代码复用。

## 1. 两套东西不是同一产品

| 维度 | 当前仓库 | V2.7 |
|---|---|---|
| 定位 | 替换 `system-thirdpart` 的接入中枢 | 纯 Outbound 执行引擎 |
| 调用方式 | `code3rd` + `endpointId` 代理一次 HTTP | Definition → Plan → Flow 执行 |
| 认证 | 请求前 `AuthProvider` 注入头/签名 | Authentication 是普通 Flow |
| 数据变换 | MappingEngine + 线性 Transform | Pipeline Graph（数据流） |
| 脚本 | Groovy | GraalVM Polyglot |
| 状态 | 内存 TokenCache | Session + Generation + Coordinator |
| 失败处理 | HTTP status / 业务 success 判定 | Outcome ≠ HTTP；Retry / Refresh / Replay 分离 |
| 不负责 | 上游网关鉴权（已部分外置） | 网关、租户、对内映射、限流 |
| 交付 | Fat JAR + `/console/` + 管理 BFF | Phase 0 只要 Runtime + Mock A–I |

当前编排是一条直线：

```text
resolve endpoint → mapRequest → transform → AuthProvider → HTTP → evaluate → mapResponse
```

对应实现是 `DefaultIntegrationOrchestrator` + `AuthEngine` + `ResponseEvaluator`。认证不能根据 403 Challenge 回绕、不能跨接口登录、不能按 Generation 协调并发刷新，也不能把「业务失败」和「需要认证」分开。

V2.7 的执行模型是：

```text
Definition → Validate → Compile → ExecutionPlan → ExecutionSnapshot
    → FlowRuntime → Pipeline → Transport
    → Outcome → Decision → Session / Replay / Retry
```

这是编译器 + 解释器 + 会话协调器，不是「更强的 Orchestrator」。按现有模块硬改，会同时背两套语义。绿场更合理。

## 2. V2.7 作为编码契约：够不够写 Phase 0

**够写核心 Runtime 骨架，不够当完整产品规格。** V2.6 已定概念，V2.7 补了 6 处实现细节，方向正确。Phase 0 仍有必须先补的缺口。

### 已经够硬、可以直接落代码的

- 单一 Flow 模型；Definition 不直接执行
- `ExecutionSnapshot` 不可变、绑死 `planId`
- `StepOutcome` 与 HTTP 解耦
- `AuthTrigger` / `AuthInvocation` / depth + attempt 双限制
- SessionKey / Generation / 单 Refresh Owner / Failure Cooldown
- Session 跨 Revision 兼容规则：只看 `authProfile` + `credentialRef`
- Secret 类型体系、`SecretSinkPolicy` + `SinkDestination`
- Retry / Refresh / Replay 分离，含 `RETRY_FLOW` vs `REPLAY_REQUEST`
- `UNKNOWN_OUTCOME` 默认不自动 Replay
- Pipeline 是数据流、默认禁止 Cycle
- Condition 用 AST，禁 SpEL/OGNL/MVEL
- `StreamBody` 不进 Challenge/Auth 判定
- Script 用 GraalVM Polyglot + `HostAccess.EXPLICIT`
- 验收场景 Mock A–I 写得很清楚

### 编码前必须先补的规格

详见 [01-encoding-prerequisites.md](01-encoding-prerequisites.md)。摘要：

1. **Canonical Definition 完整 schema** — §47–48 只是示意。  
2. **对外 API / `ApiClient` 契约** — 架构图画了，签名没有。  
3. **JSONPath 还是 Phase 0 预研** — 不能直接用 Jayway 默认模式。  
4. **GraalVM 运行时基线未写死** — 会改 STACK，不只是换 Groovy。  
5. **§33 变量数据流分析是编译器级工作** — Phase 0 应降级。  
6. **持久化 / Definition 生命周期几乎空白** — Phase 0 可以内存，但要写明。  
7. **标准认证库未列 Phase 0** — 没有内置组件目录，配置化承诺会落空。

## 3. 绿场重写是否可行

**可行，而且应该绿场。前提是把范围锁在「引擎」，不要顺便把现有中枢能力搬进去。**

V2.7 §54 的模块建议是对的：

```text
core / runtime / transport / config
```

包内再切 `flow / state / session / pipeline / crypto / script / extension / policy / observability`。SPI 稳定后再拆 Maven 模块。不要一上来复制现在的 11 模块。

| 做 | 明确不做（Phase 0） |
|---|---|
| Definition 解析 / 校验 / 编译 / PlanCache | 管理台、Visual Editor |
| FlowRuntime + Condition AST | Java Catalog / 60+ 厂商接入 |
| SessionCoordinator + CookieStore | 对内业务参数映射、错误 JSON 兼容 |
| Pipeline Graph + 有限 Codec/Signer | 入站鉴权、限流、租户 |
| HttpTransport（JDK HttpClient）+ Replay | Groovy、Jayway 全功能 JSONPath |
| GraalVM Script + Capability | Spring 管理 BFF、`/console/` |
| DecisionTrace + Mock A–I | 旧 URL、旧 DTO、旧错误码 |

现有仓库里和 V2.7 **概念相近、但不能当实现基础** 的部分（按前提：不算复用，只说明差距）：

- `HttpTransport` / `JdkHttpTransport`：形状接近 §35，但请求模型、Outcome、StreamBody 规则都要重做
- `AkskCanonicalSigner`：只是 Signer 的一种，不是 Canonicalizer/Signer 管线
- `TokenCache`：单 key 缓存，没有 Generation / 单 Owner / Cooldown
- `ResponseEvaluator`：JsonPath 判成功，不是 Classifier + ChallengeDetector
- `ConnectorSpec`：endpoint 列表 + auth map，不是 Flow Definition

这些都不该「迁过去」。当前实现离 V2.7 不是差一层抽象，是差一个运行时。

## 4. 主要技术风险

**1. Phase 0 实际是一个小编译器，不是一个 HTTP 客户端**  
Validate/Compile 含 Pipeline 类型检查、Condition AST、变量/Secret 流、Cycle 检测。解释器还要正确实现 Challenge 回绕、Replay 重建、并发 Session。Mock A–I 里任意一条做假，后面厂商协议都会反噬。

建议：Phase 0 再拆成 0a 契约与内存执行、0b Session 并发、0c Pipeline/Compile、0d Script/Secret。文档现在的 0.1–0.7 是能力清单，不是可交付增量。见 [02-phase0-increments.md](02-phase0-increments.md)。

**2. GraalVM 会改部署基线**  
当前是 Zulu 21 + 单 Fat JAR。Polyglot + 语句计数限制，通常意味着 GraalVM JDK 或额外 native 语言运行时。镜像、CI、`mvnw-jdk21.ps1`、现场 JDK 都要换。

建议：Phase 0 第一周就做「标准 JDK 能否跑通 resourceLimits」的 spike，过不了就写进平台约束。

**3. Secret 体系容易做成「看起来安全」**  
`SecretValue` 禁 `reveal()`、Sink + Destination 校验、Trace 脱敏，设计是对的。真正难的是 Jackson/日志/异常/脚本输出/Pipeline 中间值每一条路径都不漏。这要从类型系统往外推，不能事后打补丁。

**4. Replay 重建是正确的，也最容易写错**  
禁止 clone 已发请求，必须从 Template + 当前变量 + Session + Pipeline + Signer 重建。Challenge HMAC、过期 timestamp、一次性 nonce（`RETRY_FLOW`）都依赖这件事。没有 DecisionTrace 和 Replay 单测，现场只能看抓包。

**5. 产品真空**  
丢掉兼容和复用之后，这个引擎**不能**单独替换 ITS 现有第三方调用。没有代理 API、没有厂商 Definition、没有管理发布、没有对内映射。V2.7 自己也说：先验证 Runtime，再谈标准认证、厂商协议、Visual Editor。

若组织仍要「接入中枢」，V2.7 只能当内核，外面还要再做 Adapter/Host。那是第二个项目，不要写进 Phase 0。

**6. 源设计文档本身还有生成痕迹**  
文中有 `filecite` 残留和「相对 V2.6 变更」。不影响架构判断，但 Canonical Schema、ApiClient、错误模型这些「编码前必须有的附件」还没独立成可执行规格。

## 5. 工作量（绿场、不考虑迁旧代码）

按 2 名熟 JDK 21 的人估算，只做文档里的 Phase 0，并按建议砍掉完整数据流编译器：

| 增量 | 内容 | 大致时间 |
|---|---|---|
| 0 预研 | JSONPath 选型；GraalVM 运行时/resourceLimits；Definition schema 初稿 | 2–3 周 |
| 0a | DataValue、Snapshot、Flow 解释器、Condition AST、线性 Pipeline | 4–6 周 |
| 0b | SessionCoordinator、Generation、CookieStore、并发/失败风暴 | 3–4 周 |
| 0c | Pipeline Graph、Port 类型、Compile/Validate 子集、Replay 重建 | 4–6 周 |
| 0d | SecretProvider/Sink、GraalVM Capability、DecisionTrace | 3–4 周 |
| 验收 | Mock A–I + 并发/安全测试补齐 | 2–3 周 |

合计大约 **4–6 个月到「Phase 0 完成标准」**。若 §33 全做、再加完整 schema/宿主 API/OAuth 等内置 Flow，很容易到 **8–10 个月**。

这还不包含：管理台、厂商 Definition、对内映射、生产存储、多实例 Session。那些是引擎之上的产品层。

## 6. 建议

1. **新仓库或新 Maven 父工程**（如 `api-connector-runtime`），模块按 §54，Java 21，无 Spring 或只在 host 模块用 Spring。现有 `api-connector` 继续当中枢，或冻结。  
2. **先冻结 3 份编码附件，再写实现：** Canonical Definition JSON Schema、`ApiClient`/`Execution` 对外 API、错误与 Trace 字段表。  
3. **Phase 0 门禁按 Mock A–I，不要按模块完成度。** 尤其是 49.1 Challenge Replay、49.3 单 Owner 刷新、49.4 失败风暴、49.6 UNKNOWN_OUTCOME、49.7 Revision。  
4. **JSONPath 与 GraalVM 必须先 spike。** 任一失败都要改设计。  
5. **§33 降级。** Phase 0 做类型/必填/Secret sink 静态检查 + 运行时强制；完整 definite assignment 放到引擎被 2–3 个真实协议打过之后。  
6. **不要在 Phase 0 带回这些旧产品决策：** Groovy、Java Catalog、MappingEngine、`code3rd` 代理、Vue 控制台、legacy URL。  
7. **只有 Runtime 通过 Mock A–I 后，再决定 Host：** 嵌入式库、还是独立服务 + 新的管理面。

## 7. 一句话判断

V2.7 作为 **Outbound Flow Runtime 的合同，质量足够开工**；作为 **当前 api-connector 的重写说明书，范围错位**。按给定前提（不要兼容、不要复用），正确路径是：**新引擎按 V2.7 做 Phase 0，现有中枢不当改造对象。** 最大风险不是概念再变，而是 Phase 0 做成「半个编译器 + 半个平台」，两头都验不完。
