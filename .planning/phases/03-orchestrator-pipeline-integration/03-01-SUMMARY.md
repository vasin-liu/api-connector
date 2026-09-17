---
phase: 03-orchestrator-pipeline-integration
plan: 01
subsystem: api
tags: [spring-boot, maven, orchestrator, mapping, transform, hmac, slf4j, concurrenthashmap, wiremock, junit5]

# Dependency graph
requires:
  - phase: 01-auth
    provides: AuthEngine, AuthContext, AuthOutcome, HMAC/aksk signing, TokenCache eviction pattern
  - phase: 02-mapping-transform
    provides: MappingEngine SPI, TransformPipeline, MappingConfigResolver, ResolvedMapping, ErrorMappingTrigger, EndpointMeta
provides:
  - Wired DefaultIntegrationOrchestrator.invoke() running resolve -> mapRequest -> transform.applyRequest -> auth -> HTTP -> transform.applyResponse -> evaluate -> mapResponse/mapError
  - HMAC/signature computed over the mapped+transformed outbound body (MAP-06)
  - ResolvedMappingCache (ConcurrentHashMap, keyed code3rd:endpointId) with publish-time invalidation
  - Global integration.invoke.mapping-enabled toggle (default true)
  - DEBUG-only stage-timing logger (stage + millis, no bodies/credentials)
affects: [03-02-audit-correlation, 03-03-legacy-route, 03-04-streaming]

# Tech tracking
tech-stack:
  added: [slf4j-api direct dependency in api-connector-engine]
  patterns: [interface-first cache contract, back-compat widening constructor with passthrough defaults, RED->GREEN TDD wave]

key-files:
  created:
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ResolvedMappingCache.java
  modified:
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    - api-connector-app/src/main/resources/application.yml
    - api-connector-engine/pom.xml
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestratorTest.java
    - api-connector-app/src/test/java/com/suntek/apiconnector/app/InvokeIntegrationTest.java

key-decisions:
  - "Request body finalized (mapRequest + transform.applyRequest) BEFORE authenticate(...) so HMAC signs the body actually sent (MAP-06/D-01)"
  - "Engine reads the raw property via @Value(${integration.invoke.mapping-enabled:true}); never imports api-layer IntegrationInvokeProperties"
  - "Null endpointSpec (legacy raw-path dispatch) stays a mapping passthrough rather than resolving a mapping key"
  - "invokeStream left untouched this plan; streaming wiring is 03-04 scope"

patterns-established:
  - "Interface-first: ResolvedMappingCache contract created before orchestrator consumes it"
  - "Back-compat widening constructor: 4-arg ctor delegates to 8-arg with null/false passthrough defaults so existing unit harness compiles"
  - "Publish invalidation co-located: resolvedMappingCache.evict(code3rd) beside tokenCache.evictForConnector(code3rd)"
  - "DEBUG stage timing logs stage name + millis only, never bodies/credentials (D-23/Security V7)"

requirements-completed: [MAP-06, PIPE-01]

# Metrics
duration: 42min
completed: 2026-06-18
---

# Phase 03 Plan 01: Orchestrator Pipeline Integration Summary

**DefaultIntegrationOrchestrator now runs the full mapping/transform pipeline around auth so HMAC signs the mapped outbound body, with a publish-invalidated ResolvedMappingCache and a global mapping-enabled toggle**

## Performance

- **Duration:** 42 min
- **Started:** 2026-06-18T11:55:14+08:00
- **Completed:** 2026-06-18T12:37:01+08:00
- **Tasks:** 3
- **Files modified:** 8 (1 created, 7 modified)

## Accomplishments
- Unified invoke pipeline wired end-to-end: resolve -> mapRequest -> transform.applyRequest -> auth -> HTTP -> transform.applyResponse -> evaluate -> mapResponse/mapError (PIPE-01)
- HMAC/signature computed over the mapped+transformed body, verified end-to-end via WireMock `withRequestBody(matchingJsonPath("$.b")).withHeader("X-Auth-Signature", matching("[0-9a-f]{64}"))` (MAP-06)
- Passthrough endpoints (`hasAnyMapping == false`) skip the mapping engine entirely (D-04); `mapping-enabled=false` forces whole-pipeline passthrough (D-22)
- Response side mirrors strictly: decrypt -> evaluate -> error routing via `ErrorMappingTrigger.shouldMapError` (D-02/D-03)
- `ResolvedMappingCache` (ConcurrentHashMap) resolves once per connector+endpoint, invalidated on connector publish (D-21)
- DEBUG-only stage-timing logger emits stage + millis, never bodies or credentials (D-23)

## Task Commits

Each task was committed atomically:

1. **Task 1: Failing ordering + passthrough + response-mirror tests (RED)** - `f48ea2a` (test)
2. **Task 2: ResolvedMappingCache + publish invalidation + bean wiring** - `d0f142f` (feat)
3. **Task 3: Insert request + response pipeline stages in orchestrator (GREEN)** - `20a0b05` (feat)

**Regression fix (Rule 1, plan verification):** `ad9926f` (fix) — null endpointSpec passthrough guard

**Plan metadata:** see final `docs(03-01)` commit.

