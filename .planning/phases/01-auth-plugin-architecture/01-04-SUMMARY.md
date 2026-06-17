---
phase: 01-auth-plugin-architecture
plan: "04"
subsystem: auth
tags: [AuthContextSnapshot, AuthConfigResolver, InvocationResult, orchestrator, pipeline]

requires:
  - phase: 01-02
    provides: EndpointSpec.authOverride Groovy-only validation
  - phase: 01-03
    provides: OAuth providers populating ext map via TokenCache
provides:
  - Immutable AuthContextSnapshot with standard ext keys on InvocationResult
  - AuthConfigResolver for endpoint authOverride vs connector auth
  - Orchestrator carry-forward for invoke and invokeStream
affects: [01-05, 01-06, phase-2-mapping]

tech-stack:
  added: []
  patterns: [AuthContextSnapshot in domain, AuthContextSnapshots factory in engine, optional auth on InvocationResult]

key-files:
  created:
    - api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/AuthContextSnapshot.java
    - api-connector-domain/src/test/java/com/suntek/apiconnector/domain/model/AuthContextSnapshotTest.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/AuthConfigResolver.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/AuthContextSnapshots.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/AuthConfigResolverTest.java
  modified:
    - api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/AuthOutcome.java
    - api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/InvocationResult.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/EndpointResolver.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestratorTest.java
    - api-connector-auth/** (AuthOutcome import migration)

key-decisions:
  - "AuthOutcome moved to api-connector-domain to allow InvocationResult accessors without domain→auth cycle"
  - "AuthContextSnapshots.from() lives in engine; AuthContextSnapshot.of() in domain for map-based construction"
  - "Shared authenticate() private method mirrors auth path in invoke and invokeStream"

patterns-established:
  - "Pattern: AuthConfigResolver.resolve(spec, endpoint) prefers endpoint authOverride when present"
  - "Pattern: Mutable HashMap ext on AuthContext during auth; immutable AuthContextSnapshot after authenticate"
  - "Pattern: InvocationResult optional authSnapshot() and authOutcome() for Phase 2 mapping hook"

requirements-completed: [AUTH-04, AUTH-05]

duration: 45min
completed: 2026-06-17
---

# Phase 1 Plan 04 Summary

**Immutable AuthContextSnapshot and auth outcome carried on InvocationResult via orchestrator auth resolution**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-06-17T05:49:00Z
- **Completed:** 2026-06-17T06:04:00Z
- **Tasks:** 3
- **Files modified:** 15

## Accomplishments

- Added `AuthContextSnapshot` record with unmodifiable `ext` map and D-11 standard keys (`accessToken`, `tokenExpiresAt`, `oauthRawResponse`, `signatureBase`, `appliedHeaders`)
- Implemented `AuthConfigResolver` for Groovy-only endpoint `authOverride` vs connector `auth`
- Extended `InvocationResult` with optional `authSnapshot()` and `authOutcome()` accessors
- Wired `DefaultIntegrationOrchestrator` to resolve endpoint, authenticate with mutable `ext`, build snapshot, and attach to result for `invoke` and `invokeStream`
- Proved AUTH-05 structurally: unit test reads `accessToken` from snapshot without HTTP

## Task Commits

1. **Task 1: Implement AuthContextSnapshot immutable record in domain** - `8a49a25` (feat)
2. **Task 2: AuthConfigResolver and extend InvocationResult** - `2b2c6d6` (feat)
3. **Task 3: Wire orchestrator carry-forward for invoke and invokeStream** - `959c021` (test)

## Files Created/Modified

- `AuthContextSnapshot.java` — immutable post-auth carrier with `of()` factory for ext/credential refs
- `AuthContextSnapshots.java` — engine `from(AuthContext, AuthOutcome, profileTypes)` avoiding domain→auth cycle
- `AuthConfigResolver.java` — endpoint override resolution
- `InvocationResult.java` — backward-compatible overload with optional auth fields
- `DefaultIntegrationOrchestrator.java` — `EndpointResolver` + `AuthConfigResolver` + snapshot on result
- `AuthContextSnapshotTest.java` — accessToken preservation and ext immutability
- `AuthConfigResolverTest.java` — override vs connector auth
- `DefaultIntegrationOrchestratorTest.java` — AUTH-05 snapshot assertion and override path

## Decisions Made

- Moved `AuthOutcome` from `api-connector-auth` to `api-connector-domain` so `InvocationResult` can expose it without circular Maven dependency
- Placed `AuthContextSnapshots.from()` in engine per RESEARCH cycle constraint; domain record uses `of()` for map-based construction
- Extracted private `authenticate()` helper shared by `invoke()` and `invokeStream()`

## Deviations from Plan

### AuthOutcome module relocation

- **Plan implied:** `AuthOutcome` remains in auth module; `InvocationResult` exposes optional `authOutcome()`
- **Implemented:** `AuthOutcome` relocated to `api-connector-domain` with import updates across auth/engine
- **Impact:** Eliminates domain↔auth cycle; aligns with "domain owns pipeline carrier types"

### Factory location

- **Plan specified:** `AuthContextSnapshot.from(AuthContext, AuthOutcome, List)` on domain record
- **Implemented:** `AuthContextSnapshot.of(...)` in domain; `AuthContextSnapshots.from(...)` in engine
- **Impact:** Same behavior; domain tests use `of()` with maps per cycle constraint

## Issues Encountered

- PowerShell requires quoting `-pl` and `-Dtest` arguments with commas
- `HttpTransport` is not a functional interface — test stubs use anonymous classes

## User Setup Required

None

## Next Phase Readiness

- Plan 01-05 can add structured `AuthException` and platform error codes
- Plan 01-06 can add ROADMAP SC#2 integration test and legacy auth inventory
- Phase 2 mapping can read `InvocationResult.authSnapshot().ext()` without re-fetching tokens

## Self-Check: PASSED

- `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test` exits 0
- `.\mvnw-jdk21.ps1 -pl api-connector-domain -am test -Dtest=AuthContextSnapshotTest` exits 0
- Endpoint `authOverride` used when present (`AuthConfigResolverTest`, `DefaultIntegrationOrchestratorTest`)

---
*Phase: 01-auth-plugin-architecture*
*Completed: 2026-06-17*
