# OpenAPI 导入规则（草案）

## 目标

从厂家 OpenAPI 3 文档生成 Connector Spec 初稿，人工在 UI 中补认证与响应映射。

## 映射规则

| OpenAPI | Connector Spec |
|---------|----------------|
| `servers[0].url` | `connector.baseUrl` |
| `paths.*` | Catalog 接口方法 + `@HttpGet`/`@HttpPost` |
| `operationId` 或 path+method | 方法名 → `endpoint.id` |
| `summary` / `description` | 可选 `@CatalogEndpoint`；否则由 id/path 自动推导 |
| `tags[0]` | 由 path 自动分组，或 `@CatalogEndpoint(group=...)` |
| `parameters.query` | `@QueryNames` + 运行时 `body.query` |
| `securitySchemes.apiKey` | `auth.type: api_key_query/header` |
| `securitySchemes.http(bearer)` | `bearer_static` 或 `oauth2_*` |
| `securitySchemes.oauth2` | `oauth2_client_credentials` 等 |
| `responses.200.schema` | 辅助生成 `response.dataPath` |

## 无法自动推断（需人工）

- 国内私有签名（科拓、途强、IDPS AK/SK）
- SM4 加密 body（CETC）
- 多步登录（大华）
- 成功码非标准（`code==0` vs `200`）

## 导入流程

1. 上传 OpenAPI YAML/JSON
2. 生成 `connector` 草稿 + `endpoints` 列表
3. 向导选择 Auth Profile（见 [profile-registry.md](./profile-registry.md)）
4. 沙箱试调 `POST /api/v1/integrations/{code3rd}/endpoints/{endpointId}/invoke`（或通用 `/invoke`）
5. 保存版本并发布

## 实现状态

**规划中**（P2）。当前内置厂家请维护 Java Catalog；控制台/JSON Spec 仅保留 id/method/path。
