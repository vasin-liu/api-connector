<!-- GSD:project-start source:PROJECT.md -->

## Project

**API Connector**

API Connector（`api-connector`）是 ITS 平台的第三方 API 集成中枢，用于替代旧模块 `system-thirdpart`。它通过声明式 connector 规格、可插拔认证、数据映射与可视化管理台，将 60+ 厂商/域的第三方 HTTP 接口统一接入并对外暴露。

调用方通过上游 API 网关访问本服务；本服务负责出站第三方认证、请求编排、数据映射与可观测性。目标是**完全兼容**旧模块的 URL、请求/响应格式与错误语义，在全部接口迁移完成后一次性切换，调用方无感知。

**Core Value:** **在零感知替换 `system-thirdpart` 的前提下，让任意第三方 API 的接入、认证、映射与运维可通过配置（及必要时的 Groovy 扩展）完成，而无需为每个厂商手写 Controller。**

### Constraints

- **Tech stack**: Java 21 + Spring Boot 4 后端；React + Ant Design 前端 — 团队指定，Vue 不继续使用
- **Compatibility**: 旧 `system-thirdpart` 对外 HTTP 契约必须完全兼容 — 生产切换硬性要求
- **Migration**: 全部接口就绪后一次性切换 — 不接受长期双轨并行作为终态
- **Auth boundary**: 调用方鉴权由上游 API 网关处理；本服务提供 endpoint 鉴权元数据并专注出站第三方认证
- **Deployment**: 单 Fat JAR、单端口同时提供 API 与管理台 — 延续现有部署模式
- **Independence**: 禁止依赖 `system-thirdpart` 及 suntek-system 旧模块代码

<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Languages

- Java 21 — All application code across 11 Maven modules (`pom.xml`, `api-connector-parent/pom.xml`)
- JavaScript / Vue 3 — Admin console in `api-connector-ui/frontend/`
- SQL — Schema in `api-connector-persistence/src/main/resources/db/schema.sql`
- YAML — Optional connector specs via `ConnectorSpecLoader` (`api-connector-spec/`)

## Runtime

- JDK 21 (Zulu recommended) — `mvnw-jdk21.ps1` pins `JAVA_HOME` and delegates to `mvnw.cmd`
- Spring Boot 4.0.6 — Runnable fat JAR from `api-connector-app`
- Virtual threads enabled — `spring.threads.virtual.enabled: true` in `api-connector-app/src/main/resources/application.yml`
- Default HTTP port: `19090`
- Maven 3.9.11 — `.mvn/wrapper/maven-wrapper.properties`
- npm 10.8.2 / Node v20.18.1 — `api-connector-ui/pom.xml` via `frontend-maven-plugin`
- Lockfile: `api-connector-ui/frontend/package-lock.json`

## Frameworks

- Spring Boot 4.0.6 — Web, JSON, Validation, Actuator, Security, JDBC (`api-connector-dependencies/pom.xml`)
- Spring Cloud 2025.1.0 (Oakwood) — BOM imported only; no Spring Cloud starters in use yet
- Vue 3.5.13 + Vue Router 4.5.0 — `api-connector-ui/frontend/package.json`
- Vite 6.0.5 — `api-connector-ui/frontend/vite.config.js` (`base: '/console/'`)
- JUnit 5 (Jupiter) — All `*Test.java` files
- Spring Boot Test — `api-connector-app`, `api-connector-api` integration tests
- WireMock 3.13.1 (standalone, test scope) — `api-connector-app/pom.xml`
- AssertJ — Used alongside JUnit in spec and connector tests
- Maven Wrapper (`mvnw`, `mvnw.cmd`, `mvnw-jdk21.ps1`)
- flatten-maven-plugin 1.7.3 — CI-friendly `${revision}` versioning in root `pom.xml`
- frontend-maven-plugin 1.15.1 — Builds Vue assets into `api-connector-ui` JAR
- spring-boot-maven-plugin 4.0.6 — Fat JAR repackage in `api-connector-app`
- springdoc-openapi 3.0.3 — OpenAPI/Swagger UI at `/v3/api-docs`, `/swagger-ui.html`

