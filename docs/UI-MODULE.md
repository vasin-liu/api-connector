# UI 独立模块与统一部署

## 模块

| 模块 | 职责 |
|------|------|
| `api-connector-ui` | Vue 3 + Vite 管理控制台；产出 `classpath:/static/console/**` JAR |
| `api-connector-app` | 依赖 `ui` + `api`，Spring Boot fat JAR **单端口** 发布 |

## 访问路径（默认端口 19090）

| 路径 | 说明 |
|------|------|
| `/` | 302 → `/console/` |
| `/console/` | 管理控制台 SPA |
| `/api/v1/integrations/{code3rd}/proxy` | 代理 API |
| `/api/v1/admin/*` | 控制台 BFF（连接器 CRUD、Profile 元数据） |
| `/swagger-ui.html` | OpenAPI |

## 构建

```bash
# 全量（含 npm 构建 UI，需网络下载 Node）
.\mvnw-jdk21.ps1 clean package -DskipTests

# 仅 Java，跳过前端（使用 ui 模块内占位 static）
.\mvnw-jdk21.ps1 clean package -DskipTests -Dskip.ui=true
```

## 前端本地开发

```bash
cd api-connector-ui/frontend
npm install
npm run dev
```

Vite 已将 `/api` 代理到 `http://localhost:19090`，需同时启动 Spring Boot。

## 打包原理

```
api-connector-ui.jar
  └── static/console/index.html, assets/*
           ↑
api-connector-app.jar (repackage)
  └── BOOT-INF/classes/static/console/**   ← prepare-package 解压 ui 静态资源
```

`prepare-package` 阶段由 `maven-dependency-plugin` 将 `api-connector-ui` 中的 `static/**` 解压到
`api-connector-app/target/classes`，再随 Spring Boot repackage 打入 fat JAR；ui 依赖 JAR 不再重复嵌套进 `BOOT-INF/lib`。

`UiWebConfiguration` 注册 `/console/**` 资源链，SPA 路由回退 `index.html`。

## 管理 API（已实现）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/connectors` | 列表 |
| GET | `/api/v1/admin/connectors/{code3rd}` | 详情（凭证脱敏） |
| POST | `/api/v1/admin/connectors` | 新建 |
| PUT | `/api/v1/admin/connectors/{code3rd}` | 保存草稿 |
| POST | `/api/v1/admin/connectors/{code3rd}/publish` | 发布 |
| DELETE | `/api/v1/admin/connectors/{code3rd}` | 删除 |
| GET | `/api/v1/admin/profiles` | Profile 元数据 |

保存后立即写入 `ConnectorRegistry`（热加载）；状态 `DRAFT` / `PUBLISHED` 仅作 UI 展示，代理 API 均可调用。

## 控制台功能（当前）

| 页面 | 路径 | 能力 |
|------|------|------|
| 连接器列表 | `/console/connectors` | 列表、从库刷新、删除（非 Catalog）、试调入口 |
| 编辑向导 | `/console/connectors/{code3rd}` | 基础/认证/端点；Catalog 连接器展示只读端点目录 |
| 试调 | `/console/trial/{code3rd}` | endpointId、**Query 参数表**（选端点后按 Catalog 预填）、body、自定义 path |
| 设置 | `/console/settings` | **Admin/Runtime API Key**；读取 `/api/v1/admin/console-info` 显示鉴权开关 |

开启 `integration.security.enabled=true` 时，请先在「设置」页填写 Admin API Key。

## 持久化

- **保存/发布**：写入本地 H2（见 [CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md)）并更新运行时 `ConnectorRegistry`。
- **刷新**：`POST /api/v1/admin/sync` 将 DB 中已发布配置热加载到注册表。
- 配置项见 `application.yml` → `integration.persistence.*`（[CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md)）。
