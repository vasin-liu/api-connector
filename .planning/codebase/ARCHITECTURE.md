---
last_mapped_commit: 9b5f5ce02dc1eb222a9a437d18f44861be53ccb6
last_mapped_at: 2026-06-17
---
# Architecture

**Analysis Date:** 2026-06-17

## Pattern Overview

**Overall:** Hexagonal (Ports & Adapters) multi-module Maven monolith

**Key Characteristics:**
- Framework-free domain core (`api-connector-domain`) with SPI ports
- Declarative connector specs (Java Catalog + optional YAML + JDBC)
- Single Spring Boot fat JAR with unified runtime + admin UI
- Pluggable auth profiles and HTTP transport via SPI
- Spec-driven proxy API — clients invoke by `code3rd` + `endpointId`, not raw URLs

## Layers

**Domain (innermost):**
- Purpose: Framework-agnostic models and SPI contracts
- Contains: `InvocationRequest`, `InvocationResult`, `ConnectorCode`, `IntegrationOrchestrator`, `StreamingInvocationSink`
- Location: `api-connector-domain/src/main/java/com/suntek/apiconnector/domain/`
- Depends on: Nothing (no Spring)
- Used by: spec, auth, engine, api

**Spec:**
- Purpose: ConnectorSpec model, YAML parsing, Java Catalog scanning
- Contains: `ConnectorSpec`, `EndpointSpec`, `CatalogConnectorScanner`, `ConnectorSpecLoader`
- Location: `api-connector-spec/src/main/java/com/suntek/apiconnector/spec/`
- Depends on: domain
- Used by: auth, engine, connectors, app bootstrap

**Auth:**
- Purpose: Authentication profile engine — sign requests, inject tokens/headers
- Contains: `AuthEngine`, `AuthProvider` SPI, profile implementations
- Location: `api-connector-auth/src/main/java/com/suntek/apiconnector/auth/`
- Depends on: domain, spec
- Used by: engine

**Engine (application):**
- Purpose: Orchestration, HTTP transport, connector registry, endpoint resolution
- Contains: `DefaultIntegrationOrchestrator`, `ConnectorRegistry`, `JdkHttpTransport`, `ResponseEvaluator`
- Location: `api-connector-engine/src/main/java/com/suntek/apiconnector/engine/`
- Depends on: domain, spec, auth, spring-context
- Used by: api

**API (driving adapter):**
- Purpose: REST controllers, DTOs, admin BFF, legacy compat, OpenAPI
- Contains: `IntegrationProxyController`, `IntegrationAdminController`, invoke services
- Location: `api-connector-api/src/main/java/com/suntek/apiconnector/api/`
- Depends on: engine, spring-web (not direct auth)
- Used by: app

**Persistence (driven adapter):**
- Purpose: JDBC-backed connector config store and sync
- Contains: `JdbcConnectorConfigStore`, `ConnectorConfigSyncService`
- Location: `api-connector-persistence/src/main/java/com/suntek/apiconnector/persistence/`
- Depends on: engine store port, spring-jdbc
- Used by: app

**Connectors (spec provider):**
- Purpose: Built-in vendor Java Catalog definitions
- Contains: `BuiltinConnectorCatalogs`, per-vendor `*ConnectorCatalog.java` interfaces
- Location: `api-connector-connectors/src/main/java/com/suntek/apiconnector/connectors/`
- Depends on: spec
- Used by: app bootstrap

**App (composition root):**
- Purpose: Spring Boot entry, security, connector bootstrap, fat JAR assembly
- Location: `api-connector-app/src/main/java/com/suntek/apiconnector/app/`
- Depends on: api, ui, persistence, spring-boot

**UI (static adapter):**
- Purpose: Vue admin console served at `/console/`
- Location: `api-connector-ui/frontend/` → `static/console/` in JAR

## Data Flow

**Proxy invoke (primary path):**

1. Client sends `POST /api/v1/integrations/{code3rd}/endpoints/{endpointId}/invoke` — `IntegrationProxyController.java`
2. `IntegrationInvokeService` applies rate limiting, strict-endpoint enforcement, resolves endpoint — `InvokeRateLimiter.java`, `StrictEndpointsEnforcer.java`, `EndpointResolver.java`
3. Builds `InvocationRequest` and calls `IntegrationOrchestrator.invoke()` — domain SPI
4. `DefaultIntegrationOrchestrator` loads spec + credentials from `ConnectorRegistry.require(code3rd)`
5. `AuthEngine` dispatches to matching `AuthProvider.apply()` → `AuthOutcome` (header/query/body mutations)
6. Merged request sent via `HttpTransport.exchange()` — default `JdkHttpTransport.java`
7. Vendor response evaluated by `ResponseEvaluator` against `response.successWhen` JSONPath
8. `InvocationResult` mapped to `ProxyInvokeResponse` via `InvokeHttpResponseMapper`
9. Platform HTTP status from `IntegrationInvokeProperties.platformHttpStatus`; vendor status preserved in body

