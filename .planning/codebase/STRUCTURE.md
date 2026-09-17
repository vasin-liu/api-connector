---
last_mapped_commit: 9b5f5ce02dc1eb222a9a437d18f44861be53ccb6
last_mapped_at: 2026-06-17
---
# Codebase Structure

**Analysis Date:** 2026-06-17

## Directory Layout

```
api-connector/
├── api-connector-dependencies/   # BOM — Spring Boot 4, library versions
├── api-connector-parent/         # Compiler & Maven plugin conventions
├── api-connector-domain/         # Framework-free domain models + SPI
├── api-connector-spec/           # ConnectorSpec model, YAML loader, Catalog scanner
├── api-connector-auth/           # AuthEngine + AuthProvider profiles
├── api-connector-engine/         # Orchestrator, transport, registry, store port
├── api-connector-connectors/     # Built-in vendor Java Catalog connectors
├── api-connector-api/            # REST controllers, DTOs, admin BFF, legacy compat
├── api-connector-persistence/    # JDBC ConnectorConfigStore + sync jobs
├── api-connector-ui/               # Vue 3 console → static/console JAR resources
├── api-connector-app/            # Runnable Spring Boot fat JAR (composition root)
├── docs/                         # Architecture, ADRs, API style, migration guides
├── .mvn/                         # Maven wrapper config, JDK 21 settings
├── mvnw / mvnw.cmd / mvnw-jdk21.ps1
└── pom.xml                       # Root aggregator (revision 1.0.0-SNAPSHOT)
```

## Directory Purposes

