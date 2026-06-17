# system-thirdpart 能力迁移指南

本文档说明如何将 `suntek-system/system-thirdpart` 中「每厂家一套 Client/Controller」的实现，迁移到 **api-connector** 独立平台的 **Connector Spec + Auth Profile** 模型。

## 1. 原则

| 旧模式 (thirdpart) | 新模式 (api-connector) |
|--------------------|---------------------------|
| `XxxClient extends BaseClient` | `connectors/{code3rd}.yaml` + 凭证 |
| `XxxController` 暴露业务 API | 统一 Invoke API（见 [API-STYLE.md](./API-STYLE.md)） |
| 认证散落在 Client 内 | `auth.type` 或 `auth.pipeline` |
| 配置来自 system-manage Feign | **本应用内嵌 H2** 或外接 JDBC |
| 与 system-manage / SDK 耦合 | **零耦合**，可单独部署 |

## 2. 迁移步骤（新厂家 / 存量厂家）

1. **识别认证 Profile** — 对照 [profile-registry.md](./profile-registry.md)，优先 L1/L2 YAML。
2. **编写 Connector Spec** — 内置厂家用 Java Catalog（`api-connector-connectors`）；自定义/试验性可用 YAML 或控制台 JSON（仅 id/method/path，文档自动推导）。
3. **登记端点** — 将原 Client 中 `get/post` 路径整理为 `endpoints[]`。
4. **控制台发布** — `http://localhost:19090/console/` 填写凭证并发布。
5. **业务侧改调用** — 由直连 thirdpart 改为调用 integration Invoke API（见 [API-STYLE.md](./API-STYLE.md)）。
6. **L3 场景** — 大华多步登录、海康 SDK、讯飞 WebSocket 等保留 Java SPI（见 ADR-002）。

## 3. 厂家包 → Profile 映射（摘要）

| thirdpart 包 | 建议 Profile | 模板/备注 |
|--------------|--------------|-----------|
| idps, traffic, trafficDf | `aksk_hmac_sha256_v1` | `template-idps.yaml` |
| gaode | `api_key_query` | `template-gaode.yaml` |
| llm (chatgpt, transgpt) | `bearer_static` | token → appSecret |
| xinShiQi, meiya (token) | `oauth2_client_credentials` | 配置 tokenUrl |
| changchun | `custom_headers` | 待实现 Profile |
| keytop | `md5_body_sign_sorted_v1` | P1 |
| keda | `oauth2_password` | P1 |
| hikvision | `hikvision_artemis_sdk_v1` | L3 SPI |
| trafficDaHua | `multi_step_login_dahua_v1` | L3 SPI |
| tts/iflytek | `ws_iflytek_hmac_v1` | L3 SPI |
| cetc | `sm4_body_encrypt_v1` | L2 |
| yuhaoban | `bearer_plus_request_sign_v1` | L2 |
| publicservice | `cookie_session_v1` | L3 |

完整 50+ 包清单见 thirdpart `client/` 目录；按上表分批迁移，**先 HTTP+标准认证，后 SDK/多步登录**。

## 4. 统一调用示例

原 thirdpart：

```java
idpsClient.getRequest("/brain-auth/...", params);
```

新 integration（**推荐**：Spec 已登记端点时）：

```bash
curl -X POST http://localhost:19090/api/v1/integrations/MY_IDPS/endpoints/{endpointId}/invoke \
  -H "Content-Type: application/json" \
  -d '{"query":{"loginName":"..."}}'
```

通用 invoke（未登记 path 或临时试调）：

```bash
curl -X POST http://localhost:19090/api/v1/integrations/MY_IDPS/invoke \
  -H "Content-Type: application/json" \
  -d '{"method":"GET","path":"/brain-auth/...","query":{"loginName":"..."}}'
```

> `POST .../proxy` 已废弃，与 `/invoke` 等价，请勿在新代码中使用。

## 5. 不建议迁移到本平台的场景