## Key Dependencies

- SnakeYAML 2.4 — Connector spec YAML parsing (`api-connector-spec/pom.xml`)
- Jayway JsonPath 2.10.0 — Response evaluation (`api-connector-engine/pom.xml`)
- BouncyCastle bcprov-jdk18on 1.80 — Gaode traffic HMAC and planned 国密 profiles (`api-connector-auth/pom.xml`)
- Jackson databind — JSON serialization in auth and persistence modules
- Lombok 1.18.44 — API DTOs (`api-connector-dependencies/pom.xml`)
- JDK HttpClient — Default HTTP transport via `JdkHttpTransport` (`api-connector-engine/.../transport/JdkHttpTransport.java`); no Feign/WebClient
- H2 Database (runtime) — Embedded file DB default in `application.yml`
- Spring JDBC — `JdbcConnectorConfigStore` in `api-connector-persistence`

## Configuration

- `application.yml` — Core app config in `api-connector-app/src/main/resources/`
- `application-prod.yml` — Production overrides (security enabled, rate limits)
- Per-connector env vars: `{CODE3RD}_APP_ID`, `{CODE3RD}_APP_SECRET`, `{CODE3RD}_PUBLIC_KEY`, `{CODE3RD}_BASE_URL` — `ConnectorBootstrapConfiguration.java`
- `integration.persistence.mode` — `classpath` | `memory` | `jdbc` | `composite` (default)
- `integration.security.*` — Optional API key auth (`integration.security.enabled`, key lists)
- `integration.invoke.*` — Platform HTTP status mapping, audit, rate limits
- `api-connector-dependencies/pom.xml` — Central BOM for all dependency versions
- `api-connector-parent/pom.xml` — Compiler (`release 21`), Surefire, resource plugin conventions
- `.mvn/jvm.config` — `--enable-native-access=ALL-UNNAMED`
- `skip.ui` property — Skip Vue build in `api-connector-ui/pom.xml`

## Platform Requirements

- Windows/Linux/macOS with JDK 21
- Node.js 20.x for UI development (or let Maven `frontend-maven-plugin` install it)
- Build: `.\mvnw-jdk21.ps1 clean verify` (with tests) or `.\mvnw-jdk21.ps1 clean package -DskipTests`
- JDK 21 runtime
- Deployable as single Spring Boot fat JAR (`api-connector-app`)
- Default embedded H2 suitable for dev only; production should use MySQL/PostgreSQL via `spring.datasource.*` (driver added at deploy time)
- Vue console served as static assets from `/console/` inside the same JAR

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Naming Patterns

- Java classes: `PascalCase.java` (e.g. `ConnectorRegistry.java`, `AkskCanonicalSigner.java`)
- Test files: `{ClassName}Test.java` in parallel `src/test/java/` tree mirroring main package
- Vue views: `PascalCase.vue` (e.g. `ConnectorList.vue`, `ConnectorEditor.vue`)
- Vue utilities: `camelCase.js` (e.g. `queryParams.js`, `clientSettings.js`)
- camelCase for all Java methods (e.g. `require()`, `apply()`, `invoke()`)
- Catalog endpoint methods: camelCase → becomes `endpointId` (e.g. `roadSpeeds()`)
- Event handlers in Vue: standard Vue conventions
- camelCase for Java fields and local variables
- UPPER_SNAKE_CASE for connector codes (`IDPS`, `BAIDU_WENXIN`) and Java constants
- `record` types for immutable value objects (e.g. `ResponseEvaluation.java`)
- PascalCase for classes, interfaces, records, enums — no `I` prefix on interfaces
- Domain/spec models: `final` classes with accessors, not Lombok — `ConnectorSpec.java`, `ConnectorCode.java`
- API DTOs: Lombok `@Data` / `@Builder` — `ProxyInvokeRequest.java`, `ApiErrorResponse.java`
- Utility classes: `public final` with private constructor — `AkskCanonicalSigner.java`

