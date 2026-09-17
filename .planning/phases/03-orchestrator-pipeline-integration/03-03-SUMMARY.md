---
phase: 03-orchestrator-pipeline-integration
plan: 03
subsystem: testing
tags: [legacy-compat, thirdpart, orchestrator, mapping, wiremock, spring-boot, integration-test]

# Dependency graph
requires:
  - phase: 03-01
    provides: orchestrator mapping/transform/auth wired into IntegrationInvokeService.invoke()
  - phase: 03-02
    provides: correlation-id capture + audit outcome classification reused by the legacy path
provides:
  - Parity guard proving legacy thirdpart URL and unified API share one orchestrator pipeline (D-06/D-08)
  - Envelope-only error guard proving mapping.error owns business-error JSON once; formatter is transport-only (D-09)
  - Path-based legacy endpointId resolution in ThirdpartLegacyDispatcher (Option A) so matched legacy routes map
affects: [legacy-compat, thirdpart-migration, future-connector-onboarding]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Legacy adapter resolves a unique (method, path) -> endpointId before delegating to shared invoke; ambiguous/absent matches stay null (D-04 passthrough preserved)"
    - "Parity asserted on CORE result (success/vendorCode/mapped body), never on envelope byte-identity (D-08)"

key-files:
  created: []
  modified:
    - api-connector-app/src/test/java/com/suntek/apiconnector/app/LegacyCompatIntegrationTest.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/legacy/ThirdpartLegacyDispatcher.java

key-decisions:
  - "Option A: resolve endpointId by unique (method, path) match in the legacy adapter rather than changing the engine, preserving the D-04 null-endpointId passthrough invariant"
  - "Parity test asserts equal mapped core body, not envelope byte-identity (D-08)"
  - "Task 1 parity test folded into the Option A production-fix commit (0f1ad4a); Task 2 error guard committed separately"

patterns-established:
  - "api/legacy/* adapters contain zero mapping/transform calls — confirmed by grep; mapping is owned solely by the shared orchestrator (D-07)"
  - "Legacy error responses carry the mapping.error shape exactly once; LegacyCompatResponseFormatter adds only the transport envelope (D-09)"

requirements-completed: [PIPE-02]

# Metrics
duration: 31min
completed: 2026-06-18
---

# Phase 03 Plan 03: Legacy Compat Pipeline Parity Summary

**Legacy thirdpart URLs and the unified API proven to share one orchestrator pipeline (identical mapped core result), with a single-mapping envelope-only error guard and a path-based legacy endpointId resolution fix.**

## Performance

- **Duration:** ~31 min
- **Started:** 2026-06-18T14:47:23+08:00 (first 03-03 commit)
- **Completed:** 2026-06-18T15:15:04+08:00 (Task 2 commit) + metadata commit
- **Tasks:** 2
- **Files modified:** 2 (1 test, 1 production adapter via Option A)

## Accomplishments
- `legacyAndUnifiedCoreResultParity`: same WireMock-backed endpoint invoked via the unified API and the legacy `/idps/...` URL returns equal `success`, `vendorCode`, and mapped body (`raw` -> `mapped`), proving both ran request/response mapping through the same `orchestrator.invoke()` (D-06/D-08).
- `legacyErrorMappedOnceEnvelopeOnly`: a vendor business error via the legacy URL carries the `mapping.error` shape exactly once; the legacy body equals the mapped error verbatim, proving `LegacyCompatResponseFormatter` is transport/envelope-only (D-09).
- Confirmed (grep) that `api/legacy/*` adapters contain no mapping/transform engine references — mapping lives solely in the shared pipeline (D-07).
- Option A production fix: `ThirdpartLegacyDispatcher.resolveEndpointId` resolves a uniquely matching enabled endpoint's id so matched legacy routes get mapping, while ambiguous/absent matches stay null (D-04 passthrough preserved).

## Task Commits

