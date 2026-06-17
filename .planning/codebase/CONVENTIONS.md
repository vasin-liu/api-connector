---
last_mapped_commit: 9b5f5ce02dc1eb222a9a437d18f44861be53ccb6
last_mapped_at: 2026-06-17
---
# Coding Conventions

**Analysis Date:** 2026-06-17

## Naming Patterns

**Files:**
- Java classes: `PascalCase.java` (e.g. `ConnectorRegistry.java`, `AkskCanonicalSigner.java`)
- Test files: `{ClassName}Test.java` in parallel `src/test/java/` tree mirroring main package
- Vue views: `PascalCase.vue` (e.g. `ConnectorList.vue`, `ConnectorEditor.vue`)
- Vue utilities: `camelCase.js` (e.g. `queryParams.js`, `clientSettings.js`)

**Functions/Methods:**
- camelCase for all Java methods (e.g. `require()`, `apply()`, `invoke()`)
- Catalog endpoint methods: camelCase → becomes `endpointId` (e.g. `roadSpeeds()`)
- Event handlers in Vue: standard Vue conventions

**Variables:**
- camelCase for Java fields and local variables
- UPPER_SNAKE_CASE for connector codes (`IDPS`, `BAIDU_WENXIN`) and Java constants
- `record` types for immutable value objects (e.g. `ResponseEvaluation.java`)

**Types:**
- PascalCase for classes, interfaces, records, enums — no `I` prefix on interfaces
- Domain/spec models: `final` classes with accessors, not Lombok — `ConnectorSpec.java`, `ConnectorCode.java`
- API DTOs: Lombok `@Data` / `@Builder` — `ProxyInvokeRequest.java`, `ApiErrorResponse.java`
- Utility classes: `public final` with private constructor — `AkskCanonicalSigner.java`

## Code Style

**Formatting:**
- JDK 21 with `maven.compiler.release=21` — `api-connector-parent/pom.xml`
- UTF-8 source encoding — root `pom.xml`
- Compiler flags: `-Xlint:unchecked`, `-parameters` — `api-connector-parent/pom.xml`
- No Prettier/Checkstyle/Spotless configured in repo
- Text blocks for JSON in tests — `ResponseEvaluatorTest.java`

**Linting:**
- No ESLint for Vue frontend
- Maven compiler warnings only (`-Xlint:unchecked`)
- Run full verify: `.\mvnw-jdk21.ps1 clean verify`

## Import Organization

**Order:**
1. `java.*` / `javax.*` standard library
2. Third-party packages (Spring, Jackson, SnakeYAML, etc.)
3. `com.suntek.apiconnector.*` internal modules
4. Static imports (rare)

**Grouping:**
- No enforced blank-line grouping; follows typical Java IDE defaults
- Module boundaries enforced by Maven POM dependencies, not package imports

**Path Aliases:**
- None in Java (standard package structure)
- Vue: relative imports within `frontend/src/`

## Error Handling

**Patterns:**
- Validation / not-found: `IllegalArgumentException` with descriptive message — `ConnectorRegistry.require()`, `EndpointResolver.java`
- I/O / crypto / upstream failures: `IllegalStateException` wrapping cause — `JdkHttpTransport.java`, `JdbcConnectorConfigStore.java`
- Rate limiting: dedicated `InvokeRateLimitException` — `api-connector-api/.../invoke/InvokeRateLimitException.java`
- HTTP mapping via `@RestControllerAdvice` — `RuntimeApiExceptionHandler.java`, `AdminApiExceptionHandler.java`

**Error Types:**
- Throw on invalid input, missing connector/endpoint, unknown auth profile
- Platform errors returned as `{ "code": "...", "message": "..." }` — `ApiErrorResponse.java`
- Documented error codes: `CONNECTOR_NOT_FOUND`, `ENDPOINT_NOT_FOUND`, `STRICT_ENDPOINTS`, `RATE_LIMITED`, `UPSTREAM_AUTH_FAILED` — `docs/API-STYLE.md`
- Vendor HTTP status preserved in invoke response body, separate from platform status

## Logging

**Framework:**
- SLF4J via `LoggerFactory.getLogger()` — not `@Slf4j` Lombok annotation in sampled production code
- Named audit logger: `LoggerFactory.getLogger("integration.invoke.audit")` — `InvokeAuditLogger.java`

**Patterns:**
- Structured key=value audit logging for invoke events
- Persistence sync logs in `ConnectorConfigSyncService.java`
- Engine/auth layers mostly throw rather than log errors
- Toggle audit: `integration.invoke.audit-enabled` in `application.yml`

## Comments

**When to Comment:**
- All `public` classes and methods require Javadoc with `@param` / `@return` — per `docs/DEVELOPER.md`
- PCI copyright header on every Java file (2021–2026)
- `@author Gensokyo`, `@version`, `@since` on core types
- Catalog endpoint comments in Chinese describing vendor API purpose — `IdpsConnectorCatalog.java`
- Core domain Javadocs in English — `ConnectorSpec.java`, `ConnectorCode.java`

**JSDoc/TSDoc:**
- Required for public Java API per `docs/DEVELOPER.md`
- Vue components: minimal inline comments; UI spec in `docs/UI-CONFIG-SPEC.md`

**TODO Comments:**
- No `TODO` or `FIXME` markers found in Java sources (as of mapping date)

## Function Design

**Size:**
- Orchestrator and invoke service methods are moderate length; auth providers are focused single-responsibility classes
- Utility extraction in `auth/support/` (e.g. `AkskCanonicalSigner.java`)

**Parameters:**
- Domain records (`InvocationRequest`, `AuthContext`) bundle related parameters
- AuthProvider `apply(AuthContext)` single-parameter pattern

**Return Values:**
- Immutable records for results (`InvocationResult`, `AuthOutcome`, `ResponseEvaluation`)
- Explicit exception throws for failure paths

## Module Design

**Exports:**
- Package-private test classes (`class FooTest`, not `public`)
- SPI interfaces in `domain.spi` and `auth.spi` for extension points
- Spring `@Configuration` classes wire beans in `engine/config/` and `app/config/`

**Dependency Rules (enforced):**
- `domain` → no Spring
- `spec` → domain only
- `auth` → domain, spec
- `engine` → domain, spec, auth, spring-context
- `api` → engine, spring-web (not direct auth)
- `app` → api, ui, persistence, spring-boot
- Documented in `docs/ARCHITECTURE.md` dependency table

**Catalog-as-code pattern:**
- Connectors defined as annotated Java interfaces, not YAML — preferred for production per `docs/API-STYLE.md`
- Endpoint metadata derived by reflection in `CatalogConnectorScanner.java`

---

*Convention analysis: 2026-06-17*
*Update when patterns change*
