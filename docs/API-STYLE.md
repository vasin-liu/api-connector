# API 风格与 OpenAPI 可读性

本文档汇总运行时 Invoke API 的设计约定、与旧 `proxy` 的迁移方式，以及 Swagger UI 的阅读顺序。

## 设计原则

| 原则 | 说明 |
|------|------|
| **Spec 驱动** | 端点 method/path 定义在 Connector Spec；运行时优先用 `endpointId` 解析 |
| **RPC 网关** | 平台不是 REST 资源建模，而是「按厂家编码转发 HTTP」 |
| **厂家状态在 body** | 默认 `platform-http-status: PLATFORM_OK`（HTTP 恒 200）；`vendorHttpStatus` 保留厂家原始状态。兼容期可设 `VENDOR` 透传 |
| **平台错误统一** | 未知连接器/端点、参数校验失败 → `{ "code", "message" }` |

## 运行时 API（推荐）

基础路径：`/api/v1/integrations/{code3rd}`

### 1. 列出端点

```http
GET /api/v1/integrations/IDPS/endpoints
```

返回 Spec 中 `enabled != false` 的端点目录，含 `invokeUrl` 便于复制。

### 2. 按端点调用（推荐）

```http
POST /api/v1/integrations/IDPS/endpoints/getToken/invoke
Content-Type: application/json

{
  "query": {},
  "headers": {},
  "body": null
}
```

- URL 中的 `{endpointId}` 与 Spec `endpoints[].id` 一致
- 请求体只需 `query` / `headers` / `body`；method/path 由 Spec 解析

### 3. 通用 invoke（高级 / 试调）

```http
POST /api/v1/integrations/IDPS/invoke
Content-Type: application/json

{
  "endpointId": "getToken"
}
```

或显式指定（未登记端点、临时试调）：

```json
{
  "method": "GET",
  "path": "/some/path",
  "query": {},
  "headers": {},
  "body": null
}
```

字段说明：

- `path` 为规范字段；`uri` 仍接受（`@JsonAlias`），新集成请用 `path`
- `endpointId` 与 `method+path` 二选一；同时存在时 `endpointId` 优先

### 4. proxy（已废弃）

```http
POST /api/v1/integrations/{code3rd}/proxy
```

与 `/invoke` 行为相同，仅保留兼容。新代码与文档勿再引用。

## 响应体

```json
{
  "code3rd": "IDPS",
  "endpointId": "getToken",
  "method": "POST",
  "path": "/oauth/token",
  "vendorHttpStatus": 200,
  "httpStatus": 200,
  "latencyMs": 42,
  "success": true,
  "data": {},
  "rawBody": "..."
}
```

| 字段 | 含义 |
|------|------|
| `vendorHttpStatus` | 厂家 HTTP 状态（首选） |
| `httpStatus` | 兼容字段，与 `vendorHttpStatus` 相同 |
| `success` | 按 Spec `responseEvaluator` 判定 |
| `endpointId` / `method` / `path` | 实际发出的请求摘要 |

## 平台错误

```json
{
  "code": "CONNECTOR_NOT_FOUND",
  "message": "Unknown connector: FOO"
}
```

常见 HTTP 状态：404（未知连接器/端点）、400（参数错误）、401（未授权）、429（限流）、502（厂家不可达等）。

## 鉴权与治理

配置项（`application.yml`）：

```yaml
integration:
  security:
    enabled: false
    api-key-header: X-Integration-Api-Key
    runtime-api-keys: [ "runtime-secret" ]
    admin-api-keys: [ "admin-secret" ]
  invoke:
    audit-enabled: true
    rate-limit-per-code3rd-per-minute: 0   # 0=关闭
```

| 路径 | 密钥 |
|------|------|
| `/api/v1/integrations/**` | `runtime-api-keys` |
| `/api/v1/admin/**`、`/console/**` | `admin-api-keys` |
| `/actuator/health`、`/swagger-ui/**` | 默认公开 |

Catalog 受管连接器（`IDPS`、`GAODE_*`、`BAIDU_*`、`DEMO_*`）禁止 `method+path` 自由调用，必须使用 `endpointId`（`STRICT_ENDPOINTS`）。

生产环境请使用 `application-prod.yml` 并注入密钥，勿将明文密钥提交仓库。

## OpenAPI / Swagger 阅读顺序

访问：`http://localhost:19090/swagger-ui.html`

### 单个厂家（推荐）

1. 右上角分组选 **`vendor-IDPS`**（或对应 code3rd）— 只看该厂家全部 API
2. 左侧标签按 **`IDPS · 路况感知 · 道路`** 等业务分组折叠
3. 每个厂家 API 对应 **一条独立操作**（如 `IDPS_roadSpeeds`），summary 即接口名称，可直接 Try it out
4. 请求体示例来自 Spec `doc.parameters`（见下文）

### 平台 / 运维

- **`admin`** — 连接器配置、Profile、同步
- **`runtime`** — 通用 invoke 模板（已隐藏，高级场景用）

Swagger UI 配置（`application.yml`）：