## Code Style

- JDK 21 with `maven.compiler.release=21` — `api-connector-parent/pom.xml`
- UTF-8 source encoding — root `pom.xml`
- Compiler flags: `-Xlint:unchecked`, `-parameters` — `api-connector-parent/pom.xml`
- No Prettier/Checkstyle/Spotless configured in repo
- Text blocks for JSON in tests — `ResponseEvaluatorTest.java`
- No ESLint for Vue frontend
- Maven compiler warnings only (`-Xlint:unchecked`)
- Run full verify: `.\mvnw-jdk21.ps1 clean verify`

## Import Organization

- No enforced blank-line grouping; follows typical Java IDE defaults
- Module boundaries enforced by Maven POM dependencies, not package imports
- None in Java (standard package structure)
- Vue: relative imports within `frontend/src/`

## Error Handling

- Validation / not-found: `IllegalArgumentException` with descriptive message — `ConnectorRegistry.require()`, `EndpointResolver.java`
- I/O / crypto / upstream failures: `IllegalStateException` wrapping cause — `JdkHttpTransport.java`, `JdbcConnectorConfigStore.java`
- Rate limiting: dedicated `InvokeRateLimitException` — `api-connector-api/.../invoke/InvokeRateLimitException.java`
- HTTP mapping via `@RestControllerAdvice` — `RuntimeApiExceptionHandler.java`, `AdminApiExceptionHandler.java`
- Throw on invalid input, missing connector/endpoint, unknown auth profile
- Platform errors returned as `{ "code": "...", "message": "..." }` — `ApiErrorResponse.java`
- Documented error codes: `CONNECTOR_NOT_FOUND`, `ENDPOINT_NOT_FOUND`, `STRICT_ENDPOINTS`, `RATE_LIMITED`, `UPSTREAM_AUTH_FAILED` — `docs/API-STYLE.md`
- Vendor HTTP status preserved in invoke response body, separate from platform status

## Logging

- SLF4J via `LoggerFactory.getLogger()` — not `@Slf4j` Lombok annotation in sampled production code
- Named audit logger: `LoggerFactory.getLogger("integration.invoke.audit")` — `InvokeAuditLogger.java`
- Structured key=value audit logging for invoke events
- Persistence sync logs in `ConnectorConfigSyncService.java`
- Engine/auth layers mostly throw rather than log errors
- Toggle audit: `integration.invoke.audit-enabled` in `application.yml`

## Comments

- All `public` classes and methods require Javadoc with `@param` / `@return` — per `docs/DEVELOPER.md`
- PCI copyright header on every Java file (2021–2026)
- `@author Gensokyo`, `@version`, `@since` on core types
- Catalog endpoint comments in Chinese describing vendor API purpose — `IdpsConnectorCatalog.java`
- Core domain Javadocs in English — `ConnectorSpec.java`, `ConnectorCode.java`
- Required for public Java API per `docs/DEVELOPER.md`
- Vue components: minimal inline comments; UI spec in `docs/UI-CONFIG-SPEC.md`
- No `TODO` or `FIXME` markers found in Java sources (as of mapping date)

## Function Design

- Orchestrator and invoke service methods are moderate length; auth providers are focused single-responsibility classes
- Utility extraction in `auth/support/` (e.g. `AkskCanonicalSigner.java`)
- Domain records (`InvocationRequest`, `AuthContext`) bundle related parameters
- AuthProvider `apply(AuthContext)` single-parameter pattern
- Immutable records for results (`InvocationResult`, `AuthOutcome`, `ResponseEvaluation`)
- Explicit exception throws for failure paths

## Module Design