**Streaming variant:**
- `IntegrationProxyController.streamEndpoint` → `IntegrationOrchestrator.invokeStream` → `HttpTransport.exchangeStream` → `ServletStreamingInvocationSink`

**Connector bootstrap (startup):**
- `ConnectorBootstrapConfiguration.java` scans Java Catalogs, loads classpath YAML, injects env credentials
- On `ApplicationReadyEvent`, JDBC sync reloads published configs into registry — `ConnectorConfigSyncService.java`

**State Management:**
- In-memory `ConnectorRegistry` (ConcurrentHashMap) — spec + credentials + status per `code3rd`
- JDBC persistence for published connector configs — `JdbcConnectorConfigStore.java`
- OAuth token cache in-memory per auth provider
- No distributed state; single-instance assumption

## Key Abstractions

**IntegrationOrchestrator:**
- Purpose: Domain port for end-to-end invoke orchestration
- Examples: `domain/spi/IntegrationOrchestrator.java` ← `engine/DefaultIntegrationOrchestrator.java`
- Pattern: SPI with single default implementation

**AuthProvider:**
- Purpose: Pluggable outbound authentication profile
- Examples: `auth/spi/AuthProvider.java`, `auth/profile/*AuthProvider.java`
- Pattern: Registry keyed by `profileType()`; wired via `IntegrationEngineConfiguration.java`

**HttpTransport:**
- Purpose: Pluggable HTTP client abstraction
- Examples: `engine/transport/HttpTransport.java`, `JdkHttpTransport.java`
- Pattern: Interface with JDK default; replaceable bean

**ConnectorRegistry:**
- Purpose: Runtime in-memory store of connector specs and credentials
- Examples: `engine/ConnectorRegistry.java`
- Pattern: Mutable registry; loaded from Catalog + YAML + JDBC sync

**ConnectorConfigStore:**
- Purpose: Persistence port for connector configs
- Examples: `engine/store/ConnectorConfigStore.java` ← `persistence/jdbc/JdbcConnectorConfigStore.java`
- Pattern: Hexagonal port/adapter

**ConnectorSpec / Java Catalog:**
- Purpose: Declarative connector definition (endpoints, auth, response rules)
- Examples: `spec/model/ConnectorSpec.java`, `connectors/idps/IdpsConnectorCatalog.java`
- Pattern: Annotated interface methods → scanned endpoints via `CatalogConnectorScanner.java`

## Entry Points

**Spring Boot main:**
- Location: `api-connector-app/src/main/java/com/suntek/apiconnector/app/IntegrationApplication.java`
- Triggers: `java -jar api-connector-app.jar` or Maven `spring-boot:run`
- Responsibilities: Component scan `com.suntek.apiconnector`, auto-configure all modules

**Runtime invoke API:**
- Location: `api-connector-api/.../api/controller/IntegrationProxyController.java`
- Triggers: HTTP POST from clients or legacy compat filter
- Responsibilities: Request validation, delegate to `IntegrationInvokeService`

**Admin BFF:**
- Location: `api-connector-api/.../api/admin/IntegrationAdminController.java`
- Triggers: HTTP from Vue console at `/console/`
- Responsibilities: Connector CRUD, publish, credential management

## Error Handling

**Strategy:** Exception-based with `@RestControllerAdvice` mapping to structured JSON errors

**Patterns:**
- `IllegalArgumentException` → 400/404 with codes `CONNECTOR_NOT_FOUND`, `ENDPOINT_NOT_FOUND`, `STRICT_ENDPOINTS` — `RuntimeApiExceptionHandler.java`
- `InvokeRateLimitException` → 429 `RATE_LIMITED`
- `IllegalStateException` (missing AuthProvider) → 502 `UPSTREAM_AUTH_FAILED`
- Admin errors → 400 `BAD_REQUEST` — `AdminApiExceptionHandler.java`
- Error payload: `{ "code": "...", "message": "..." }` — `ApiErrorResponse.java`
- Vendor HTTP status preserved separately in invoke response body; platform status mapped via config

## Cross-Cutting Concerns

**Logging:**
- SLF4J `LoggerFactory` in production code
- Structured invoke audit via `InvokeAuditLogger.java` (`integration.invoke.audit` logger)
- Toggle: `integration.invoke.audit-enabled` in `application.yml`

**Validation:**
- Spring Validation on request DTOs
- Business rules via `IllegalArgumentException` in engine (registry lookup, endpoint resolution)
- Strict endpoints mode for catalog-managed connectors

**Authentication:**
- Outbound: AuthProvider profiles per connector spec
- Inbound: Optional API key filter (`IntegrationApiKeyFilter.java`); disabled by default
- Legacy URL compat exposed without auth when security disabled

**Rate limiting:**
- Per-`code3rd` per-minute limit — `InvokeRateLimiter.java`
- Disabled by default (`rate-limit-per-code3rd-per-minute: 0`)

---

*Architecture analysis: 2026-06-17*
*Update when major patterns change*
