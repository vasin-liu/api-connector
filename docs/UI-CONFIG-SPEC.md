# UI 配置规范

管理端与 **api-connector** 运行时之间的 UI/数据契约。

**部署形态：** 控制台由独立模块 `api-connector-ui` 构建，与 `api-connector-app` 打在同一 fat JAR、共用 `server.port`（见 [UI-MODULE.md](./UI-MODULE.md)）。与 `system-manage` 的对接为 P2 持久化阶段。与 [profile-registry.md](./profile-registry.md)、[CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md) 配套使用。

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| **双层分离** | `SM_THIRDPART_CLIENT` 存连接与凭证；`IT_CONNECTOR_SPEC` 存协议行为（YAML/JSON 同构） |
| **Profile 驱动** | 认证 Tab 由 Profile 元数据渲染，禁止把协议细节写入 `paramJson` |
| **凭证槽位稳定** | 统一 `appId` / `appSecret` / `publicKey`，Profile 通过 `*Ref` 映射 |
| **多实例** | 业务键 `(tenantId, code3rd, scope)`，与 `getUrlByAreaCode` 一致 |
| **可试调** | 保存前/发布后均可调用 Proxy API 沙箱 |

## 2. 页面结构

### 2.1 列表页（增强 `POST /client/page`）

| 列 | 来源 |
|----|------|
| 对接方名称 | `name3rd` |
| 编码 | `code3rd` |
| 区域 | `scope` |
| 协议 | `protocol` |
| 服务地址 | `host3rd`（脱敏域名） |
| Auth Profile | `IT_CONNECTOR_SPEC.auth_summary` |
| Spec 版本 | `spec_version` |
| 状态 | `status` |
| 最近试调 | `last_trial_status` / `last_trial_time`（可选） |

行操作：**编辑** · **试调** · **复制** · **发布** · **导出 YAML**

### 2.2 详情向导（四步）

```text
[① 基础信息] → [② 认证] → [③ 接口与响应] → [④ 试调]
```

#### 步骤 ① 基础信息（复用 `SmThirdpartClientVO`）

| 字段 | 控件 | 校验 |
|------|------|------|
| `code3rd` | 文本，新建可编辑 | 必填，`^[A-Z][A-Z0-9_]{2,48}$` |
| `name3rd` | 文本 | 必填，≤64 |
| `scope` | 下拉/文本 | 多区域实例必填 |
| `host3rd` | URL | 必填，`https?://` |
| `protocol` | 枚举 | `HTTP` / `HTTPS` / `WEBSOCKET` |
| `proxy` | `ip:port` | 可选 |
| `tenantId` | 文本 | 多租户 |
| `useforDesc` / `remark` | 多行文本 | 可选 |

凭证区（按 Profile 动态显示，见 §4）：

| 字段 | UI 标签（默认） | 控件 |
|------|-----------------|------|
| `appId` | 应用 ID / Client ID | 文本 |
| `appSecret` | 应用密钥 / Client Secret | 密码，留空不改 |
| `publicKey` | Access Key / 公钥 | 密码或文本 |

> **文案修正：** 禁止再标「私钥」；`publicKey` 在 IDPS 场景为 Access Key。

#### 步骤 ② 认证

| 控件 | 行为 |
|------|------|
| 实现级别 | 只读徽章 L1 / L2 / L3 |
| Profile 选择 | 下拉，数据来自注册表；L3 显示「需 SPI」说明 |
| 高级：Pipeline | 仅 L2+ 展开，步骤卡片可拖拽排序 |
| Spec 参数 | 根据 `paramSchema` 动态表单 |
| YAML 预览 | 开发者模式折叠面板 |

#### 步骤 ③ 接口与响应

**端点表格** `endpoints[]`：

| 列 | 字段 | 说明 |
|----|------|------|
| 启用 | `enabled` | 开关 |
| ID | `id` | 唯一，代理调用 `endpointId` |
| 方法 | `method` | GET/POST/PUT/DELETE/PATCH |
| 路径 | `path` | 相对 `baseUrl` |
| Body 模板 | `bodyTemplate` | 可选 JSON |

工具栏：**从 OpenAPI 导入**（P2）· **新增行** · **批量启用/禁用**

**响应映射** `response`：

| 字段 | UI | 示例 |
|------|-----|------|
| `successWhen` | JsonPath 表达式 | `$.code==0` 或 `$.url` |
| `dataPath` | JsonPath | `$.data` |
| `vendorCodePath` | JsonPath | `$.code` |
| `vendorMessagePath` | JsonPath | `$.message` |

**传输** `transport`（折叠）：

| 字段 | 说明 |
|------|------|
| `connectTimeoutMs` | 连接超时 |
| `readTimeoutMs` | 读超时 |
| `proxy` | 可覆盖基础 Tab 的 `proxy` |

**变换** `transform[]`（P2，折叠）：SM4 等，与 Auth 分离。

#### 步骤 ④ 试调

