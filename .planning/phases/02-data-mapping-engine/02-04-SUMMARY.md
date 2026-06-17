---
phase: 02-data-mapping-engine
plan: "04"
subsystem: api
tags: [mapping, groovy, MappingScript, ConnectorPublishListener, MappingException]

requires:
  - phase: 02-03
    provides: MappingEngineImpl declarative facade, MappingContext, MappingSpecValidator
provides:
  - MappingScript SPI and GroovyMappingScriptProvider with ScriptCompileService reuse
  - MappingEngineImpl Groovy script dispatch with JSON serialization
  - ConnectorPublishListener mapping validation and compile-on-publish (D-09, D-29, D-30)
  - RuntimeApiExceptionHandler MappingException with code-based HTTP status (D-14)
affects: [02-05, Phase 3 orchestrator wiring]

tech-stack:
  added: [api-connector-mapping dependency on api-connector-api for MappingException handler]
  patterns: [auth Groovy symmetry — compile-once on publish, ctx bindings at runtime]

key-files:
  created:
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/spi/MappingScript.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/GroovyMappingScriptProvider.java
    - api-connector-mapping/src/test/java/com/suntek/apiconnector/mapping/GroovyMappingScriptProviderTest.java
    - api-connector-api/src/test/java/com/suntek/apiconnector/api/RuntimeApiExceptionHandlerTest.java
  modified:
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/MappingEngineImpl.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/exception/MappingExceptions.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/RuntimeApiExceptionHandler.java
    - api-connector-api/pom.xml

key-decisions:
  - "MappingScript returns Object (Map/List); MappingEngineImpl serializes to JSON string"
  - "Publish labels: {code3rd}:mapping:{direction} and {code3rd}:endpoint:{id}:mapping:{direction}"
  - "Orchestrator invoke wiring deferred to Phase 3 per plan"

patterns-established:
  - "Pattern: Groovy mapping mirrors auth — ScriptCompileService cache, ctx in SimpleBindings"
  - "Pattern: MappingSpecValidator + script contract validation on ConnectorPublishListener.onPublish"
  - "Pattern: MappingException HTTP mapping — spec/compile → 400, runtime/coerce → 502"

requirements-completed: [MAP-03, MAP-05]

duration: 55min
completed: 2026-06-17
---

# Phase 2 Plan 04: Groovy Mapping Scripts Summary

**MappingScript SPI, publish-time compile, and structured MappingException API errors**

## Performance

- **Duration:** 55 min
- **Started:** 2026-06-17T22:45:00+08:00
- **Completed:** 2026-06-17T23:06:00+08:00
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- Added `MappingScript` SPI and `GroovyMappingScriptProvider` with `ScriptCompileService` compile-once cache and `ctx` bindings including `authSnapshot().ext()`
- Wired `MappingEngineImpl` to dispatch Groovy scripts when direction has `script` (rules XOR script per D-10)
- Extended `ConnectorPublishListener` with `MappingSpecValidator`, per-direction script compile, and contract validation stub `{"sample":true}`
- Registered `DeclarativeRuleExecutor`, `GroovyMappingScriptProvider`, and `MappingEngine` beans in `IntegrationEngineConfiguration`
- Added `RuntimeApiExceptionHandler.handleMapping` mirroring auth handler pattern with `ApiErrorResponse` code/message/details

## Task Commits

Each task was committed atomically:

1. **Task 1: MappingScript SPI and GroovyMappingScriptProvider** - `3e01321` (feat)
2. **Task 2: Extend ConnectorPublishListener for mapping validation and compile** - `26eb03a` (feat)
3. **Task 3: MappingException API handler** - `3aa034c` (feat)

## Files Created/Modified

- `api-connector-mapping/.../spi/MappingScript.java` - Groovy script functional interface `apply(MappingContext)`
- `api-connector-mapping/.../GroovyMappingScriptProvider.java` - compile/eval adapter with Map/List/MappingScript return normalization
- `api-connector-mapping/.../MappingEngineImpl.java` - script path dispatch with compile labels and JSON output
- `api-connector-mapping/.../MappingExceptions.java` - `scriptCompileError` and `scriptRuntimeError` factories
- `api-connector-mapping/.../GroovyMappingScriptProviderTest.java` - MAP-03 legacy shape, authSnapshot, cache, invalid return type
- `api-connector-engine/.../ConnectorPublishListener.java` - mapping validate/compile on publish before auth scan
- `api-connector-engine/.../IntegrationEngineConfiguration.java` - MappingEngine bean registration
- `api-connector-engine/.../ConnectorPublishListenerTest.java` - mapping compile, invalid JSONPath, rules+script rejection
- `api-connector-api/.../RuntimeApiExceptionHandler.java` - `@ExceptionHandler(MappingException.class)`
- `api-connector-api/.../RuntimeApiExceptionHandlerTest.java` - 400 vs 502 status mapping

## Decisions Made

- Runtime compile labels include endpoint id when `MappingContext.endpoint()` present; cache keyed by script hash regardless of label
- Invalid mapping spec at publish wraps `IllegalArgumentException` as `MAPPING_SPEC_INVALID` (D-30)
- Orchestrator invoke wiring intentionally omitted — Phase 3 scope

## Deviations from Plan

None - plan executed as specified.

## Issues Encountered

- Groovy GString map values require explicit string concatenation in test scripts to avoid non-JSON-serializable return types
- PowerShell requires quoted `-pl` and `-Dtest` arguments for Maven reactor builds

## User Setup Required

None - no external service configuration required.

## Verification Results

```
.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=GroovyMappingScriptProviderTest
→ BUILD SUCCESS (4 tests)

.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=ConnectorPublishListenerTest
→ BUILD SUCCESS (7 tests)

.\mvnw-jdk21.ps1 -pl api-connector-mapping,api-connector-engine,api-connector-api -am test
→ BUILD SUCCESS (all module tests green)
```

## Next Phase Readiness

- Ready for **02-05**: error mapping direction, transform pipeline, or remaining MAP requirements
- `MappingEngine` bean available for Phase 3 orchestrator wiring (`mapRequest` → `transform[]` → `auth`)
- Publish path validates and pre-compiles mapping scripts without restart (MAP-05)

---
*Phase: 02-data-mapping-engine*
*Completed: 2026-06-17*