1. **Task 1: Legacy vs unified core-result parity test (D-06/D-08)** + Option A production fix - `0f1ad4a` (feat) — parity test folded into the production-fix commit
2. **Task 2: No-double-mapping error envelope guard (D-07/D-09)** - `9c3e9d9` (test)

**Plan metadata:** `docs(03-03): complete legacy compat parity plan` (this commit)

## Files Created/Modified
- `api-connector-app/src/test/java/com/suntek/apiconnector/app/LegacyCompatIntegrationTest.java` - Added `legacyAndUnifiedCoreResultParity` (D-06/D-08) and `legacyErrorMappedOnceEnvelopeOnly` (D-09) plus `registerMappedIdps`/`registerErrorMappedIdps` WireMock fixtures.
- `api-connector-api/src/main/java/com/suntek/apiconnector/api/legacy/ThirdpartLegacyDispatcher.java` - Added `resolveEndpointId` (Option A): unique (method, path) -> endpointId before shared invoke; ambiguous/absent stay null.

## Decisions Made
- **Option A over engine change:** resolve the legacy endpointId in the adapter, not by relaxing the engine's null-endpointId passthrough, keeping the D-04 invariant intact for all existing free-path/IDPS-style routes.
- **Parity on core result, not envelope:** `LegacySuntekResult`/raw-body envelope is allowed to differ (D-08); only `success`/`vendorCode`/mapped body are asserted equal.
- **Test commit split:** the parity test was committed together with the Option A fix (`0f1ad4a`) because it only goes green with that fix; the error guard is a clean test-only follow-up (`9c3e9d9`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 4 - Architectural] Plan's "no new pipeline code" premise was false; legacy routes did not map**
- **Found during:** Task 1 (parity test) — the test was RED.
- **Issue:** The plan assumed 03-01's wiring already gave legacy mapping "for free" (D-06), so no production change was expected. In reality `ThirdpartLegacyDispatcher` dispatched with a null `endpointId`, which the engine treats as a mapping passthrough (D-04). Legacy URLs that DO correspond to a configured, mapped endpoint silently skipped request/response mapping, so the parity test could never pass without a production change.
- **Fix:** Added `resolveEndpointId(code3rd, method, path)` to `ThirdpartLegacyDispatcher`: when exactly one enabled endpoint matches the already-adapted (method, path), its id is set on the `ProxyInvokeRequest` so the shared orchestrator runs mapping. Zero matches or multiple matches both leave `endpointId` null, preserving the engine's D-04 passthrough invariant unchanged for every existing legacy route. No engine code was touched; mapping ownership stays in the shared pipeline (D-07 intact).
- **Files modified:** api-connector-api/src/main/java/com/suntek/apiconnector/api/legacy/ThirdpartLegacyDispatcher.java
- **Verification:** `LegacyCompatIntegrationTest` GREEN (exit 0); audit logs confirm legacy invoke runs `endpointId=parity context=LEGACY` (mapped) while unmatched `/brain-auth/ping` stays `endpointId=null` (passthrough).
- **Committed in:** `0f1ad4a` (Option A production fix + Task 1 parity test)

---

**Total deviations:** 1 auto-fixed (1 architectural — Rule 4)
**Impact on plan:** The fix is minimal, scoped to the legacy adapter, and preserves the engine's passthrough invariant; no scope creep. It corrected a false premise in the plan rather than adding new behavior beyond PIPE-02's intent.

## Issues Encountered
- Full Spring Boot integration test cold-start is slow on Windows (~55s context start, ~10 min wall including multi-module `-am` compile); resolved by running with a generous timeout and confirming GREEN via exit code + audit-line evidence for all invoke scenarios.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- PIPE-02 satisfied: legacy compat filter URL hits the same pipeline with an identical core outcome as the unified API; divergence/double-mapping regression guards are in place.
- No blockers for subsequent 03 phase work.

## Self-Check: PASSED

---
*Phase: 03-orchestrator-pipeline-integration*
*Completed: 2026-06-18*
