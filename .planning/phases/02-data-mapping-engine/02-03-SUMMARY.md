---
phase: 02-data-mapping-engine
plan: "03"
subsystem: api
tags: [mapping, array_map, MappingEngine, MappingConfigResolver, jsonpath]

requires:
  - phase: 02-02
    provides: DeclarativeRuleExecutor rename/set/coerce/nest ops, MappingException hierarchy
provides:
  - array_map op with nested per-element rule application (MAP-02)
  - MappingContext record with bodyAsMap() for Groovy bindings
  - MappingEngine SPI and MappingEngineImpl declarative dispatch facade
  - ResolvedMapping in api-connector-mapping (avoids engine circular dep)
  - MappingConfigResolver with per-direction override and hasAnyMapping passthrough API
affects: [02-04, 02-05, Phase 3 orchestrator wiring]

tech-stack:
  added: [jackson-databind on api-connector-domain for MappingContext]
  patterns: [per-direction endpoint override merge, script path stubbed until Plan 04]

key-files:
  created:
    - api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/MappingContext.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/ResolvedMapping.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/spi/MappingEngine.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/MappingEngineImpl.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/MappingConfigResolver.java
    - api-connector-mapping/src/test/java/com/suntek/apiconnector/mapping/ArrayMapRuleTest.java
    - api-connector-mapping/src/test/java/com/suntek/apiconnector/mapping/MappingEngineImplTest.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/MappingConfigResolverTest.java
  modified:
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/DeclarativeRuleExecutor.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/validation/MappingSpecValidator.java
    - api-connector-domain/pom.xml
    - api-connector-engine/pom.xml

key-decisions:
  - "ResolvedMapping lives in api-connector-mapping (not engine) to avoid circular Maven dependency"
  - "array_map applies nested rules per element via recursive applyRules on element JSON"
  - "Groovy script directions throw UnsupportedOperationException until Plan 02-04"

patterns-established:
  - "Pattern: MappingConfigResolver merges connector mapping with per-direction endpoint override (D-01)"
  - "Pattern: hasAnyMapping() false when no direction has rules or script (D-23 passthrough guard)"
  - "Pattern: MappingEngineImpl dispatches declarative rules only; script path deferred"

requirements-completed: [MAP-02]

duration: 45min
completed: 2026-06-17
---

# Phase 2 Plan 03: Array Map and Mapping Facade Summary

**array_map nested array transforms, MappingEngine declarative facade, and MappingConfigResolver with hasAnyMapping passthrough API**

## Performance

- **Duration:** 45 min
- **Started:** 2026-06-17T22:30:00+08:00
- **Completed:** 2026-06-17T22:41:00+08:00
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- Implemented `array_map` in `DeclarativeRuleExecutor` with lenient missing-source handling and recursive nested rule application per array element
- Added `MappingContext` record with `body()` / `bodyAsMap()` helpers for Groovy script bindings (Plan 04)
- Created `MappingEngine` SPI and `MappingEngineImpl` with declarative dispatch per request/response/error direction
- Placed `ResolvedMapping` in `api-connector-mapping` with per-direction rules/script holders and `hasRequest/hasResponse/hasError` helpers
- Implemented `MappingConfigResolver` mirroring auth override semantics with per-direction endpoint override precedence and `hasAnyMapping()` API

## Task Commits

Each task was committed atomically:

1. **Task 1: array_map operator in DeclarativeRuleExecutor** - `8247262` (feat)
2. **Task 2: MappingContext and MappingEngine facade** - `b18f958` (feat)
3. **Task 3: MappingConfigResolver and hasAnyMapping** - `52337c9` (feat)

## Files Created/Modified

- `api-connector-mapping/.../DeclarativeRuleExecutor.java` - array_map op with per-element nested rule recursion
- `api-connector-mapping/.../validation/MappingSpecValidator.java` - require target path for array_map
- `api-connector-mapping/.../ArrayMapRuleTest.java` - MAP-02 nested array and nested object tests
- `api-connector-domain/.../MappingContext.java` - pipeline input record with Jackson bodyAsMap()
- `api-connector-mapping/.../ResolvedMapping.java` - resolved per-direction config holder
- `api-connector-mapping/.../spi/MappingEngine.java` - mapRequest/mapResponse/mapError SPI
- `api-connector-mapping/.../MappingEngineImpl.java` - declarative dispatch facade
- `api-connector-mapping/.../MappingEngineImplTest.java` - end-to-end request/response mapping tests
- `api-connector-engine/.../MappingConfigResolver.java` - connector + endpoint override resolution
- `api-connector-engine/.../MappingConfigResolverTest.java` - override precedence and hasAnyMapping tests
- `api-connector-engine/pom.xml` - added api-connector-mapping dependency
- `api-connector-domain/pom.xml` - added jackson-databind for MappingContext

## Decisions Made

- `ResolvedMapping` placed in `api-connector-mapping` per plan checker to avoid engine↔mapping circular Maven dependency
- Script-configured directions throw `UnsupportedOperationException` until Groovy provider wired in Plan 02-04
- Endpoint `mappingOverride` replaces connector mapping per direction only when that direction block is present

## Deviations from Plan

None - plan executed as specified.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Verification Results

```
.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=ArrayMapRuleTest
→ BUILD SUCCESS (3 tests)

.\mvnw-jdk21.ps1 -pl api-connector-mapping,api-connector-domain -am test -Dtest=MappingEngineImplTest
→ BUILD SUCCESS (4 tests)

.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=MappingConfigResolverTest
→ BUILD SUCCESS (4 tests)

.\mvnw-jdk21.ps1 -pl api-connector-mapping,api-connector-engine -am test
→ BUILD SUCCESS (39 tests across mapping + engine modules)
```

## Next Phase Readiness

- Ready for **02-04**: `GroovyMappingScriptProvider`, publish listener mapping script compile, `MappingException` API handler
- `MappingConfigResolver.hasAnyMapping()` ready for Phase 3 orchestrator passthrough short-circuit
- `MappingEngineImpl` ready to wire Groovy script path when `ResolvedDirection.compiledScript` populated on publish

---
*Phase: 02-data-mapping-engine*
*Completed: 2026-06-17*
