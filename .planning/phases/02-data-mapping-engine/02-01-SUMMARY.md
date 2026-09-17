---
phase: 02-data-mapping-engine
plan: "01"
subsystem: api
tags: [mapping, jsonpath, yaml, groovy, maven]

requires:
  - phase: 01-auth-plugin-architecture
    provides: ConnectorSpec parser patterns, authOverride model, ScriptCompileService
provides:
  - api-connector-mapping Maven module (domain, spec, scripting, json-path deps)
  - MappingSpec / DirectionMappingSpec / MappingRule spec models
  - ConnectorSpec.mapping and EndpointSpec.mappingOverride fields
  - ConnectorSpecParser mapping parse/serialize with D-10 mutual exclusion
  - MappingSpecValidator publish-time JSONPath and op validation
affects: [02-02, 02-03, 02-04, ConnectorPublishListener, MappingConfigResolver]

tech-stack:
  added: [api-connector-mapping module, Jayway JsonPath validation]
  patterns: [rules XOR script per direction, JDBC blob round-trip via toConnectorMap]

key-files:
  created:
    - api-connector-mapping/pom.xml
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/MappingSpec.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/DirectionMappingSpec.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/MappingRule.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/MappingOps.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/validation/MappingSpecValidator.java
    - api-connector-spec/src/test/java/com/suntek/apiconnector/spec/ConnectorSpecParserMappingTest.java
    - api-connector-mapping/src/test/java/com/suntek/apiconnector/mapping/validation/MappingSpecValidatorTest.java
    - api-connector-mapping/src/test/resources/mapping/demo-rename.yaml
  modified:
    - pom.xml
    - api-connector-dependencies/pom.xml
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/ConnectorSpec.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/EndpointSpec.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/ConnectorSpecParser.java
    - api-connector-spec/pom.xml

key-decisions:
  - "mappingOverride is full MappingSpec per endpoint (not per-field), mirroring authOverride block pattern"
  - "Spec tests load mapping fixtures via extra testResource pointing at api-connector-mapping test resources"

patterns-established:
  - "Pattern: mapping.request/response/error direction blocks with rules XOR script (D-10)"
  - "Pattern: normalizePath bare field to $.field before JsonPath.compile (matches ResponseEvaluator)"

requirements-completed: [MAP-01, MAP-05]

duration: 45min
completed: 2026-06-17
---

# Phase 2 Plan 01: Mapping Spec Foundation Summary

**Declarative mapping schema with MappingSpec models, JDBC round-trip parser, and publish-time JsonPath validation in new api-connector-mapping module**

## Performance

- **Duration:** 45 min
- **Started:** 2026-06-17T12:45:00Z
- **Completed:** 2026-06-17T13:30:00Z
- **Tasks:** 3
- **Files modified:** 25

## Accomplishments

- Scaffolded `api-connector-mapping` module in root POM and BOM (no engine dependency)
- Added `MappingSpec`, `DirectionMappingSpec`, `MappingRule` records and extended `ConnectorSpec` / `EndpointSpec`
- Extended `ConnectorSpecParser` to parse/serialize `mapping` and `mappingOverride` with D-10 rules XOR script rejection
- Implemented `MappingSpecValidator` with known-op checks and `JsonPath.compile` validation on source/target paths
- Added `ConnectorSpecParserMappingTest`, `MappingSpecValidatorTest`, and `demo-rename.yaml` fixture

## Task Commits

Each task was committed atomically:

1. **Task 1: Scaffold api-connector-mapping module and spec models** - `8944fd2` (feat)
2. **Task 2: Extend ConnectorSpecParser parse/serialize and mutual exclusion** - `5fe8dec` (feat)
3. **Task 3: MappingSpecValidator publish-time validation** - `cfaf7d7` (feat)

**Plan metadata:** `bad4dbf` (docs)

## Files Created/Modified

- `api-connector-mapping/pom.xml` - New mapping module (domain, spec, scripting, json-path)
- `api-connector-spec/.../MappingSpec.java` - Connector-level request/response/error mapping model
- `api-connector-spec/.../ConnectorSpecParser.java` - Parse/serialize mapping blocks + D-10 validation
- `api-connector-mapping/.../MappingSpecValidator.java` - Publish-time JSONPath and op validation
- `api-connector-spec/.../ConnectorSpecParserMappingTest.java` - Round-trip and mutual exclusion tests
- `api-connector-mapping/.../MappingSpecValidatorTest.java` - Invalid path/op/coerce rejection tests
- `api-connector-mapping/src/test/resources/mapping/demo-rename.yaml` - Minimal rename rule fixture

## Decisions Made

- `mappingOverride` stores a full `MappingSpec` block per endpoint (endpoint wins per direction at resolve time in 02-03)
- Spec module test classpath includes mapping test resources via `testResources` directory reference (avoids fixture duplication)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Spec tests could not load mapping-module fixture from classpath**
- **Found during:** Task 2 (ConnectorSpecParserMappingTest)
- **Issue:** Fixture lives in `api-connector-mapping` test resources but test runs in `api-connector-spec`
- **Fix:** Added `testResources` entry in `api-connector-spec/pom.xml` pointing at `../api-connector-mapping/src/test/resources`
- **Files modified:** `api-connector-spec/pom.xml`
- **Verification:** `ConnectorSpecParserMappingTest` passes with `mapping/demo-rename.yaml`
- **Committed in:** `5fe8dec` (Task 2 commit)

**2. [Rule 3 - Blocking] Mapping module tests lacked AssertJ/SnakeYAML test deps**
- **Found during:** Task 3 (MappingSpecValidatorTest compile)
- **Issue:** `api-connector-mapping` only had JUnit; AssertJ unavailable; SnakeYAML needed for fixture load
- **Fix:** Switched to JUnit 5 assertions; added `snakeyaml` test-scoped dependency
- **Files modified:** `api-connector-mapping/pom.xml`, `MappingSpecValidatorTest.java`
- **Verification:** `MappingSpecValidatorTest` compiles and passes
- **Committed in:** `cfaf7d7` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes required for test execution; no scope creep.

## Issues Encountered

None beyond deviations above.

## User Setup Required

None - no external service configuration required.

## Verification Results

```
.\mvnw-jdk21.ps1 -pl api-connector-mapping,api-connector-spec -am test -Dtest=ConnectorSpecParserMappingTest,MappingSpecValidatorTest -Dsurefire.failIfNoSpecifiedTests=false
→ BUILD SUCCESS (all 8 tests pass)
```

## Self-Check: PASSED

- [x] Root pom lists `api-connector-mapping`
- [x] Mapping block round-trips through `ConnectorSpecParser`
- [x] Invalid JSONPath rejected by `MappingSpecValidator`
- [x] `demo-rename.yaml` exists under mapping test resources
- [x] `api-connector-mapping/pom.xml` has no engine dependency

## Next Phase Readiness

- Ready for **02-02**: `DeclarativeRuleExecutor` (rename, set, coerce, nest)
- `MappingSpecValidator` ready to wire into `ConnectorPublishListener` (02-04)
- `MappingConfigResolver` and runtime mapping deferred to 02-03

---
*Phase: 02-data-mapping-engine*
*Completed: 2026-06-17*