## Files Created/Modified
- `api-connector-engine/.../ResolvedMappingCache.java` - NEW: ConcurrentHashMap cache keyed `code3rd:endpointId`, `get(spec, endpoint)` via computeIfAbsent, `evict(code3rd)` drops prefixed keys
- `api-connector-engine/.../DefaultIntegrationOrchestrator.java` - Wired request/response pipeline stages, 8-arg constructor + back-compat 4-arg, mappingActive gate, DEBUG stage logger
- `api-connector-engine/.../ConnectorPublishListener.java` - Wider constructor accepting ResolvedMappingCache; evict beside tokenCache eviction in onPublishInternal
- `api-connector-engine/.../config/IntegrationEngineConfiguration.java` - resolvedMappingCache() bean; orchestrator + publish-listener bean wiring with MappingEngine/TransformPipeline + mapping-enabled @Value
- `api-connector-app/src/main/resources/application.yml` - `integration.invoke.mapping-enabled: true` toggle (D-22)
- `api-connector-engine/pom.xml` - slf4j-api direct dependency (D-23 logging)
- `api-connector-engine/.../DefaultIntegrationOrchestratorTest.java` - RED tests + test-harness fix
- `api-connector-app/.../InvokeIntegrationTest.java` - hmacSignsMappedRequestBody + unifiedInvokeRunsFullPipeline

## Decisions Made
- Engine reads the raw `${integration.invoke.mapping-enabled:true}` property directly via `@Value`, never importing the api-layer `IntegrationInvokeProperties` (RESEARCH Open Q1).
- `invokeStream` deferred to 03-04 — only `invoke()` is wired this plan, matching the test scope.
- A null `endpointSpec` (legacy raw-path dispatch) is treated as a mapping passthrough rather than attempting cache resolution.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added slf4j-api as a direct engine dependency**
- **Found during:** Task 3 (orchestrator GREEN)
- **Issue:** `package org.slf4j does not exist` — engine module had no direct SLF4J access, but D-23/Security V7 mandate SLF4J `LoggerFactory` stage-timing logs (explicitly "not Lombok")
- **Fix:** Added `org.slf4j:slf4j-api` dependency to `api-connector-engine/pom.xml`
- **Files modified:** api-connector-engine/pom.xml
- **Verification:** Engine compiles; orchestrator logs via SLF4J; full build green
- **Committed in:** `20a0b05` (Task 3 commit)

**2. [Rule 1 - Bug] Test harness called context.body() instead of context.requestBody()**
- **Found during:** Task 3 (compiling Task 1 RED tests against the real AuthContext)
- **Issue:** `RecordingNoneAuthProvider` referenced a non-existent `AuthContext.body()` accessor
- **Fix:** Changed to `context.requestBody()`
- **Files modified:** api-connector-engine/.../DefaultIntegrationOrchestratorTest.java
- **Verification:** Unit tests compile and pass (3/3 green)
- **Committed in:** `20a0b05` (Task 3 commit)

**3. [Rule 1 - Bug/Regression] Null endpointSpec NPE on legacy dispatch path**
- **Found during:** Plan-level wave-merge verification (`-pl api-connector-app -am test`)
- **Issue:** `LegacyCompatIntegrationTest.legacyIdpsGetProxiesVendorJson` returned 500 — `resolvedMappingCache.get(spec, endpointSpec)` NPE'd on `endpoint.id()` because the legacy route invokes with a null EndpointSpec
- **Fix:** Guard the cache lookup with `endpointSpec != null`; a null endpoint stays a mapping passthrough (D-04), transforms still apply via `spec.transform()`
- **Files modified:** api-connector-engine/.../DefaultIntegrationOrchestrator.java
- **Verification:** Full `api-connector-app -am test` green — 23 tests, 0 failures
- **Committed in:** `ad9926f` (dedicated fix commit)

---

**Total deviations:** 3 auto-fixed (2 Rule 1 bugs, 1 Rule 3 blocking)
**Impact on plan:** All auto-fixes necessary for correctness/compilation and required by the plan's own D-23 logging mandate. No scope creep — `invokeStream` and legacy mapping remain deferred to their planned phases.

## Issues Encountered
- Module-scoped Maven runs needed `-am` (build upstream sibling modules) and `-Dsurefire.failIfNoSpecifiedTests=false` (avoid failing modules lacking the named test) — Maven build-config only, no source impact.
- The targeted GREEN gate (`DefaultIntegrationOrchestratorTest+InvokeIntegrationTest`) passed before the full wave-merge surfaced the legacy NPE regression, reinforcing the value of running the plan-level wave-merge verification.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Unified `invoke()` pipeline is the lead vertical slice; 03-02 (audit/correlation), 03-03 (legacy route), and 03-04 (streaming) build on this wiring.
- `invokeStream` still uses `request.body()` for auth and does not yet run mapping/transform — must be wired in 03-04 to extend MAP-06 ordering to streaming.

---
*Phase: 03-orchestrator-pipeline-integration*
*Completed: 2026-06-18*

## Self-Check: PASSED

- 03-01-SUMMARY.md exists on disk.
- All task commits verified via `git log --grep="(03-01)"`: `f48ea2a` (Task 1 RED), `d0f142f` (Task 2), `20a0b05` (Task 3 GREEN), `ad9926f` (Rule 1 regression fix).
- Plan-level verification green: `api-connector-app -am test` — 23 tests, 0 failures.
- requirements-completed = [MAP-06, PIPE-01] (verbatim from PLAN frontmatter).