| 区域 | 内容 |
|------|------|
| 左 | 已启用端点列表，点击填充 method/path |
| 右 | Query（键值表）、Headers（键值表）、Body（JSON 编辑器） |
| 底 | 调用 `POST /api/v1/integrations/{code3rd}/proxy`，展示 status、latency、`data`、`rawBody` |

请求体与平台 API 一致：

```json
{
  "endpointId": "echoGet",
  "method": "GET",
  "uri": "/get",
  "query": {},
  "headers": {},
  "body": null
}
```

试调时 `code3rd` + `scope` 解析为运行时连接器实例（见持久化契约）。

---

## 3. 数据合并规则（UI → 运行时）

保存时前端提交 **一份聚合 DTO**（或由后端组装）：

```text
ClientInfo（凭证层） + ConnectorSpec（行为层）
         ↓
api-connector ConnectorRegistry.register(spec, credentials)
```

凭证 Map 固定键：

```json
{
  "appId": "<from SM_THIRDPART_CLIENT>",
  "appSecret": "<decrypted>",
  "publicKey": "<decrypted>"
}
```

`auth.accessKeyRef` / `secretKeyRef` 等指向上述键，**不在 Spec 中保存明文密钥**。

---

## 4. Profile UI 元数据（credentialSchema + paramSchema）

每个 Profile 在注册表扩展以下 JSON（存 `IT_AUTH_PROFILE_META` 或 classpath `profiles-meta/*.json`，P1 可先硬编码前端）。

### 4.1 元数据结构

```json
{
  "profileId": "aksk_hmac_sha256_v1",
  "level": "L1",
  "displayName": "AK/SK 规范串 (HMAC-SHA256)",
  "description": "IDPS / Traffic 大脑签名规范",
  "credentialSlots": [
    { "ref": "publicKey", "label": "Access Key", "required": true, "masked": false },
    { "ref": "appSecret", "label": "Secret Key", "required": true, "masked": true }
  ],
  "paramSchema": {
    "type": "object",
    "properties": {
      "accessKeyRef": { "type": "string", "default": "publicKey", "ui:hidden": true },
      "secretKeyRef": { "type": "string", "default": "appSecret", "ui:hidden": true }
    }
  },
  "specFragmentExample": {
    "type": "aksk_hmac_sha256_v1",
    "accessKeyRef": "publicKey",
    "secretKeyRef": "appSecret"
  }
}
```

### 4.2 P1 优先 Profile 清单

| profileId | level | credentialSlots | paramSchema 要点 |
|-----------|-------|-----------------|------------------|
| `none` | L1 | 无 | 仅 `type` |
| `aksk_hmac_sha256_v1` | L1 | publicKey, appSecret | accessKeyRef, secretKeyRef（默认隐藏） |
| `oauth2_client_credentials` | L1 | appId, appSecret | tokenUrl, scope, headerName |
| `api_key_query` | L1 | appSecret→apiKey | paramName, placement |
| `bearer_static` | L1 | appSecret→token | headerName |
| `custom_headers` | L1 | 可选 appSecret | headers[]: name, value, expression |
| `md5_body_sign_sorted_v1` | L1 | appId, appSecret | sortOrder, saltField, signField |

### 4.3 `oauth2_client_credentials` 示例

```json
{
  "profileId": "oauth2_client_credentials",
  "level": "L1",
  "displayName": "OAuth2 Client Credentials",
  "credentialSlots": [
    { "ref": "appId", "label": "Client ID", "required": true },
    { "ref": "appSecret", "label": "Client Secret", "required": true, "masked": true }
  ],
  "paramSchema": {
    "type": "object",
    "required": ["tokenUrl"],
    "properties": {
      "tokenUrl": { "type": "string", "title": "Token 地址", "format": "uri-path" },
      "scope": { "type": "string", "title": "Scope" },
      "headerName": { "type": "string", "default": "Authorization", "title": "Token 头名称" },
      "cacheTtlSeconds": { "type": "integer", "default": 3600, "minimum": 60 }
    }
  }
}
```

对应 Spec 片段：

```yaml
auth:
  type: oauth2_client_credentials
  tokenUrl: /oauth/token
  scope: openid
  headerName: Authorization
```

### 4.4 `md5_body_sign_sorted_v1` 示例（科拓类）

```json
{
  "profileId": "md5_body_sign_sorted_v1",
  "level": "L1",
  "displayName": "Body 排序 MD5 签名",
  "credentialSlots": [
    { "ref": "appId", "label": "App Key", "required": true },
    { "ref": "appSecret", "label": "App Secret", "required": true, "masked": true }
  ],
  "paramSchema": {
    "properties": {
      "signField": { "type": "string", "default": "sign" },
      "excludeFields": { "type": "array", "items": { "type": "string" }, "default": ["sign"] }
    }
  }
}
```

### 4.5 L3 / Pipeline（UI 限制）