- `tags-sorter: alpha`
- `doc-expansion: list`
- `operations-sorter: alpha`
- `x-tagGroups` — 按厂家折叠标签（平台 / IDPS / GAODE…）

### Spec 端点文档（代码即文档）

**内置连接器**不再维护 `connectors/*.yaml`，而是在 `its-integration-connectors` 模块用 Java Catalog 接口定义：

```java
@CatalogConnector(code3rd = "IDPS", baseUrl = "https://idps.example.com")
@CatalogAuth(type = "aksk_hmac_sha256_v1", accessKeyRef = "publicKey", secretKeyRef = "appSecret")
public interface IdpsConnectorCatalog {

    @HttpGet("/api/v2/traffic-aware/road-aware/speeds")
    @QueryNames(value = {"roadclid", "from_time", "to_time"}, required = {"roadclid"})
    void roadSpeeds();
}
```

- **OpenAPI summary / group / 参数示例** 由扫描器 + `EndpointDocumentation` 自动推导
- 可选 `@CatalogEndpoint(summary=..., group=...)`、`@QueryNames` 补充业务语义
- **控制台/UI 保存的 Spec** 同样自动 enrich，**勿再写 YAML `doc` 块**（已忽略）

新增内置厂家：在 `its-integration-connectors` 增加 Catalog 接口并注册到 `BuiltinConnectorCatalogs`。

### Catalog 受管（内置厂家）

`IDPS`、`GAODE_*`、`BAIDU_*`、`DEMO_*` 等内置 code3rd 为 **catalogManaged**：

| 可改 | 不可改（仅改 Java Catalog） |
|------|---------------------------|
| 凭证、`baseUrl`（控制台/环境变量） | `endpoints`、`auth`、`response` |
| | 删除连接器（需改代码） |

从库 `reloadFromStore` 时：端点始终以 Catalog 为准，避免 DB 里旧 Spec 覆盖新代码。

## 与 system-thirdpart 对照

| 旧模式 | 新模式 |
|--------|--------|
| 每厂家一个 Controller | 统一 Invoke API + Spec YAML |
| 硬编码 URL | `GET .../endpoints` + `endpointId` |
| 业务方实现签名 | Auth Profile 在平台侧注入 |

## 管理端试调

```http
POST /api/v1/admin/connectors/{code3rd}/trial/invoke
```

- 需 **admin-api-keys**（与运行时密钥分离）
- 允许 `method+path` 试调（不受 catalog strictEndpoints 限制）
- 控制台试调面板默认调用此接口

## 流式调用（SSE）

```http
POST /api/v1/integrations/{code3rd}/endpoints/{endpointId}/invoke/stream
Accept: text/event-stream
Content-Type: application/json

{ "query": {}, "headers": {}, "body": "{...}" }
```

- 平台按行透传厂家 `text/event-stream` 响应（文心等 LLM 场景）
- 未指定 `Accept` 时自动向厂家注入 `text/event-stream`

## 旧 thirdpart URL 兼容

在 `integration.legacy.enabled=true` 时，由 `LegacyCompatFilter` 按 **最长前缀** 匹配 `integration.legacy.routes`（内置默认见 `IntegrationLegacyProperties`）：

| 旧路径前缀 | code3rd | 响应形态 |
|------------|---------|----------|
| `/idps` | `IDPS` | 厂家 JSON 透传（IdpsResult 形态） |
| `/gaode` | `GAODE_OPEN_PLATFORM` | `Result`：`code` / `message` / `data` / `success` |
| `/gaode/traffic` | `GAODE_TRAFFIC` | 同上 |
| `/baiduJiaotong` | `BAIDU_MAP` | 同上 |
| `/BaiduGpt` | `BAIDU_WENXIN`（别名） | 同上 |

| 方法 | 行为 |
|------|------|
| `GET {prefix}/**` | query 透传 → 厂家 path = 去掉前缀后的路径 |
| `POST {prefix}/**` | body 透传（JSON）；**高德交通** POST 自动改写为 GET + `ReqBody.data` → query |
| `POST /idps/getExchange` | body `{ "uri", "queryParameters" }` → GET 转发 |

### 路径别名（内置）

| Legacy 路径 | 厂家 path | 连接器 |
|-------------|-----------|--------|
| `/gaode/placeAroundSearch` | `/v5/place/around` | `GAODE_OPEN_PLATFORM` |
| `/gaode/traffic/getRectangleTrafficInfo` | `/v3/traffic/status/rectangle` | `GAODE_OPEN_PLATFORM` |

### IDPS 聚合接口（Special Handler）

| Legacy 路径 | 行为 |
|-------------|------|
| `/idps/roadIndex` | 聚合 speeds + congestion-indexes + congestion-miles |
| `/idps/districtIndex` | 聚合 macro road-speeds + congestion-miles + congestion-indexes |
| `/idps/getSign` | 本地 MD5 签名（凭证 `loginName` + `key`） |
| `/idps/currentUserInfo` | 转发 `POST /brain-auth/check/getUserInfo` |

> 说明：旧 Gaode Controller 中其余 **业务方法名** 若与厂家 path 不一致，可通过 `LegacyPathAlias` 扩展；交通指数类 POST 已统一改写为 GET + query。
