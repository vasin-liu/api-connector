# api-connector

ITS 出站 HTTP **Flow / Protocol Execution Engine**（JDK 21，V2.7 Phase 0）。

产品是 in-process Runtime，不是 `system-thirdpart` 接入中枢。对外宿主 API 是 `com.suntek.apiconnector.core.api.ApiClient`（`execute` / `cancel`）。没有 Fat JAR 管理台、没有 `/integrations/{code3rd}` 代理 API。

- Runtime 合同：[docs/design/api-connector-design-v2_7.md](docs/design/api-connector-design-v2_7.md)
- 绿场规格包：[docs/design/v2.7-greenfield/](docs/design/v2.7-greenfield/README.md)
- OpenSpec 基线：`openspec/specs/`
- 平台笔记（JSONPath / GraalVM / CookieStore / planId）：[api-connector-config/README.md](api-connector-config/README.md)

## 模块

| 模块 | 说明 |
|------|------|
| `api-connector-dependencies` | BOM 版本管理 |
| `api-connector-parent` | 编译与插件约定 |
| `api-connector-core` | 宿主类型（`ApiClient`、snapshot / result），无 Spring |
| `api-connector-runtime` | Flow 编译与执行、Session、Pipeline、Script |
| `api-connector-transport` | HTTP transport |
| `api-connector-config` | Definition 装载与平台笔记 |

## 构建（JDK 21+）

Windows（推荐项目 Wrapper）：

```powershell
.\mvnw-jdk21.ps1 -B -pl api-connector-core,api-connector-transport,api-connector-runtime,api-connector-config -am test
```

`mvnw-jdk21.ps1` 会绑定本机 JDK 21。CI（`.github/workflows/v2-7-runtime.yml`）在 Temurin 21 上跑同一组模块，并设置 `MAVEN_OPTS=--enable-native-access=ALL-UNNAMED`。

Unix：

```bash
./mvnw -B -pl api-connector-core,api-connector-transport,api-connector-runtime,api-connector-config -am test
```

## 历史文档

下列入口描述 **pre-V2.7 中枢**（已删除的 11 模块 / Vue 控制台 / 代理 API），仅作考古，不是当前工作：

- [docs/README.md](docs/README.md)
- [docs/DEVELOPER.md](docs/DEVELOPER.md)