| 场景 | UI 行为 |
|------|---------|
| L3 Profile | 下拉可选但标红「需部署 SPI」；参数仅 pluginId + 凭证 |
| `auth.pipeline[]` | 高级模式：每步选 Profile，禁止循环依赖 |
| `custom_spi` | plugin 下拉来自已注册 Spring Bean 列表（管理 API 查询） |

---

## 5. 聚合 API 契约（manage 侧，草案）

与 [CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md) 表结构一致。

### 5.1 获取完整配置（编辑页加载）

```http
GET /client/{id}/integration-config
```

响应：

```json
{
  "client": { "id": 1, "code3rd": "DEMO_AKSK", "scope": "440100", "host3rd": "https://...", "appId": "", "appSecret": "******", "publicKey": "******", "protocol": "HTTP", "proxy": null, "paramJson": null, "name3rd": "演示AKSK" },
  "spec": {
    "code3rd": "DEMO_AKSK",
    "version": "1.0.0",
    "baseUrl": "https://httpbin.org",
    "protocol": "HTTP",
    "auth": { "type": "aksk_hmac_sha256_v1" },
    "endpoints": [{ "id": "echoGet", "method": "GET", "path": "/get", "enabled": true }],
    "response": { "successWhen": "$.url", "dataPath": "$" }
  },
  "specStatus": "DRAFT",
  "authSummary": "aksk_hmac_sha256_v1"
}
```

### 5.2 保存草稿

```http
PUT /client/{id}/integration-config
Content-Type: application/json
```

请求：

```json
{
  "client": { "host3rd": "https://httpbin.org", "appSecret": null, "publicKey": "new-key-only-if-changed" },
  "spec": { "...": "完整 ConnectorSpec JSON" }
}
```

规则：`appSecret` / `publicKey` 为 `null` 表示不修改密文。

### 5.3 发布

```http
POST /client/{id}/integration-config/publish
```

行为：校验 Spec → `spec_status=PUBLISHED` → 递增 `spec_version` → 通知 integration 刷新（消息或轮询）。

### 5.4 试调（经 manage 代理，可选）

```http
POST /client/{id}/integration-config/trial
```

manage 转发至 `api-connector` Proxy API，避免浏览器跨域；请求体同 §2.2 步骤④。

### 5.5 Profile 元数据

```http
GET /integration/profiles
GET /integration/profiles/{profileId}
```

返回 §4.1 结构，供前端动态表单。

---

## 6. 校验规则（保存/发布）

| 项 | 规则 |
|----|------|
| `code3rd` | 与 client 主数据一致 |
| `baseUrl` | 与 `host3rd` 一致或显式覆盖（需记录原因） |
| `auth.type` | 必须在注册表存在 |
| `endpoints[].id` | 同一 Spec 内唯一 |
| 凭证 | 已选 Profile 的 `credentialSlots` 必填项非空 |
| L3 | 未部署 SPI 时禁止发布 |
| JsonPath | `successWhen` / `dataPath` 语法预检（可选） |

---

## 7. 与 `paramJson` 迁移

| 旧用法 | 新位置 |
|--------|--------|
| tokenUrl、scope | `auth.tokenUrl` / `auth.scope` |
| 签名盐值、字段名 | `auth.*` 或 `transform[]` |
| 真正业务扩展 | 保留 `paramJson`，文档注明「非协议」 |

迁移工具（P2）：扫描 `paramJson` 关键字，建议 Profile + 生成 Spec 草稿。

---

## 8. 实施分期

| 阶段 | UI 交付 |
|------|---------|
| **P1** | 详情页 Tab「对接规格」JSON 编辑器 + Profile 下拉 + 凭证联动；manage 聚合 API；integration Feign 加载 |
| **P2** | 端点表格、响应 JsonPath 助手、OpenAPI 导入、试调 Tab |
| **P3** | Pipeline 可视化、版本 diff、回归用例、Transform 配置 |

---

## 9. 线框（详情页）

```text
┌─────────────────────────────────────────────────────────────┐
│ 第三方对接 · DEMO_AKSK                    [保存草稿] [发布]   │
├──────────┬──────────┬──────────┬──────────┬─────────────────┤
│ 基础信息 │ 认证配置 │ 接口响应 │ 试调     │                 │
├──────────┴──────────┴──────────┴──────────┴─────────────────┤
│ 认证方式: [ AK/SK 规范串 (HMAC-SHA256) ▼ ]     级别: L1      │
│ Access Key: [****************]  Secret: [******] [显示]    │
│ ▶ 高级参数  ▶ Pipeline  ▶ YAML 预览                          │
├─────────────────────────────────────────────────────────────┤
│                                    [上一步]  [下一步]        │
└─────────────────────────────────────────────────────────────┘
```

---

## 10. 参考

- 运行时 Spec 模型：`ConnectorSpec` / `EndpointSpec`
- 已实现 Profile：`aksk_hmac_sha256_v1`（见 `demo-aksk.yaml`）
- 代理 API：`POST /api/v1/integrations/{code3rd}/proxy`
