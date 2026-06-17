---
last_mapped_commit: 9b5f5ce02dc1eb222a9a437d18f44861be53ccb6
last_mapped_at: 2026-06-17
---
# Codebase Concerns

**Analysis Date:** 2026-06-17

## Tech Debt

**Transform pipeline parsed but never executed:**
- Issue: `ConnectorSpec.transform()` is modeled and serialized but `DefaultIntegrationOrchestrator.java` runs Auth → HTTP only
- Why: P2 backlog item per `docs/ARCHITECTURE.md` §7
- Impact: Specs with transform steps silently ignored at runtime
- Fix approach: Implement transform pipeline per `docs/adr/002-auth-and-transform.md`

**In-memory connector registry:**
- Issue: `ConnectorRegistry.java` stores all specs/credentials in `ConcurrentHashMap`; comment notes future DB replacement
- Why: Fast MVP; JDBC sync overlays DB configs but registry remains authoritative runtime store
- Impact: No cluster coordination; restart loses unsynced in-memory state
- Fix approach: Externalize registry or add distributed cache for multi-instance deployments

**JDBC sync upsert-only:**
- Issue: `ConnectorConfigSyncService.reloadFromStore()` upserts DB rows but does not purge registry entries for deleted DB records
- File: `api-connector-persistence/.../ConnectorConfigSyncService.java`
- Impact: Stale connectors remain in registry until explicit admin delete
- Fix approach: Diff DB keys against registry on sync; remove orphans

**Documentation drift:**
- Issue: `docs/DEVELOPER.md` lists only 2 auth profiles; code has 7 in `IntegrationEngineConfiguration.java`
- Issue: `docs/DEPENDENCIES.md` lists Lombok 1.18.38; BOM pins 1.18.44 in `api-connector-dependencies/pom.xml`
- Impact: Onboarding confusion
- Fix approach: Sync docs with implementation

**~50+ thirdpart packages unmigrated:**
- Issue: Only 5 production connectors landed; remainder per `docs/THIRDPART-MIGRATION.md` §7
- Impact: Large migration surface; many auth profiles in `docs/profile-registry.md` unimplemented
- Fix approach: Phased migration per profile priority (P1/P2/P3)

## Known Bugs

**OAuth token cache key too coarse:**
- Symptoms: Token reuse across different credentials or scopes for same `code3rd`
- Trigger: Multiple credential sets or scopes for one connector code
- Files: `OAuth2ClientCredentialsAuthProvider.java`, `OAuth2TokenInQueryAuthProvider.java`
- Root cause: Cache keyed by `code3rd` only; ADR-002 specifies `code3rd + profile + scope`
- Fix: Expand cache key; add credential-hash invalidation

**Empty credential bootstrap:**
- Symptoms: Connectors register with blank secrets when env vars unset
- Trigger: Missing `{CODE3RD}_APP_ID` / `_APP_SECRET` env vars
- File: `ConnectorBootstrapConfiguration.java`
- Workaround: Vendor calls fail at auth step
- Fix: Validate credentials on bootstrap; skip or mark connector as misconfigured

## Security Considerations

**Platform API auth disabled by default:**
- Risk: Runtime and admin APIs unprotected in default `application.yml`
- Files: `application.yml` (`integration.security.enabled: false`), `IntegrationApiKeyFilter.java`
- Current mitigation: `application-prod.yml` enables security
- Recommendations: Fail-closed default or document mandatory prod checklist

**Legacy URL compat exposed without auth:**
- Risk: Old thirdpart URL prefixes accessible when `integration.legacy.enabled: true` and security disabled
- File: `LegacyCompatFilter.java` (`matchIfMissing = true`)
- Current mitigation: None in dev mode
- Recommendations: Require auth on legacy paths or disable legacy in production

**Credentials stored in plaintext:**
- Risk: `APP_SECRET`, `PUBLIC_KEY` stored unencrypted in H2/DB
- Files: `schema.sql`, `JdbcConnectorConfigStore.java`, `ConnectorConfigStore.java` contract
- Current mitigation: API read masking via `CredentialsView.java` / `IntegrationConfigService.java`
- Recommendations: Implement P2 encryption at rest per `docs/CONNECTOR-PERSISTENCE.md` §5

**Default H2 with empty password:**
- Risk: Embedded DB with `sa`/empty password in `application.yml`
- Current mitigation: Dev-only default
- Recommendations: Document production datasource requirements; add startup warning

**BouncyCastle declared but unused for 国密:**
- Risk: `sm4_body_encrypt_v1` profile planned but not implemented; dependency present without usage
- Files: `api-connector-auth/pom.xml`, `docs/adr/002-auth-and-transform.md`
- Recommendations: Implement or defer dependency until needed

## Performance Bottlenecks

**Single-instance in-memory registry:**
- Problem: All connector lookups hit in-memory map; no connection pooling concerns but no horizontal scale
- Cause: Monolithic fat JAR design
- Improvement path: Stateless pods with shared DB + optional Redis for token cache

**Synchronous HTTP only:**
- Problem: All vendor calls block request thread (mitigated by virtual threads)
- File: `JdkHttpTransport.java`
- Cause: No async/reactive transport
- Improvement path: Virtual threads already enabled; monitor under load

## Fragile Areas