**api-connector-domain/**
- Purpose: Innermost hexagonal core — no Spring dependencies
- Contains: `domain/model/` (records), `domain/spi/` (ports)
- Key files: `InvocationRequest.java`, `IntegrationOrchestrator.java`
- Subdirectories: `model/`, `spi/`

**api-connector-spec/**
- Purpose: Connector specification parsing and Java Catalog annotations
- Contains: `spec/model/` (immutable spec records), `spec/catalog/` (annotations + scanner)
- Key files: `ConnectorSpec.java`, `ConnectorSpecParser.java`, `CatalogConnectorScanner.java`
- Subdirectories: `model/`, `catalog/`

**api-connector-auth/**
- Purpose: Outbound authentication profiles
- Contains: `auth/spi/`, `auth/profile/`, `auth/support/`, `auth/context/`
- Key files: `AuthEngine.java`, `AkskHmacSha256V1AuthProvider.java`, `AkskCanonicalSigner.java`
- Subdirectories: `profile/` (one class per auth type), `support/` (crypto helpers)

**api-connector-engine/**
- Purpose: Application orchestration layer
- Contains: Orchestrator, HTTP transport, registry, endpoint resolution, response evaluation
- Key files: `DefaultIntegrationOrchestrator.java`, `ConnectorRegistry.java`, `JdkHttpTransport.java`
- Subdirectories: `transport/`, `store/`, `config/`

**api-connector-connectors/**
- Purpose: Built-in vendor connector definitions as Java Catalog interfaces
- Contains: Per-vendor `*ConnectorCatalog.java` annotated interfaces
- Key files: `BuiltinConnectorCatalogs.java`, `CatalogManagedSpecMerger.java`
- Subdirectories: `connectors/demo/`, `connectors/idps/`, `connectors/gaode/`, `connectors/baidu/`

**api-connector-api/**
- Purpose: REST API driving adapters
- Contains: Controllers, DTOs, invoke services, admin BFF, legacy compat, OpenAPI
- Key files: `IntegrationProxyController.java`, `IntegrationAdminController.java`, `IntegrationInvokeService.java`
- Subdirectories: `controller/`, `service/`, `dto/`, `admin/`, `legacy/`, `invoke/`, `openapi/`, `config/`

**api-connector-persistence/**
- Purpose: JDBC persistence adapter
- Contains: Config store, sync service, periodic job, admin sync endpoint
- Key files: `JdbcConnectorConfigStore.java`, `ConnectorConfigSyncService.java`
- Subdirectories: `jdbc/`, `web/`; resources: `db/schema.sql`

**api-connector-ui/**
- Purpose: Vue admin console
- Contains: `frontend/` (Vue 3 + Vite source), built to `src/main/resources/static/console/`
- Key files: `frontend/src/App.vue`, `frontend/src/router.js`, views in `frontend/src/views/`
- Subdirectories: `frontend/src/views/`, `frontend/src/components/`, `frontend/src/api/`

**api-connector-app/**
- Purpose: Composition root — Spring Boot entry, security, bootstrap
- Contains: Main class, security config, connector bootstrap, application YAML
- Key files: `IntegrationApplication.java`, `ConnectorBootstrapConfiguration.java`, `application.yml`
- Subdirectories: `config/`, `security/`, `web/`; resources: `application.yml`, `application-prod.yml`

**docs/**
- Purpose: Project documentation and ADRs
- Key files: `ARCHITECTURE.md`, `DEVELOPER.md`, `API-STYLE.md`, `THIRDPART-MIGRATION.md`, `adr/`

## Key File Locations

**Entry Points:**
- `api-connector-app/src/main/java/com/suntek/apiconnector/app/IntegrationApplication.java` — Spring Boot main
- `api-connector-api/.../api/controller/IntegrationProxyController.java` — Runtime invoke API
- `api-connector-ui/frontend/src/main.js` — Vue console entry

**Configuration:**
- `api-connector-dependencies/pom.xml` — Dependency version BOM
- `api-connector-parent/pom.xml` — Build plugin conventions
- `api-connector-app/src/main/resources/application.yml` — Runtime config
- `api-connector-app/src/main/resources/application-prod.yml` — Production overrides
- `api-connector-ui/frontend/vite.config.js` — Frontend build config
- `api-connector-ui/frontend/package.json` — npm dependencies

**Core Logic:**
- `api-connector-engine/.../DefaultIntegrationOrchestrator.java` — Invoke orchestration
- `api-connector-auth/.../AuthEngine.java` — Auth profile dispatch
- `api-connector-spec/.../ConnectorSpecParser.java` — Spec parsing
- `api-connector-connectors/.../BuiltinConnectorCatalogs.java` — Built-in connector registry

**Testing:**
- `api-connector-*/src/test/java/` — Per-module JUnit 5 tests (22 test files total)
- `api-connector-app/src/test/java/.../InvokeIntegrationTest.java` — WireMock integration test
- `api-connector-connectors/src/test/java/.../support/CatalogEndpointAssertions.java` — Test helper

**Documentation:**
- `docs/ARCHITECTURE.md` — Hexagonal architecture and dependency rules
- `docs/DEVELOPER.md` — Developer guide and conventions
- `docs/API-STYLE.md` — REST API contract
- `docs/profile-registry.md` — Auth profile registry

## Naming Conventions

**Files:**
- Java: `PascalCase.java` for classes; `*Test.java` for tests in parallel `src/test/java/` tree
- Vue: `PascalCase.vue` for views/components; `camelCase.js` for utilities
- Docs: `UPPERCASE.md` for top-level guides; `kebab-case.md` for ADRs

**Directories:**
- Maven modules: `api-connector-{layer}` prefix
- Java packages: `com.suntek.apiconnector.{module-segment}` mirroring Maven module
- Connector vendors: `connectors/{vendor}/` lowercase (e.g. `connectors/idps/`)

**Special Patterns:**
- Connector codes: `UPPER_SNAKE_CASE` (e.g. `IDPS`, `BAIDU_WENXIN`)
- Endpoint IDs: camelCase from Catalog method names (e.g. `roadSpeeds`)
- Catalog classes: `{Vendor}{Product}ConnectorCatalog` as `@CatalogConnector` interface
- Auth providers: `{ProfileName}AuthProvider`; `profileType()` returns registry ID
- Controllers: `Integration{Area}Controller`
- DTOs: `*Request`, `*Response`, `*Summary` suffixes

## Where to Add New Code

**New L1 connector (Java Catalog, preferred):**
- Implementation: `api-connector-connectors/src/main/java/.../connectors/{vendor}/{Vendor}ConnectorCatalog.java`
- Register: `BuiltinConnectorCatalogs.scanAll()`
- Tests: `api-connector-connectors/src/test/java/`

**New L1 connector (YAML, supplementary):**
- File: `api-connector-app/src/main/resources/connectors/{name}.yaml`
- Profile doc: `docs/profile-registry.md`

**New auth profile (L2):**
- Implementation: `api-connector-auth/src/main/java/.../auth/profile/{Name}AuthProvider.java`
- Wire bean: `IntegrationEngineConfiguration.java`
- Test: `api-connector-auth/src/test/java/`
- Registry: `docs/profile-registry.md`

**New REST endpoint:**
- Controller: `api-connector-api/src/main/java/.../api/controller/` or `api/admin/`
- DTOs: `api-connector-api/src/main/java/.../api/dto/` or `api/admin/`

**Orchestration changes:**
- Engine: `api-connector-engine/` (extend `DefaultIntegrationOrchestrator`)
- New port: `api-connector-domain/src/main/java/.../domain/spi/` if needed

**Persistence changes:**
- Implement `ConnectorConfigStore` port or extend `JdbcConnectorConfigStore`
- DDL: `api-connector-persistence/src/main/resources/db/schema.sql`

**Admin UI:**
- Views: `api-connector-ui/frontend/src/views/`
- Components: `api-connector-ui/frontend/src/components/`
- API client: `api-connector-ui/frontend/src/api/http.js`

**App-level config/security:**
- Config: `api-connector-app/src/main/java/.../app/config/`
- Security: `api-connector-app/src/main/java/.../app/security/`
- YAML: `api-connector-app/src/main/resources/application.yml`

## Special Directories

**api-connector-ui/frontend/**
- Purpose: Vue source code; not served directly in production
- Source: Built by `frontend-maven-plugin` during Maven `package`
- Output: `api-connector-ui/src/main/resources/static/console/`
- Committed: Source yes; build output typically gitignored via `api-connector-ui/.gitignore`

**.mvn/**
- Purpose: Maven wrapper and JDK 21 settings
- Key files: `wrapper/maven-wrapper.properties`, `settings-jdk21.xml`, `jvm.config`

**docs/schemas/**
- Purpose: JSON schemas for profile metadata used by UI
- Contains: `docs/schemas/profiles-meta/` (3 profile meta files currently)

**.planning/codebase/**
- Purpose: GSD codebase map documents (this folder)
- Committed: Yes (generated by `/gsd-map-codebase`)

---

*Structure analysis: 2026-06-17*
*Update when directory structure changes*
