---
phase: 02-data-mapping-engine
plan: "05"
subsystem: api
tags: [mapping, error-mapping, LegacySuntekResult, hasAnyMapping, passthrough, ErrorMappingTrigger]

requires:
  - phase: 02-04
    provides: MappingEngineImpl Groovy dispatch, MappingEngine SPI
provides:
  - ErrorMappingTrigger record with shouldMapError gate (D-15, D-19)
  - Complete mapError with trigger param and passthrough paths (D-18, MAP-04)
  - Error mapping resolution tests with endpoint override (D-17)
  - hasAnyMapping passthrough detection tests (D-23..D-26, MAP-07)
affects: [Phase 3 orchestrator wiring, DefaultIntegrationOrchestrator]

tech-stack:
  added: []
  patterns: [error trigger matrix, orchestrator guard pseudocode, per-direction override independence]

key-files:
  created:
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/ErrorMappingTrigger.java
    - api-connector-mapping/src/test/java/com/suntek/apiconnector/mapping/ErrorMappingEngineTest.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/PassthroughMappingGuardTest.java
  modified:
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/MappingEngineImpl.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/spi/MappingEngine.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/MappingConfigResolverTest.java

key-decisions:
  - "mapError returns rawBody when trigger indicates success — caller gate deferred to Phase 3 orchestrator"
  - "PassthroughMappingGuardTest uses RecordingMappingEngine stub instead of Mockito (not in project BOM)"
  - "LegacySuntekResult shape validated via Jackson JsonNode in mapping tests (no api module circular dep)"

patterns-established:
  - "Pattern 9: ErrorMappingTrigger.shouldMapError — HTTP >= 400 OR !businessSuccess"
  - "Pattern 10: hasAnyMapping false for transform-only and empty mapping {} specs"

requirements-completed: [MAP-04, MAP-07]

duration: 35min
completed: 2026-06-17
---

# Phase 2 Plan 05: Error Mapping and Passthrough Guard Summary

**Vendor errors map to LegacySuntekResult JSON via ErrorMappingTrigger gate; hasAnyMapping passthrough API ready for Phase 3 orchestrator**

## Performance

- **Duration:** 35 min
- **Started:** 2026-06-17T23:15:00+08:00
- **Completed:** 2026-06-17T23:50:00+08:00
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Added `ErrorMappingTrigger` with `shouldMapError` aligned to `ResponseEvaluator` semantics (HTTP 4xx/5xx or business failure)
- Completed `MappingEngineImpl.mapError` with trigger gate, declarative rules, Groovy scripts, and D-18 passthrough when no `mapping.error`
- `ErrorMappingEngineTest` proves vendor `errCode`/`errMsg` → legacy `code`/`message`/`success:false` (MAP-04, SC#3)
- Extended `MappingConfigResolverTest` for error override precedence and per-direction independence (D-17)
- Added `PassthroughMappingGuardTest` documenting orchestrator short-circuit without `DefaultIntegrationOrchestrator` changes (MAP-07)

## Task Commits

Each task was committed atomically:

1. **Task 1: ErrorMappingTrigger and mapError implementation** - `2ec870e` (feat)
2. **Task 2: Endpoint error mapping override resolution** - `11b00ab` (test)
3. **Task 3: Passthrough hasAnyMapping guard tests** - `bd82d0e` (test)

## Files Created/Modified

- `api-connector-mapping/.../ErrorMappingTrigger.java` - HTTP status + business success gate input
- `api-connector-mapping/.../MappingEngineImpl.java` - Complete mapError with trigger and passthrough
- `api-connector-mapping/.../spi/MappingEngine.java` - mapError signature includes ErrorMappingTrigger
- `api-connector-mapping/.../ErrorMappingEngineTest.java` - Legacy shape, trigger matrix, Groovy error path
- `api-connector-engine/.../MappingConfigResolverTest.java` - Error override, empty mapping, transform-only
- `api-connector-engine/.../PassthroughMappingGuardTest.java` - Orchestrator guard pseudocode with recording stub

## Decisions Made

- `mapError` returns `rawBody` when `!shouldMapError(trigger)` rather than throwing — safer for Phase 3 integration
- Used `RecordingMappingEngine` test double instead of Mockito to avoid new test dependency
- Legacy shape assertions use Jackson `JsonNode` in mapping module (cannot depend on `api-connector-api`)

## Deviations from Plan

None - plan executed exactly as specified.

## Issues Encountered

- PowerShell requires quoted Maven `-D` properties and `-pl` module lists when running targeted tests
- Git HEREDOC commit messages fail on PowerShell; used `-m` title + `-m` body instead

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Error mapping API complete; Phase 3 orchestrator can wire `shouldMapError && hasError()` → `mapError`
- `hasAnyMapping()` passthrough detection proven; orchestrator short-circuit pattern documented in `PassthroughMappingGuardTest`
- No blockers for Phase 3 `DefaultIntegrationOrchestrator` invoke path integration

---
*Phase: 02-data-mapping-engine*
*Completed: 2026-06-17*