**Connector registry and composite loading:**
- File: `ConnectorRegistry.java`, `ConnectorBootstrapConfiguration.java`, `CatalogManagedSpecMerger.java`
- Why fragile: Composite mode merges Java Catalog + YAML + JDBC; ordering and override rules easy to break
- Common failures: Wrong base URL after merge; credentials not updated on null patch (`mergeCredentials()`)
- Safe modification: Add integration test per loading mode; document precedence rules
- Test coverage: Partial — `InvokeIntegrationTest.java` covers happy path only

**Spec parsing (lenient):**
- File: `ConnectorSpecParser.java`, `ConnectorSpecLoader.java`
- Why fragile: Missing `code3rd`/`baseUrl` become null; non-map endpoint entries silently dropped
- Common failures: Runtime `IllegalStateException` from `JdbcConnectorConfigStore.listPublished()` on bad JSON
- Safe modification: Add schema validation; fail fast on bootstrap
- Test coverage: `ConnectorSpecParserTest.java` — 2 happy-path cases only

**Catalog scanner limitations:**
- File: `CatalogConnectorScanner.java`
- Why fragile: Reflection-only; GET/POST methods only — PUT/PATCH/DELETE ignored
- Safe modification: Extend scanner or document constraint in `docs/API-STYLE.md`
- Test coverage: No dedicated scanner tests

**Auth engine provider registry:**
- File: `AuthEngine.java`
- Why fragile: Unknown profile → `IllegalStateException`; duplicate `profileType()` → last wins silently in `HashMap`
- Safe modification: Fail on duplicate registration at startup; validate all catalog auth profiles have providers
- Test coverage: No `AuthEngine` unit tests

## Scaling Limits

**Single fat JAR deployment:**
- Current capacity: Single instance with virtual threads
- Limit: No distributed token cache or registry; OAuth tokens not shared across instances
- Symptoms at limit: Duplicate token fetches; inconsistent registry if multiple instances write DB
- Scaling path: Shared DB (already supported) + distributed cache for tokens; load balancer with sticky sessions as interim

## Dependencies at Risk

**Spring Boot 4.0.6 (bleeding edge):**
- Risk: Major version jump from Boot 3.x; smaller community examples and ecosystem maturity
- Files: `api-connector-dependencies/pom.xml`, `docs/DEPENDENCIES.md`
- Impact: Upgrade friction; potential springdoc/Spring Security compatibility issues
- Migration plan: Pin versions in BOM; monitor Spring 4.x patch releases

**springdoc-openapi 3.0.3:**
- Risk: Boot 4 line; was 2.6.0 on Boot 3
- Impact: OpenAPI UI breakage on upgrade
- Migration plan: Test `/swagger-ui.html` after any BOM bump

**Spring Cloud BOM imported but unused:**
- Risk: `2025.1.0` imported in BOM; no module uses Spring Cloud starters
- Impact: Future upgrade surface without current benefit
- Migration plan: Remove BOM import if Cloud features not planned

**1.0.0-SNAPSHOT version:**
- Risk: Pre-release artifact identity across all modules
- Impact: No stable release baseline for downstream consumers

## Missing Critical Features

**OpenAPI import wizard:**
- Problem: Status 规划中 (P2) per `docs/openapi-import.md`
- Current workaround: Manual Java Catalog authoring
- Blocks: Rapid connector onboarding from vendor OpenAPI specs
- Implementation complexity: Medium–High

**Connector version gray/rollback:**
- Problem: `connector.version` modeled but gray/rollback not implemented
- Files: `docs/ARCHITECTURE.md` §5, `docs/CONNECTOR-PERSISTENCE.md`
- Blocks: Safe connector upgrades in production
- Implementation complexity: Medium

**Hot-reload API:**
- Problem: Documented as future in `docs/DEVELOPER.md` §新增连接器
- Current workaround: Restart app or admin publish + sync
- Blocks: Zero-downtime connector updates

**Observability (Micrometer/OTLP):**
- Problem: Actuator only; distributed tracing not implemented
- File: `docs/ARCHITECTURE.md` §6
- Blocks: Production debugging across vendor call chains

## Test Coverage Gaps

**Persistence module (zero tests):**
- What's not tested: `JdbcConnectorConfigStore`, sync service, periodic job, schema migrations
- Risk: Data corruption or sync bugs undetected
- Priority: High
- Difficulty: Medium (needs embedded H2 test setup)

**Auth providers (5 of 7 untested):**
- What's not tested: `OAuth2ClientCredentialsAuthProvider`, `OAuth2TokenInQueryAuthProvider`, `BearerStaticAuthProvider`, `ApiKeyQueryAuthProvider`, `AkskHmacSha256V1AuthProvider`, `NoneAuthProvider`, `AuthEngine`
- Risk: Auth regressions on vendor integrations
- Priority: High
- Difficulty: Low–Medium

**Domain module (zero tests):**
- What's not tested: Domain models and SPI contracts
- Risk: Low (mostly records) but SPI changes untested
- Priority: Low

**Frontend (zero tests):**
- What's not tested: Vue console views and components
- Risk: Admin UI regressions
- Priority: Medium
- Difficulty: Medium (need Vitest setup)

**Security integration (minimal):**
- What's not tested: Admin/console API key separation, legacy path auth, prod profile defaults
- File: `SecurityIntegrationTest.java` — runtime key + public actuator only
- Risk: Misconfigured production security
- Priority: High

**E2E with Testcontainers:**
- What's not tested: Full flow against real MySQL/PostgreSQL
- Risk: Production datasource issues
- Priority: Medium
- Difficulty: Medium (planned in `docs/DEVELOPER.md`, not implemented)

---

*Concerns audit: 2026-06-17*
*Update as issues are fixed or new ones discovered*
