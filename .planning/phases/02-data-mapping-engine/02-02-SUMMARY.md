---
phase: 02-data-mapping-engine
plan: "02"
subsystem: api
tags: [mapping, jsonpath, coerce, declarative-rules, maven]

requires:
  - phase: 02-01
    provides: MappingSpec models, MappingRule, MappingSpecValidator, Jayway validation patterns
provides:
  - MappingDirection and EndpointMeta domain types
  - MappingException hierarchy with MappingErrorCode and MappingExceptions factory
  - PathNormalizer for bare-field JSONPath normalization
  - CoerceHelper strict string/number/boolean/date coercion
  - DeclarativeRuleExecutor with rename, set, coerce, nest ops
  - DeclarativeRuleExecutorTest proving MAP-01 rename + coerce
affects: [02-03, 02-04, MappingEngineImpl, ConnectorPublishListener]

tech-stack:
  added: [Jayway DocumentContext put/write path]
  patterns: [lenient missing source, strict coerce failure, sequential rule order D-07]

key-files:
  created:
    - api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/MappingDirection.java
    - api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/EndpointMeta.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/exception/MappingErrorCode.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/exception/MappingException.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/exception/MappingExceptions.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/PathNormalizer.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/CoerceHelper.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/DeclarativeRuleExecutor.java
    - api-connector-mapping/src/test/java/com/suntek/apiconnector/mapping/DeclarativeRuleExecutorTest.java
  modified: []

key-decisions:
  - "Use DocumentContext.put() for writes to missing paths; Jayway set() throws PathNotFound on new leaves"
  - "Rename deletes source after copy to implement field move semantics"
  - "array_map deferred to Plan 02-03 per D-05"

patterns-established:
  - "Pattern: lenient readLenient skips missing/null sources without exception (D-06)"
  - "Pattern: CoerceHelper throws MappingExceptions.coerceFailed with path and expectedType details"
  - "Pattern: ensureParentObjects creates intermediate LinkedHashMap nodes via put (Pitfall 7)"

requirements-completed: [MAP-01]

duration: 35min
completed: 2026-06-17
---

# Phase 2 Plan 02: Declarative Rule Executor Summary

**DeclarativeRuleExecutor with rename/set/coerce/nest ops, strict CoerceHelper, and MappingException hierarchy proven by MAP-01 unit tests**

## Performance

- **Duration:** 35 min
- **Started:** 2026-06-17T14:00:00Z
- **Completed:** 2026-06-17T14:35:00Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Added `MappingDirection`, `EndpointMeta`, and `MappingException` hierarchy mirroring auth error patterns
- Implemented `DeclarativeRuleExecutor.applyRules` with sequential rename, set, coerce, and nest dispatch
- Implemented `CoerceHelper` with strict string/number/boolean/ISO-8601 date coercion and `MAPPING_COERCE_FAILED` on failure
- Added `DeclarativeRuleExecutorTest` covering rename, literal set, coerce success/failure, nest parent creation, lenient missing source, and rule ordering

## Task Commits

Each task was committed atomically:

1. **Task 1: Domain enums and mapping exception types** - `c5b739c` (feat)
2. **Task 2: CoerceHelper and DeclarativeRuleExecutor core ops** - `abf1763` (feat)
3. **Task 3: MAP-01 DeclarativeRuleExecutor unit tests** - `c1cb777` (test)

## Files Created/Modified

- `api-connector-domain/.../MappingDirection.java` - REQUEST, RESPONSE, ERROR enum
- `api-connector-domain/.../EndpointMeta.java` - id/method/path record for Groovy bindings
- `api-connector-mapping/.../exception/MappingException.java` - Structured mapping failure with code + details
- `api-connector-mapping/.../exception/MappingExceptions.java` - coerceFailed and specInvalid factories
- `api-connector-mapping/.../PathNormalizer.java` - Bare field → `$.field` normalization
- `api-connector-mapping/.../CoerceHelper.java` - Strict type coercion for coerce op
- `api-connector-mapping/.../DeclarativeRuleExecutor.java` - Sequential Jayway rule executor
- `api-connector-mapping/.../DeclarativeRuleExecutorTest.java` - MAP-01 rename + coerce unit tests

## Decisions Made

- Used `DocumentContext.put()` instead of `set()` for writing to non-existent paths (Jayway default `set` throws `PathNotFoundException`)
- Rename removes source field after copying value to target (move semantics)
- `array_map` explicitly rejected at runtime until Plan 02-03

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Jayway set() fails on missing target paths**
- **Found during:** Task 3 (DeclarativeRuleExecutorTest)
- **Issue:** `ctx.set("$.version", value)` on `{}` throws `PathNotFoundException`; only in-place updates worked
- **Fix:** Added `writeValue()` using `ctx.put()` for root and nested leaf assignment; `ensureParentObjects` uses `put` for intermediate maps
- **Files modified:** `DeclarativeRuleExecutor.java`
- **Verification:** All 8 DeclarativeRuleExecutorTest cases pass
- **Committed in:** `c1cb777` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required for any set/rename/nest to new paths; no scope creep.

## Issues Encountered

None beyond the Jayway write-path deviation above.

## User Setup Required

None - no external service configuration required.

## Verification Results

```
.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=DeclarativeRuleExecutorTest -Dsurefire.failIfNoSpecifiedTests=false
→ BUILD SUCCESS (8 tests pass)

.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test
→ BUILD SUCCESS (all mapping module tests pass)
```

## Next Phase Readiness

- Ready for **02-03**: `array_map`, `MappingEngineImpl` facade, `MappingConfigResolver`
- `DeclarativeRuleExecutor` ready to wire into direction-level mapping engine
- `MappingException` ready for API handler in 02-04

---
*Phase: 02-data-mapping-engine*
*Completed: 2026-06-17*
