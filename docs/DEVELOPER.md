# 开发者指南

## 环境要求

- JDK **21+**（必须；`JAVA_HOME` 指向 JDK 21，本工程 `maven.compiler.release=21`）
- Maven **3.9+**
- 可选：Docker（后续 WireMock 契约测试）

```powershell
# 示例：切换 JAVA_HOME 后再构建（路径按本机 JDK 安装调整）
$env:JAVA_HOME = "E:\Home\vasin.GENSOKYO\sdk\zulu-jdk21.0.9"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java -version
mvn clean package -DskipTests
```

## 构建

```bash
# 根目录
mvn clean verify

# 仅打包可运行应用
mvn -pl its-integration-app -am package -DskipTests
```

## 本地运行

```bash
java -jar its-integration-app/target/its-integration-app-1.0.0-SNAPSHOT.jar
```

默认端口 `19090`。示例连接器 `DEMO_NONE` 使用 https://httpbin.org（需外网）。

### 环境变量凭证

启动前可为连接器注入凭证（与 `ConnectorBootstrapConfiguration` 约定）：

```text
DEMO_NONE_APP_ID=
DEMO_NONE_APP_SECRET=
DEMO_NONE_PUBLIC_KEY=
```

## 新增连接器（L1 配置）

1. 在 `its-integration-app/src/main/resources/connectors/` 新增 `xxx.yaml`
2. 参考 [profile-registry.md](./profile-registry.md) 选择 `auth.type`
3. 重启应用或调用后续将提供的「热加载」API

## 已内置 Auth Profile

| Profile ID | 状态 |
|------------|------|
| `none` | 已实现 |
| `aksk_hmac_sha256_v1` | 已实现（IDPS/Traffic 规范） |

## 新增 Auth Profile（L2 代码）

1. 在 `its-integration-auth` 下实现 `AuthProvider`
2. `profileType()` 返回注册表 ID（如 `oauth2_client_credentials`）
3. 声明为 Spring `@Bean` 或由 `IntegrationEngineConfiguration` 扫描注册
4. 补充单元测试与 [profile-registry.md](./profile-registry.md) 条目

## 新增 SPI（L3）

满足 [adr/002-auth-and-transform.md](./adr/002-auth-and-transform.md) 准入标准时再新增 `custom_spi` 实现。

## 模块职责速查

| 模块 | 放什么 |
|------|--------|
| domain | Record、SPI 接口，无框架 |
| spec | YAML ↔ ConnectorSpec |
| auth | AuthEngine、AuthProvider |
| engine | Orchestrator、HttpTransport、Registry |
| api | Controller、DTO |
| app | `main`、配置文件、示例 YAML |

## 代码规范

- 所有 `public` 类/方法需 Javadoc（含 `@param` / `@return`）
- 文件头使用公司版权块（见现有 Java 文件）
- 非显而易见逻辑用 `//` 注释

## 管理控制台（`its-integration-ui`）

| 文档 | 内容 |
|------|------|
| [UI-MODULE.md](./UI-MODULE.md) | 独立模块、Maven+npm 构建、同端口部署 |

```bash
# 前端热更新（另开终端启动 app）
cd its-integration-ui/frontend && npm install && npm run dev
```

访问：`http://localhost:19090/console/`

## 独立持久化

见 [CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md)、[THIRDPART-MIGRATION.md](./THIRDPART-MIGRATION.md)。

```yaml
# application.yml（默认 H2 文件库 + composite 配置源）
integration.persistence.source: composite
```

## 管理端 UI / 持久化

| 文档 | 内容 |
|------|------|
| [UI-CONFIG-SPEC.md](./UI-CONFIG-SPEC.md) | 四步向导、Profile 动态表单、试调 |
| [CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md) | `IT_CONNECTOR_SPEC` DDL、发布、本地 JDBC |
| [schemas/profiles-meta/](./schemas/profiles-meta/) | P1 Profile UI 元数据 JSON |

## 测试策略（规划）

| 层级 | 工具 |
|------|------|
| Profile 单元测试 | JUnit 5 |
| HTTP 契约 | WireMock |
| 端到端 | Testcontainers + 厂家沙箱 |
