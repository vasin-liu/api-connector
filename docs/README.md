# ITS Integration Platform 文档

本目录包含 **its-integration**（ITS 第三方通用对接平台）的全部设计与开发文档。

## 文档索引

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 总体架构、模块职责、依赖规则 |
| [DEVELOPER.md](./DEVELOPER.md) | 本地构建、运行、扩展 Profile |
| [profile-registry.md](./profile-registry.md) | 认证 Profile 注册表（A1–A28） |
| [UI-CONFIG-SPEC.md](./UI-CONFIG-SPEC.md) | 管理端 UI 向导、动态表单、试调与聚合 API |
| [UI-MODULE.md](./UI-MODULE.md) | 独立 `its-integration-ui` 模块、同 JAR 同端口部署 |
| [CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md) | 独立持久化 DDL 与管理 API |
| [THIRDPART-MIGRATION.md](./THIRDPART-MIGRATION.md) | 从 system-thirdpart 迁移到 Connector Spec |
| [API-STYLE.md](./API-STYLE.md) | 运行时 Invoke API 约定与 OpenAPI 阅读顺序 |
| [openapi-import.md](./openapi-import.md) | OpenAPI 导入与 Connector Spec 映射规则 |
| [DEPENDENCIES.md](./DEPENDENCIES.md) | 依赖与 BOM 版本清单 |
| [adr/001-platform-overview.md](./adr/001-platform-overview.md) | ADR-001 平台定位与技术选型 |
| [adr/002-auth-and-transform.md](./adr/002-auth-and-transform.md) | ADR-002 认证引擎与载荷变换 |

## 模块结构

```
its-integration/
├── its-integration-dependencies   # BOM
├── its-integration-parent         # 父 POM / 插件
├── its-integration-domain           # 领域模型（无 Spring）
├── its-integration-spec             # Connector YAML 解析
├── its-integration-auth             # Auth Profile / Pipeline
├── its-integration-engine           # 编排 + HTTP 传输
├── its-integration-connectors       # 内置厂家 Java Catalog（代码即 Spec + OpenAPI）
├── its-integration-api              # REST API + 控制台 BFF
├── its-integration-ui               # Vue 控制台（静态资源 JAR）
├── its-integration-persistence      # 独立 JDBC 配置存储
├── its-integration-app              # Spring Boot 启动（依赖 ui，单端口）
└── docs/                            # 本目录
```

## 快速启动

```bash
cd D:\Work\99_Code\ITS\its-integration
mvn -q -pl its-integration-app -am package -DskipTests
java -jar its-integration-app/target/its-integration-app-1.0.0-SNAPSHOT.jar
```

管理控制台：`http://localhost:19090/console/`

Swagger：`http://localhost:19090/swagger-ui.html`

示例调用（推荐 endpointId）：

```bash
curl -X POST http://localhost:19090/api/v1/integrations/DEMO_NONE/endpoints/echoGet/invoke \
  -H "Content-Type: application/json" \
  -d "{}"
```

详见 [API-STYLE.md](./API-STYLE.md)。

## 定位

- **独立部署**：不依赖 `system-manage`、`system-thirdpart` 或其它 ITS 模块。
- **重写 thirdpart 能力**：以 Connector Spec + Auth Profile 替代「每厂家 Client/Controller」，见 [THIRDPART-MIGRATION.md](./THIRDPART-MIGRATION.md)。
- 凭证字段语义与历史 `ClientInfoDTO` 对齐（`appId` / `appSecret` / `publicKey`），便于从旧配置迁移。
