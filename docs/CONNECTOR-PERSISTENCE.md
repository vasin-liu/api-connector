# 连接器持久化（独立应用）

`its-integration` **不依赖** `system-manage` 或 `system-thirdpart`。配置保存在本应用数据库（默认 H2 文件 `./data/integration`），运行时由 `ConnectorRegistry` 热加载。

## 1. 数据模型

| 表 | 用途 |
|----|------|
| `IT_CONNECTOR_CLIENT` | 连接信息：code3rd、host、appId、appSecret、publicKey、代理等 |
| `IT_CONNECTOR_SPEC` | 协议行为：SPEC_JSON（与 YAML 同构）、发布状态、版本 |

DDL：`its-integration-persistence/src/main/resources/db/schema.sql`

## 2. 配置源 `integration.persistence.source`

| 值 | 说明 |
|----|------|
| `classpath` | 仅加载 `connectors/*.yaml`（跳过 `template-*`） |
| `memory` | 仅控制台内存（不启用 JDBC Store） |
| `jdbc` | 仅 DB 已发布项 |
| `composite` | **推荐**：classpath 示例 + DB 已发布（同 code3rd 时 DB 覆盖） |

## 3. 管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/admin/connectors` | 新建 |
| PUT | `/api/v1/admin/connectors/{code3rd}` | 更新 |
| POST | `/api/v1/admin/connectors/{code3rd}/publish` | 发布 |
| DELETE | `/api/v1/admin/connectors/{code3rd}` | 删除 |
| POST | `/api/v1/admin/sync` | DB → 注册表全量刷新 |

保存/发布时：**同时**写入注册表与 JDBC（当 Store Bean 存在时）。

## 4. 外接 MySQL / PostgreSQL

修改 `application.yml` 中 `spring.datasource.*`，保持 `integration.persistence.source=jdbc|composite` 即可。表结构见 schema.sql（MySQL 方言，H2 使用 MODE=MySQL）。

## 5. 凭证安全

- 凭证仅存服务端 DB，API 返回掩码视图。
- 生产环境应对 `APP_SECRET` 等字段加密存储（待 P2 加密列）。