- Package-private test classes (`class FooTest`, not `public`)
- SPI interfaces in `domain.spi` and `auth.spi` for extension points
- Spring `@Configuration` classes wire beans in `engine/config/` and `app/config/`
- `domain` → no Spring
- `spec` → domain only
- `auth` → domain, spec
- `engine` → domain, spec, auth, spring-context
- `api` → engine, spring-web (not direct auth)
- `app` → api, ui, persistence, spring-boot
- Documented in `docs/ARCHITECTURE.md` dependency table
- Connectors defined as annotated Java interfaces, not YAML — preferred for production per `docs/API-STYLE.md`
- Endpoint metadata derived by reflection in `CatalogConnectorScanner.java`

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## Pattern Overview

- Framework-free domain core (`api-connector-domain`) with SPI ports
- Declarative connector specs (Java Catalog + optional YAML + JDBC)
- Single Spring Boot fat JAR with unified runtime + admin UI
- Pluggable auth profiles and HTTP transport via SPI
- Spec-driven proxy API — clients invoke by `code3rd` + `endpointId`, not raw URLs

## Layers

- Purpose: Framework-agnostic models and SPI contracts
- Contains: `InvocationRequest`, `InvocationResult`, `ConnectorCode`, `IntegrationOrchestrator`, `StreamingInvocationSink`
- Location: `api-connector-domain/src/main/java/com/suntek/apiconnector/domain/`
- Depends on: Nothing (no Spring)
- Used by: spec, auth, engine, api
- Purpose: ConnectorSpec model, YAML parsing, Java Catalog scanning
- Contains: `ConnectorSpec`, `EndpointSpec`, `CatalogConnectorScanner`, `ConnectorSpecLoader`
- Location: `api-connector-spec/src/main/java/com/suntek/apiconnector/spec/`
- Depends on: domain
- Used by: auth, engine, connectors, app bootstrap
- Purpose: Authentication profile engine — sign requests, inject tokens/headers
- Contains: `AuthEngine`, `AuthProvider` SPI, profile implementations
- Location: `api-connector-auth/src/main/java/com/suntek/apiconnector/auth/`
- Depends on: domain, spec
- Used by: engine
- Purpose: Orchestration, HTTP transport, connector registry, endpoint resolution
- Contains: `DefaultIntegrationOrchestrator`, `ConnectorRegistry`, `JdkHttpTransport`, `ResponseEvaluator`
- Location: `api-connector-engine/src/main/java/com/suntek/apiconnector/engine/`
- Depends on: domain, spec, auth, spring-context
- Used by: api
- Purpose: REST controllers, DTOs, admin BFF, legacy compat, OpenAPI
- Contains: `IntegrationProxyController`, `IntegrationAdminController`, invoke services
- Location: `api-connector-api/src/main/java/com/suntek/apiconnector/api/`
- Depends on: engine, spring-web (not direct auth)
- Used by: app
- Purpose: JDBC-backed connector config store and sync
- Contains: `JdbcConnectorConfigStore`, `ConnectorConfigSyncService`
- Location: `api-connector-persistence/src/main/java/com/suntek/apiconnector/persistence/`
- Depends on: engine store port, spring-jdbc
- Used by: app
- Purpose: Built-in vendor Java Catalog definitions
- Contains: `BuiltinConnectorCatalogs`, per-vendor `*ConnectorCatalog.java` interfaces
- Location: `api-connector-connectors/src/main/java/com/suntek/apiconnector/connectors/`
- Depends on: spec
- Used by: app bootstrap
- Purpose: Spring Boot entry, security, connector bootstrap, fat JAR assembly
- Location: `api-connector-app/src/main/java/com/suntek/apiconnector/app/`
- Depends on: api, ui, persistence, spring-boot
- Purpose: Vue admin console served at `/console/`
- Location: `api-connector-ui/frontend/` → `static/console/` in JAR

## Data Flow

- `IntegrationProxyController.streamEndpoint` → `IntegrationOrchestrator.invokeStream` → `HttpTransport.exchangeStream` → `ServletStreamingInvocationSink`
- `ConnectorBootstrapConfiguration.java` scans Java Catalogs, loads classpath YAML, injects env credentials
- On `ApplicationReadyEvent`, JDBC sync reloads published configs into registry — `ConnectorConfigSyncService.java`
- In-memory `ConnectorRegistry` (ConcurrentHashMap) — spec + credentials + status per `code3rd`
- JDBC persistence for published connector configs — `JdbcConnectorConfigStore.java`
- OAuth token cache in-memory per auth provider
- No distributed state; single-instance assumption

