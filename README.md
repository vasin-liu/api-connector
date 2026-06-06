# its-integration

ITS 第三方 HTTP/多协议 **通用对接平台**（JDK 21）。

- **代码根目录:** `D:\Work\99_Code\ITS\its-integration`
- **文档目录:** [docs/README.md](docs/README.md)

## 模块

| 模块 | 说明 |
|------|------|
| `its-integration-dependencies` | BOM 版本管理 |
| `its-integration-parent` | 编译与插件约定 |
| `its-integration-domain` | 领域模型 |
| `its-integration-spec` | Connector Spec 解析 + Catalog 扫描 |
| `its-integration-connectors` | 内置厂家 Java Catalog |
| `its-integration-auth` | 认证引擎 |
| `its-integration-engine` | 编排与 HTTP 传输 |
| `its-integration-api` | REST API（运行时 + Admin BFF） |
| `its-integration-persistence` | JDBC 配置持久化 |
| `its-integration-ui` | Vue 控制台静态资源 |
| `its-integration-app` | 可运行应用 |

## 构建（需 JDK 21）

```bash
mvn clean package -DskipTests
```

## 运行

```bash
java -jar its-integration-app/target/its-integration-app-1.0.0-SNAPSHOT.jar
```

详见 [docs/DEVELOPER.md](docs/DEVELOPER.md)。
