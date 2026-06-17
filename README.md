# api-connector

ITS 第三方 HTTP/多协议 **通用对接平台**（JDK 21）。

- **代码根目录:** `D:\Work\99_Code\01_Java\api-connector`
- **文档目录:** [docs/README.md](docs/README.md)

## 模块

| 模块 | 说明 |
|------|------|
| `api-connector-dependencies` | BOM 版本管理 |
| `api-connector-parent` | 编译与插件约定 |
| `api-connector-domain` | 领域模型 |
| `api-connector-spec` | Connector Spec 解析 + Catalog 扫描 |
| `api-connector-connectors` | 内置厂家 Java Catalog |
| `api-connector-auth` | 认证引擎 |
| `api-connector-engine` | 编排与 HTTP 传输 |
| `api-connector-api` | REST API（运行时 + Admin BFF） |
| `api-connector-persistence` | JDBC 配置持久化 |
| `api-connector-ui` | Vue 控制台静态资源 |
| `api-connector-app` | 可运行应用 |

## 构建（JDK 21+，推荐用项目自带 Wrapper）

```powershell
.\mvnw-jdk21.ps1 clean package -DskipTests
```

`mvnw-jdk21.ps1` 会绑定本机 JDK 21 与 `.mvn/settings-jdk21.xml`（与 `http-ingestion-service` 相同约定）。

## 运行

```bash
java -jar api-connector-app/target/api-connector-app-1.0.0-SNAPSHOT.jar
```

详见 [docs/DEVELOPER.md](docs/DEVELOPER.md)。
