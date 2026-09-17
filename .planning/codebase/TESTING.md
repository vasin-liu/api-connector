---
last_mapped_commit: 9b5f5ce02dc1eb222a9a437d18f44861be53ccb6
last_mapped_at: 2026-06-17
---
# Testing Patterns

**Analysis Date:** 2026-06-17

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) — all `*Test.java` files
- Maven Surefire Plugin 3.5.4 — `api-connector-parent/pom.xml` (pluginManagement, no custom configuration)

**Assertion Library:**
- JUnit `Assertions.*` — `assertEquals`, `assertThrows`, `assertDoesNotThrow`
- AssertJ `assertThat` — `ConnectorSpecParserTest.java`, `CatalogManagedSpecMergerTest.java`
- No Mockito — zero `@Mock` usage; stubs use anonymous implementations or real objects

**Run Commands:**
```bash
.\mvnw-jdk21.ps1 clean verify                          # Full build + all tests
.\mvnw-jdk21.ps1 clean package -DskipTests             # Build without tests
.\mvnw-jdk21.ps1 -pl api-connector-engine test         # Single module tests
.\mvnw-jdk21.ps1 -pl api-connector-app -am package     # App module + dependencies
.\mvnw-jdk21.ps1 -q clean verify -DskipTests           # Quiet verify, skip tests
```

## Test File Organization

**Location:**
- `src/test/java/` parallel to `src/main/java/` in each module
- 22 test files across 6 modules (~120 main Java files)

**Naming:**
- `{ClassName}Test.java` for all tests
- No distinction between unit/integration in filename
- Package-private test classes (not `public`)

**Structure:**
```
api-connector-app/src/test/java/com/suntek/apiconnector/app/
  InvokeIntegrationTest.java
  LegacyCompatIntegrationTest.java
  SecurityIntegrationTest.java

api-connector-api/src/test/java/com/suntek/apiconnector/api/
  admin/
  invoke/
  legacy/
  openapi/

api-connector-engine/src/test/java/com/suntek/apiconnector/engine/
  DefaultIntegrationOrchestratorTest.java
  ResponseEvaluatorTest.java
  transport/

api-connector-connectors/src/test/java/com/suntek/apiconnector/connectors/
  ProductionEndpointResolverTest.java
  support/CatalogEndpointAssertions.java

api-connector-auth/src/test/java/com/suntek/apiconnector/auth/
  support/AkskCanonicalSignerTest.java
  profile/GaodeTrafficHmacAuthProviderTest.java

api-connector-spec/src/test/java/com/suntek/apiconnector/spec/
  ConnectorSpecParserTest.java
```

**Modules with no tests:**
- `api-connector-domain/` — zero test files
- `api-connector-persistence/` — `spring-boot-starter-test` declared but no tests written
- `api-connector-ui/` — no frontend test files

## Test Structure

**Suite Organization:**
```java
class ResponseEvaluatorTest {

    private final ResponseEvaluator evaluator = new ResponseEvaluator();

    @Test
    void evaluatesSuccessWhenJsonPathMatches() {
        // arrange — JSON text block
        // act — call evaluator
        // assert — JUnit Assertions
    }
}
```

**Patterns:**
- Direct instantiation for pure unit tests (no Spring context)
- `@BeforeEach` for registry setup in integration tests
- `@ParameterizedTest` + `@MethodSource` for catalog endpoint coverage — `ProductionEndpointResolverTest.java`
- Explicit arrange/act/assert in complex tests
- Package-private test classes

## Mocking

**Framework:**
- No Mockito — manual stubs and fakes only
- WireMock 3.13.1 for HTTP contract testing — `api-connector-app/pom.xml`

**Patterns:**
```java
// Anonymous HttpTransport fake
HttpTransport transport = request -> new HttpTransportResponse(200, "{}", Map.of());

// WireMock in Spring Boot integration test
@SpringBootTest(webEnvironment = RANDOM_PORT)
class InvokeIntegrationTest {
    @DynamicPropertySource
    static void wiremockProps(DynamicPropertyRegistry registry) { ... }
}

// Registry mutation in @BeforeEach
registry.put(code3rd, spec, credentials);
```

**What to Mock:**
- External HTTP via WireMock — `InvokeIntegrationTest.java`, `LegacyCompatIntegrationTest.java`
- HTTP transport via anonymous `HttpTransport` — `DefaultIntegrationOrchestratorTest.java`
- Dynamic properties for security toggles — `SecurityIntegrationTest.java`

**What NOT to Mock:**
- Built-in catalog specs — use `BuiltinConnectorCatalogs.catalogSpec(...)` as real fixtures
- Pure functions (signers, parsers, evaluators) — tested directly
- Internal business logic in unit tests

## Fixtures and Factories

**Test Data:**
```java
// Real catalog spec as fixture
ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec("DEMO_NONE");

// JSON text blocks in tests
String body = """
    {"status":"ok","data":{"value":42}}
    """;
```

**Location:**
- Inline fixtures in test files for simple cases
- Shared helper: `api-connector-connectors/src/test/java/.../support/CatalogEndpointAssertions.java`
- No dedicated `tests/fixtures/` directory

## Coverage

**Requirements:**
- No enforced coverage target
- No JaCoCo or coverage plugin configured in any `pom.xml`
- Coverage tracked informally; focus on critical paths per `docs/DEVELOPER.md`

**Configuration:**
- None — Surefire runs all `*Test.java` with defaults

**View Coverage:**
- Not configured; would require adding JaCoCo plugin

## Test Types

**Unit Tests:**
- Scope: Single class in isolation
- Examples: `ResponseEvaluatorTest.java`, `AkskCanonicalSignerTest.java`, `StrictEndpointsEnforcerTest.java`, `ConnectorSpecParserTest.java`
- Speed: Fast, no Spring context

**Integration Tests:**
- Scope: Full Spring Boot context with WireMock
- Examples: `InvokeIntegrationTest.java`, `LegacyCompatIntegrationTest.java`, `SecurityIntegrationTest.java`
- Setup: `@SpringBootTest(RANDOM_PORT)`, `@DynamicPropertySource`, real `HttpClient`

**E2E Tests:**
- Not implemented
- Planned: Testcontainers + vendor sandboxes per `docs/DEVELOPER.md` §测试策略

## Common Patterns

**Async Testing:**
- Invoke API tested via `java.net.http.HttpClient` in integration tests
- No reactive/async unit test patterns (platform uses virtual threads, synchronous API)

**Error Testing:**
```java
@Test
void rejectsUnknownEndpoint() {
    assertThrows(IllegalArgumentException.class,
        () -> enforcer.enforce(spec, "unknownEndpoint"));
}
```

**Parameterized Catalog Tests:**
```java
@ParameterizedTest
@MethodSource("productionEndpoints")
void resolvesAllProductionEndpoints(String code3rd, String endpointId) { ... }
```

**Snapshot Testing:**
- Not used

## Documented Test Strategy

From `docs/DEVELOPER.md`:
- **Unit:** JUnit 5 for Auth Profiles and engine logic (partially implemented)
- **HTTP contract:** WireMock (implemented in `api-connector-app`)
- **E2E:** Testcontainers + vendor sandboxes (planned, not present)
- **New Auth Profile checklist:** implement `AuthProvider` + unit test + `profile-registry.md` entry

---

*Testing analysis: 2026-06-17*
*Update when test patterns change*
