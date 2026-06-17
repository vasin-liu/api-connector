# Walking Skeleton — API Connector Auth Pipeline

**Phase:** 1 — Auth Plugin Architecture
**Generated:** 2026-06-17

## Capability Proven End-to-End

A connector with a Groovy auth script compiles once on publish, runs through `AuthEngine` and `DefaultIntegrationOrchestrator`, produces an outbound HTTP request with auth headers applied, and returns an `InvocationResult` carrying an immutable `AuthContextSnapshot` — all verified by `.\mvnw-jdk21.ps1 clean verify` without admin UI or JDBC persistence.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Runtime | Java 21 + Spring Boot 4.0.6 | Existing brownfield stack; records, virtual threads, JDK HttpClient |
| Build | Maven multi-module (`api-connector-*`) | Established project layout; CI via `mvnw-jdk21.ps1` |
| Auth SPI | `AuthProvider` + `AuthEngine` pipeline merge | Already implemented for 7 built-in profiles; Phase 1 extends, not replaces |
| Script extension | Groovy 4.0.32 JSR-223 (`org.apache.groovy:groovy-jsr223`) | Legacy vendor flexibility; compile-on-publish cache in `api-connector-scripting` |
| Script module | `api-connector-scripting` (shared by auth + future mapping) | D-06 separation of concerns; single compile cache service |
| Auth downstream shape | Immutable `AuthContextSnapshot` + `AuthOutcome` on `InvocationResult` | D-10..D-12; mapping (Phase 2) reads tokens without re-fetch |
| OAuth tokens | Central `TokenCache` keyed `code3rd\|profile\|scope` | D-19..D-21; replaces per-provider maps |
| Config (Phase 1) | Java Catalog + classpath YAML + env credentials | D-22; JDBC admin deferred to Phase 4 |
| Validation (Phase 1) | Unit/integration tests in test resources | D-23; no admin dry-run API until Phase 4 |
| L3 vendors | Groovy scripts default; no PF4J in v1 | D-13..D-15; Spring `@Bean` AuthProvider if SPI ever needed |
| Errors | Platform codes (`AUTH_PROFILE_MISSING`, `UPSTREAM_AUTH_FAILED`, etc.) + `details` map | D-17, D-18; legacy-shaped JSON deferred to Phase 6 |

## Stack Touched in Phase 1

- [x] Project scaffold — existing Maven modules + Spring Boot app on port 19090
- [x] Auth routing — `AuthEngine.authenticate()` → `AuthProvider.apply()` dispatch
- [x] Script compile cache — `ScriptCompileService` + SHA-256 keyed `CompiledScriptCache`
- [x] Orchestrator invoke — `DefaultIntegrationOrchestrator.invoke()` applies auth to HTTP request
- [x] Test-driven validation — JUnit 5 + WireMock via `.\mvnw-jdk21.ps1 clean verify`

## Module Dependency Graph (Phase 1)

```
api-connector-dependencies     (+ groovy-jsr223 4.0.32 BOM)
api-connector-domain           (+ AuthContextSnapshot; extend InvocationResult)
api-connector-scripting        (NEW — ScriptCompileService, CompiledScriptCache)
api-connector-spec             (+ EndpointSpec.authOverride, parser validation)
api-connector-auth             (+ TokenCache, GroovyAuthScriptProvider, AuthException, Wave 1 L2)
api-connector-engine           (+ AuthConfigResolver, ConnectorPublishListener, orchestrator)
api-connector-api              (+ ApiErrorResponse.details, AuthException handler)
api-connector-connectors         (Wave 1 catalog references + demo Groovy connector)
docs/legacy-auth-inventory.md  (NEW — D-03 audit matrix)
```

## Out of Scope (Deferred to Later Slices)

- PF4J hot-deploy auth/mapping JARs (v2 — PLAT-V2-02)
- Transform Pipeline / SM4 body encrypt (Phase 2 mapping — D-16)
- Per-vendor legacy error JSON shaping (Phase 6 compat harness)
- JDBC persistence + admin CRUD for auth scripts (Phase 4 Admin BFF)
- L3 Java SPI vendor plugins — 大华, 海康 Artemis SDK (Groovy in Phase 1 — D-13)
- Distributed OAuth token cache Redis (v2 — PLAT-V2-01)
- Groovy sandbox for untrusted authors (v2 — AUTH-V2-02)
- Data mapping engine consumption of snapshot (Phase 2 — structural wiring only in Phase 1)

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions:

- **Phase 2:** Data Mapping Engine — Groovy mapping scripts reuse `api-connector-scripting`; mapping reads `AuthContextSnapshot.ext`
- **Phase 3:** Orchestrator Pipeline Integration — full resolve → mapRequest → auth → HTTP → evaluate → mapResponse ordering
- **Phase 4:** Admin BFF & Gateway Metadata — JDBC persistence, publish API, dry-run invoke
- **Phase 5:** React Console & Observability — operator UI for auth/mapping config
- **Phase 6:** Legacy Compat Test Harness — golden-file contract tests gate migration quality
- **Phase 7–8:** Vendor Migration Waves — Wave 1/2 cutover using auth profiles established here

## Walking Skeleton Verification Command

```powershell
.\mvnw-jdk21.ps1 clean verify
```

Pass signal: all module tests green; `ScriptCompileServiceTest` proves compile-once; orchestrator test exposes non-null `authSnapshot()` on result.