## Key Abstractions

- Purpose: Domain port for end-to-end invoke orchestration
- Examples: `domain/spi/IntegrationOrchestrator.java` ← `engine/DefaultIntegrationOrchestrator.java`
- Pattern: SPI with single default implementation
- Purpose: Pluggable outbound authentication profile
- Examples: `auth/spi/AuthProvider.java`, `auth/profile/*AuthProvider.java`
- Pattern: Registry keyed by `profileType()`; wired via `IntegrationEngineConfiguration.java`
- Purpose: Pluggable HTTP client abstraction
- Examples: `engine/transport/HttpTransport.java`, `JdkHttpTransport.java`
- Pattern: Interface with JDK default; replaceable bean
- Purpose: Runtime in-memory store of connector specs and credentials
- Examples: `engine/ConnectorRegistry.java`
- Pattern: Mutable registry; loaded from Catalog + YAML + JDBC sync
- Purpose: Persistence port for connector configs
- Examples: `engine/store/ConnectorConfigStore.java` ← `persistence/jdbc/JdbcConnectorConfigStore.java`
- Pattern: Hexagonal port/adapter
- Purpose: Declarative connector definition (endpoints, auth, response rules)
- Examples: `spec/model/ConnectorSpec.java`, `connectors/idps/IdpsConnectorCatalog.java`
- Pattern: Annotated interface methods → scanned endpoints via `CatalogConnectorScanner.java`

## Entry Points

- Location: `api-connector-app/src/main/java/com/suntek/apiconnector/app/IntegrationApplication.java`
- Triggers: `java -jar api-connector-app.jar` or Maven `spring-boot:run`
- Responsibilities: Component scan `com.suntek.apiconnector`, auto-configure all modules
- Location: `api-connector-api/.../api/controller/IntegrationProxyController.java`
- Triggers: HTTP POST from clients or legacy compat filter
- Responsibilities: Request validation, delegate to `IntegrationInvokeService`
- Location: `api-connector-api/.../api/admin/IntegrationAdminController.java`
- Triggers: HTTP from Vue console at `/console/`
- Responsibilities: Connector CRUD, publish, credential management

## Error Handling

- `IllegalArgumentException` → 400/404 with codes `CONNECTOR_NOT_FOUND`, `ENDPOINT_NOT_FOUND`, `STRICT_ENDPOINTS` — `RuntimeApiExceptionHandler.java`
- `InvokeRateLimitException` → 429 `RATE_LIMITED`
- `IllegalStateException` (missing AuthProvider) → 502 `UPSTREAM_AUTH_FAILED`
- Admin errors → 400 `BAD_REQUEST` — `AdminApiExceptionHandler.java`
- Error payload: `{ "code": "...", "message": "..." }` — `ApiErrorResponse.java`
- Vendor HTTP status preserved separately in invoke response body; platform status mapped via config

## Cross-Cutting Concerns

- SLF4J `LoggerFactory` in production code
- Structured invoke audit via `InvokeAuditLogger.java` (`integration.invoke.audit` logger)
- Toggle: `integration.invoke.audit-enabled` in `application.yml`
- Spring Validation on request DTOs
- Business rules via `IllegalArgumentException` in engine (registry lookup, endpoint resolution)
- Strict endpoints mode for catalog-managed connectors
- Outbound: AuthProvider profiles per connector spec
- Inbound: Optional API key filter (`IntegrationApiKeyFilter.java`); disabled by default
- Legacy URL compat exposed without auth when security disabled
- Per-`code3rd` per-minute limit — `InvokeRateLimiter.java`
- Disabled by default (`rate-limit-per-code3rd-per-minute: 0`)

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