- 强业务编排（多 Client 组合、本地文件批处理）— 保留业务服务内编排，仅将 **对外 HTTP** 部分交给 integration。
- 仅 SDK、无 HTTP 的厂家 — 使用 L3 SPI 模块扩展，而非强行 YAML 化。

## 7. 已落地 Connector（IDPS / 高德 / 百度）

| code3rd | 配置文件 | 认证 | baseUrl（需按环境改） |
|---------|----------|------|------------------------|
| `IDPS` | `connectors/idps/IdpsConnectorCatalog.java` | `aksk_hmac_sha256_v1` | 大脑网关地址 |
| `GAODE_OPEN_PLATFORM` | `connectors/gaode/GaodeOpenPlatformConnectorCatalog.java` | `api_key_query` (key) | `https://restapi.amap.com` |
| `GAODE_TRAFFIC` | `connectors/gaode/GaodeTrafficConnectorCatalog.java` | `gaode_traffic_hmac_v1` | `https://et-api.amap.com` |
| `BAIDU_MAP` | `connectors/baidu/BaiduMapConnectorCatalog.java` | `api_key_query` (ak) | `https://api.map.baidu.com` |
| `BaiduGpt`（别名） / `BAIDU_WENXIN`（规范） | `connectors/baidu/BaiduWenxinConnectorCatalog.java` | `oauth2_token_in_query` | `https://aip.baidubce.com` |

### 凭证与环境变量

| code3rd | publicKey | appId | appSecret |
|---------|-----------|-------|-----------|
| IDPS | AccessKey | — | SecretKey |
| GAODE_OPEN_PLATFORM | Web Key | — | — |
| GAODE_TRAFFIC | clientKey | — | 签名密钥 |
| BAIDU_MAP | — | AK | — |
| BaiduGpt / BAIDU_WENXIN | — | API Key | Secret Key |

启动时可通过 `{CODE3RD}_PUBLIC_KEY` / `_APP_ID` / `_APP_SECRET` 注入；或在控制台发布时填写。

### 调用示例

**IDPS 路段速度**（endpointId 见 `IdpsConnectorCatalog.java`）

```bash
curl -X POST http://localhost:19090/api/v1/integrations/IDPS/endpoints/roadSpeeds/invoke \
  -H "Content-Type: application/json" \
  -d '{"query":{"roadclid":"xxx","from_time":"2026-01-01 00:00:00","to_time":"2026-01-01 23:59:59"}}'
```

**高德开放平台 矩形路况**

```bash
curl -X POST http://localhost:19090/api/v1/integrations/GAODE_OPEN_PLATFORM/endpoints/trafficStatusRectangle/invoke \
  -H "Content-Type: application/json" \
  -d '{"query":{"rectangle":"116.0,39.0;117.0,40.0","level":"6","extensions":"all"}}'
```

**百度地图 地点提示**

```bash
curl -X POST http://localhost:19090/api/v1/integrations/BAIDU_MAP/endpoints/placeSuggestion/invoke \
  -H "Content-Type: application/json" \
  -d '{"query":{"query":"机场","region":"广州","output":"json"}}'
```

**百度文心 对话**

```bash
curl -X POST http://localhost:19090/api/v1/integrations/BAIDU_WENXIN/endpoints/chatCompletionsPro/invoke \
  -H "Content-Type: application/json" \
  -d '{"body":"{\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"}'
```

查看全部端点：`GET /api/v1/integrations/{code3rd}/endpoints`

## 6. 当前实现状态

| 能力 | 状态 |
|------|------|
| 独立 H2 持久化 | 已实现 |
| none / aksk / bearer / api_key / oauth2_cc | 已实现 |
| 控制台 CRUD + 发布 | 已实现 |
| 其余 Profile | 按 profile-registry 排期 |
| thirdpart Controller 兼容层 | **已实现**：`LegacyCompatFilter` 按 `integration.legacy.routes` 最长前缀匹配；默认 `/idps`（透传）、`/gaode`/`/gaode/traffic`/`/baiduJiaotong`/`/BaiduGpt`（`Result` 包装）。见 [API-STYLE.md](./API-STYLE.md) |
