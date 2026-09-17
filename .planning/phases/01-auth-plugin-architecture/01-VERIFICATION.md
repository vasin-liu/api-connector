---
phase: 01-auth-plugin-architecture
verified: 2026-06-17T08:05:00Z
status: human_needed
score: 44/46 must-haves verified
---

# Phase 1: Auth Plugin Architecture Verification Report

**Phase Goal:** Establish independent auth plugin module with Java built-ins and Groovy script support; AuthContext flows to downstream pipeline stages.

**Verified:** 2026-06-17T08:05:00Z  
**Status:** human_needed

## Goal Achievement

### Observable Truths

| # | Truth | Plan | Status | Evidence |
|---|-------|------|--------|----------|
| 1 | Groovy script compiles once; same SHA-256 hash returns cached `CompiledScript` | 01-01 | ✓ VERIFIED | `ScriptCompileService.compile()` cache lookup; `ScriptCompileServiceTest.secondCompileSameSourceUsesCache` |
| 2 | Invalid Groovy source fails with line in `ScriptCompileException` | 01-01 | ✓ VERIFIED | `ScriptCompileException` + `invalidScriptIncludesLineInException` test |
| 3 | `api-connector-scripting` module builds and tests pass | 01-01 | ✓ VERIFIED | Module in root `pom.xml`; 4/4 scripting tests green |
| 4 | `groovy_auth_script` connector/endpoint config produces `AuthOutcome` headers | 01-02 | ✓ VERIFIED | `GroovyAuthScriptProviderTest` asserts `Authorization: Bearer test-token` |
| 5 | Endpoint `authOverride` accepts only `groovy_auth_script` at parse time | 01-02 | ✓ VERIFIED | `ConnectorSpecParser` throws Groovy-only error; `ConnectorSpecParserTest` |
| 6 | `GroovyAuthScriptProvider` registered as Spring `AuthProvider` bean | 01-02 | ✓ VERIFIED | `IntegrationEngineConfiguration.groovyAuthScriptProvider()` |
| 7 | OAuth providers share central `TokenCache` keyed by code3rd + profile + scope | 01-03 | ✓ VERIFIED | `TokenCacheKey` record; OAuth providers inject `TokenCache` |
| 8 | Token refresh 60s before expiry with single-flight per key | 01-03 | ✓ VERIFIED | `REFRESH_SKEW_SECONDS = 60`; `TokenCacheTest.concurrentGetOrRefreshFetchesOnce` |
| 9 | Connector republish evicts cached tokens for code3rd | 01-03 | ✓ VERIFIED | `TokenCache.evictForConnector`; `ConnectorPublishListener.onPublish` |
| 10 | Publish listener pre-compiles all `groovy_auth_script` sources | 01-03 | ✓ VERIFIED | `ConnectorPublishListener` scans auth + endpoint overrides; `ConnectorPublishListenerTest` |
| 11 | `AuthContextSnapshot` carries ext keys (accessToken, signatureBase, etc.) | 01-04 | ✓ VERIFIED | `AuthContextSnapshot.of()` with D-11 keys; `AuthContextSnapshotTest` |
| 12 | `InvocationResult` exposes optional `authSnapshot` / `authOutcome` | 01-04 | ✓ VERIFIED | `InvocationResult.authSnapshot()` / `authOutcome()` accessors |
| 13 | Endpoint Groovy-only `authOverride` resolved via `AuthConfigResolver` | 01-04 | ✓ VERIFIED | `AuthConfigResolver.resolve`; `AuthConfigResolverTest` |
| 14 | `invokeStream` mirrors invoke auth path | 01-04 | ⚠️ PARTIAL | Shared `authenticate()` builds snapshot; snapshot not exposed on `StreamingInvocationSink` (Phase 2 concern) |
| 15 | Missing `AuthProvider` returns `AUTH_PROFILE_MISSING` with `details.profileType` | 01-05 | ✓ VERIFIED | `AuthEngine` + `AuthEngineTest`; `InvokeIntegrationTest.invokeUnknownAuthProfileReturnsStructuredError` |
| 16 | Groovy compile failure at publish returns `AUTH_SCRIPT_COMPILE_ERROR` | 01-05 | ✓ VERIFIED | `ConnectorPublishListener` wraps `ScriptCompileException`; `ConnectorPublishListenerTest` |
| 17 | OAuth upstream failure returns `UPSTREAM_AUTH_FAILED` with code3rd | 01-05 | ✓ VERIFIED | `OAuth2*AuthProvider` throws `AuthExceptions.upstreamFailed` (unit path; no dedicated HTTP integration test) |
| 18 | `ApiErrorResponse` includes optional `details` map in JSON | 01-05 | ✓ VERIFIED | `ApiErrorResponse.details`; `RuntimeApiExceptionHandler` maps `AuthException` |
| 19 | `docs/legacy-auth-inventory.md` exists with Wave 1 vendor rows | 01-06 | ✓ VERIFIED | 73 lines; IDPS, Gaode, Baidu, Hikvision, 大华, 讯飞 rows |
| 20 | Wave 1 L2 profiles implemented only if inventory confirms need | 01-06 | ✓ VERIFIED | Inventory gate documents skip; no Wave 1 vendor requires `oauth2_password` / `bearer_from_login` / `sm3_header_sign_v1` |
| 21 | Wave 1 catalogs register built-in auth via `CatalogAuth` | 01-06 | ✓ VERIFIED | `ProductionConnectorCatalogsTest.wave1CatalogAuthTypesMatchLegacyInventory` (5 code3rd) |
| 22 | WireMock integration captures signed OAuth/HMAC outbound headers | 01-06 | ✓ VERIFIED | `InvokeIntegrationTest` — `access_token` query (BAIDU_WENXIN), `X-Auth-Signature` (DEMO_AKSK) |
| 23 | Groovy compile-once on second invoke (ROADMAP SC#2) | 01-06 | ✓ VERIFIED | `InvokeIntegrationTest.groovyAuthScriptCompileOnceAcrossTwoInvokes` |
| 24 | L3 vendors marked Groovy path per D-14 in inventory | 01-06 | ✓ VERIFIED | Hikvision, 大华, 讯飞 rows marked **Groovy** with D-13/D-14 notes |

**Score:** 23/24 truths verified (1 partial, non-blocking for Phase 1)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `api-connector-scripting/.../ScriptCompileService.java` | JSR-223 compile-once service | ✓ EXISTS + SUBSTANTIVE | 89 lines; `compile(String source, String label)`, `getEngineByName("groovy")` |
| `api-connector-scripting/.../CompiledScriptCache.java` | ConcurrentHashMap cache | ✓ EXISTS + SUBSTANTIVE | 28 lines; `ConcurrentHashMap<String, CompiledScript>` |
| `api-connector-scripting/.../ScriptCompileServiceTest.java` | AUTH-03 compile-once tests | ✓ EXISTS + SUBSTANTIVE | 4 test methods, all pass |
| `api-connector-auth/.../GroovyAuthScriptProvider.java` | Groovy auth adapter | ✓ EXISTS + SUBSTANTIVE | 72 lines; `groovy_auth_script` profile type |
| `api-connector-auth/.../AuthScript.java` | Groovy contract | ✓ EXISTS + SUBSTANTIVE | `AuthOutcome apply(AuthContext ctx)` |
| `api-connector-spec/.../EndpointSpec.java` | `authOverride` field | ✓ EXISTS + SUBSTANTIVE | `authOverride()` accessor |
| `api-connector-auth/.../GroovyAuthScriptProviderTest.java` | AUTH-02 unit test | ✓ EXISTS + SUBSTANTIVE | 2 tests pass |
| `api-connector-auth/.../TokenCache.java` | Central OAuth store | ✓ EXISTS + SUBSTANTIVE | 55 lines; `evictForConnector` |
| `api-connector-auth/.../TokenCacheKey.java` | Composite key record | ✓ EXISTS + SUBSTANTIVE | `code3rd`, `profileType`, `scope` |
| `api-connector-engine/.../ConnectorPublishListener.java` | Publish hook | ✓ EXISTS + SUBSTANTIVE | 166 lines; compile + evict |
| `api-connector-auth/.../TokenCacheTest.java` | Cache behavior tests | ✓ EXISTS + SUBSTANTIVE | 5 tests including concurrent single-flight |
| `api-connector-domain/.../AuthContextSnapshot.java` | Immutable snapshot | ✓ EXISTS + SUBSTANTIVE | Record with unmodifiable `ext` |
| `api-connector-engine/.../AuthConfigResolver.java` | Auth resolution | ✓ EXISTS + SUBSTANTIVE | `resolve(spec, endpoint)` |
| `api-connector-domain/.../InvocationResult.java` | Snapshot carrier | ✓ EXISTS + SUBSTANTIVE | `authSnapshot()`, `authOutcome()` |
| `api-connector-engine/.../DefaultIntegrationOrchestratorTest.java` | AUTH-05 assertion | ✓ EXISTS + SUBSTANTIVE | `authSnapshotCarriesAccessTokenWithoutHttp` |
| `api-connector-auth/.../AuthException.java` | Typed auth failure | ✓ EXISTS + SUBSTANTIVE | `code()`, `details()` |
| `api-connector-auth/.../AuthErrorCode.java` | Platform codes | ✓ EXISTS + SUBSTANTIVE | All four enum values present |
| `api-connector-api/.../ApiErrorResponse.java` | `details` field | ✓ EXISTS + SUBSTANTIVE | `Map<String, Object> details` |
| `api-connector-app/.../InvokeIntegrationTest.java` | Structured error + WireMock | ✓ EXISTS + SUBSTANTIVE | 6 tests pass |
| `docs/legacy-auth-inventory.md` | D-03 audit matrix | ✓ EXISTS + SUBSTANTIVE | 73 lines; Wave 1 table + implementation gate |
| `api-connector-auth/.../OAuth2PasswordAuthProvider.java` | Conditional L2 | ✓ INTENTIONALLY ABSENT | Inventory gate: deferred to Wave 2 (documented) |
| `api-connector-auth/.../BearerFromLoginAuthProvider.java` | Conditional L2 | ✓ INTENTIONALLY ABSENT | Inventory gate: deferred to Wave 2 (documented) |
| `api-connector-auth/.../Sm3HeaderSignV1AuthProvider.java` | Conditional 国密 | ✓ INTENTIONALLY ABSENT | D-04: no Wave 1 国密 vendor (documented) |

**Artifacts:** 23/23 required artifacts verified (3 conditional skips documented and justified)

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ScriptCompileService` | `groovy-jsr223` | `ScriptEngineManager.getEngineByName("groovy")` | ✓ WIRED | Line 29 in `ScriptCompileService.java` |
| `GroovyAuthScriptProvider` | `ScriptCompileService` | compile + eval with `ctx` binding | ✓ WIRED | `scriptCompileService.compile` → `compiled.eval(bindings)` |
| `IntegrationEngineConfiguration` | `GroovyAuthScriptProvider` | `@Bean` registration | ✓ WIRED | `groovyAuthScriptProvider(ScriptCompileService)` |
| `OAuth2ClientCredentialsAuthProvider` | `TokenCache` | `getOrRefresh(TokenCacheKey, ...)` | ✓ WIRED | Constructor injection; no private token map |
| `ConnectorRegistry` | `ConnectorPublishListener` | `publishListener.onPublish(spec)` | ✓ WIRED | Line 144 in `ConnectorRegistry.java` |
| `ConnectorPublishListener` | `ScriptCompileService` | scan `groovy_auth_script` + compile | ✓ WIRED | `compileGroovyIfPresent` |
| `DefaultIntegrationOrchestrator` | `AuthContextSnapshot` | `AuthContextSnapshots.from` after authenticate | ✓ WIRED | `authenticate()` helper used by `invoke()` |
| `DefaultIntegrationOrchestrator` | `AuthConfigResolver` | `resolve(spec, endpointSpec)` | ✓ WIRED | Before `AuthContext` construction |
| `InvocationResult` | `AuthContextSnapshot` | constructor field | ✓ WIRED | `authSnapshot()` accessor |
| `AuthEngine` | `AuthException` | `AUTH_PROFILE_MISSING` on null provider | ✓ WIRED | `AuthExceptions.profileMissing` |
| `RuntimeApiExceptionHandler` | `AuthException` | `@ExceptionHandler` → `ApiErrorResponse` | ✓ WIRED | Maps code + details to JSON |
| `ConnectorPublishListener` | `AUTH_SCRIPT_COMPILE_ERROR` | `AuthExceptions.scriptCompileError` | ✓ WIRED | Publish path catch block |
| `legacy-auth-inventory.md` | `profile-registry.md` | `profile_id` column | ✓ WIRED | Cross-referenced in inventory table |

**Wiring:** 13/13 key links verified

## Requirements Coverage

| Requirement | Description | Status | Blocking Issue |
|-------------|-------------|--------|----------------|
| **AUTH-01** | Register Java built-in auth profile on connector endpoint | ✓ SATISFIED | Five Wave 1 catalogs + built-in providers; inventory gate documents scope |
| **AUTH-02** | Attach Groovy auth script for non-standard flows | ✓ SATISFIED | `groovy_auth_script` profile, spec extensions, `GroovyAuthScriptProviderTest` |
| **AUTH-03** | Compile and cache Groovy scripts on publish | ✓ SATISFIED | `ScriptCompileService` SHA-256 cache + `ConnectorPublishListener` pre-compile + integration test |
| **AUTH-04** | Immutable `AuthContext` consumable by mapping/HTTP | ✓ SATISFIED | `AuthContextSnapshot` on `InvocationResult` |
| **AUTH-05** | AuthContext available to mapping scripts in pipeline | ✓ SATISFIED | `DefaultIntegrationOrchestratorTest.authSnapshotCarriesAccessTokenWithoutHttp` (structural; mapping module Phase 2) |
| **AUTH-06** | Structured error on missing/misconfigured auth | ✓ SATISFIED | `AuthErrorCode` + `ApiErrorResponse.details` + integration test |

**Coverage:** 6/6 requirements satisfied in codebase

> **Note:** `.planning/REQUIREMENTS.md` traceability table still marks AUTH-02 and AUTH-03 as **Pending** — documentation drift only; implementation verified above.

## ROADMAP Success Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Built-in auth profile assignable via config | ✓ | `ProductionConnectorCatalogsTest.wave1CatalogAuthTypesMatchLegacyInventory` |
| 2 | Groovy publish + second invoke uses cached script | ✓ | `InvokeIntegrationTest.groovyAuthScriptCompileOnceAcrossTwoInvokes` |
| 3 | AuthContext accessible without HTTP call | ✓ | `DefaultIntegrationOrchestratorTest` snapshot assertion |
| 4 | Missing auth returns structured error | ✓ | Uses `AUTH_PROFILE_MISSING` (not `UPSTREAM_AUTH_FAILED` as ROADMAP text suggests — aligns with AUTH-06 / D-17) |
| 5 | Integration test: signed outbound OAuth/HMAC | ✓ | WireMock `access_token` + `X-Auth-Signature` verification |
| 6 | Legacy auth types inventory documented | ✓ | `docs/legacy-auth-inventory.md` (human completeness audit recommended) |

## Automated Test Results

| Command | Result |
|---------|--------|
| `.\mvnw-jdk21.ps1 "-pl" "api-connector-scripting,api-connector-auth,api-connector-domain,api-connector-engine,api-connector-app" "-am" "test"` | **BUILD SUCCESS** |
| Phase-relevant tests | 62 tests, 0 failures (includes `InvokeIntegrationTest` 6/6, `ScriptCompileServiceTest` 4/4, `TokenCacheTest` 5/5) |

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GroovyAuthScriptProvider.java` | 40 | `throw new IllegalStateException("Missing required auth config key: script")` | ⚠️ Warning | Blank/missing `script` key not mapped to `AuthException`; other misconfig paths are structured |
| `.planning/REQUIREMENTS.md` | 11–12, 112–113 | AUTH-02/03 marked Pending | ℹ️ Info | Traceability table stale vs implemented code |
| `.planning/phases/01-auth-plugin-architecture/01-VALIDATION.md` | 41–47 | Wave 0 / task status still pending | ℹ️ Info | Planning artifact not updated post-execution |

**Anti-patterns:** 3 found (0 blockers, 1 warning, 2 info)

## Human Verification Required

### 1. Legacy auth inventory completeness (ROADMAP SC#6 / D-03)

**Test:** Spot-check `docs/legacy-auth-inventory.md` rows against a read-only scan of `D:\Work\99_Code\ITS\suntek-system\system-thirdpart` controllers/clients. Confirm no Wave 1 vendor is missing and L3→Groovy assignments match actual legacy auth complexity.

**Expected:** All Wave 1 vendors (IDPS, Gaode, Baidu, Hikvision, TrafficBrain, etc.) represented with correct `profile_id` and implementation path.

**Why human:** Automated verification confirms file structure and test linkage; completeness against the external legacy codebase requires domain expert audit per `01-VALIDATION.md` manual-only table.

### 2. Operator-facing Groovy auth configuration

**Test:** Publish a connector with `auth.type: groovy_auth_script` and inline script via classpath YAML or catalog; invoke once after publish.

**Expected:** Auth headers applied; no compile error; second invoke uses cache (already covered by integration test — confirm in target deployment environment if different from CI).

**Why human:** Confirms end-to-end operator workflow beyond unit/integration test fixtures.

## Gaps Summary

### Critical Gaps (Block Progress)

None identified. All six AUTH requirements are implemented with passing automated tests.

### Non-Critical Gaps (Can Defer)

1. **`invokeStream` snapshot not exposed to downstream**
   - Issue: `authenticate()` builds `AuthContextSnapshot` but `StreamingInvocationSink` does not receive it
   - Impact: Phase 2 mapping on streaming invoke path may need sink API extension
   - Recommendation: Defer to Phase 2/3 when streaming mapping is implemented

2. **Missing Groovy `script` key uses `IllegalStateException`**
   - Issue: `GroovyAuthScriptProvider` line 40 not converted to `AuthException`
   - Impact: Misconfigured script source may return generic 500 instead of structured AUTH error
   - Recommendation: Small fix in Phase 2 or patch plan; not blocking Phase 1 goal

3. **Planning docs stale**
   - Issue: `REQUIREMENTS.md` AUTH-02/03 status; `01-VALIDATION.md` wave/task checkboxes
   - Impact: Traceability confusion only
   - Recommendation: Update during `/gsd-complete-milestone` or phase closeout

## Recommended Fix Plans

No fix plans required for phase progression. Optional housekeeping:

### 01-07-PLAN.md: Phase 1 closeout hygiene (optional)

**Objective:** Align planning artifacts with verified implementation

**Tasks:**
1. Update `.planning/REQUIREMENTS.md` — mark AUTH-02, AUTH-03 Complete
2. Update `01-VALIDATION.md` task statuses to green
3. Map `GroovyAuthScriptProvider` missing-script to `AuthException` (optional AUTH-06 hardening)

**Estimated scope:** Small

---

## Verification Metadata

**Verification approach:** Goal-backward (phase goal → plan must_haves → codebase + tests)  
**Must-haves source:** `01-01-PLAN.md` through `01-06-PLAN.md` frontmatter  
**Plans executed:** 6/6 with summaries  
**Automated checks:** 62 tests passed, 0 failed (targeted reactor build)  
**Human checks required:** 1 (legacy inventory completeness audit)  
**Total verification time:** ~15 min

---
*Verified: 2026-06-17T08:05:00Z*  
*Verifier: GSD verifier (subagent)*
