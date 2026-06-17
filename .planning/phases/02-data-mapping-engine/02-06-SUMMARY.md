---
phase: 02-data-mapping-engine
plan: "06"
subsystem: mapping
tags: [transform, sm4, bouncycastle, jdbc, h2, publish, jsonpath]

requires:
  - phase: 02-05
    provides: MappingEngine facade, MappingConfigResolver, ResolvedMapping, publish validation hook
  - phase: 01
    provides: ScriptCompileService, ConnectorPublishListener, ConnectorRegistry
provides:
  - TransformStep SPI + TransformContext + TransformPipeline (ordered by direction)
  - TransformStepRegistry (type → step) wired from Spring List injection
  - Sm4EncryptTransformStep / Sm4DecryptTransformStep (SM4/ECB/PKCS5Padding + Base64, keyRef only)
  - StubTransformStep for business_envelope (fails when enabled:true)
  - transform[] publish validation in MappingSpecValidator (inline key forbidden)
  - MappingPublishIntegrationTest proving JDBC SPEC_JSON persist + reload without restart
affects: [phase-03-orchestrator, MAP-06, pipeline-wiring]

tech-stack:
  added: [bouncycastle bcprov-jdk18on (mapping module), Spring EmbeddedDatabase H2 test slice]
  patterns:
    - "Transform pipeline as a distinct bean stage from JSON mapping (ADR-002, D-20/D-22)"
    - "keyRef credential resolution — never inline secrets (Pitfall 6)"
    - "Plain-object integration test over embedded H2 (no full Spring context)"

key-files:
  created:
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/spi/TransformStep.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/TransformContext.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/TransformPipeline.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/TransformStepRegistry.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/transform/Sm4Cipher.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/transform/Sm4EncryptTransformStep.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/transform/Sm4DecryptTransformStep.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/transform/StubTransformStep.java
    - api-connector-mapping/src/test/java/com/suntek/apiconnector/mapping/transform/Sm4TransformStepTest.java
    - api-connector-persistence/src/test/java/com/suntek/apiconnector/persistence/MappingPublishIntegrationTest.java
  modified:
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/validation/MappingSpecValidator.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/exception/MappingErrorCode.java
    - api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/exception/MappingExceptions.java
    - api-connector-mapping/pom.xml
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    - api-connector-persistence/src/main/java/com/suntek/apiconnector/persistence/jdbc/JdbcConnectorConfigStore.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/RuntimeApiExceptionHandler.java

key-decisions:
  - "Transform pipeline delivered as a distinct bean stage; orchestrator wiring deferred to Phase 3 (D-21, MAP-06)"
  - "Added TRANSFORM_KEY_MISSING and TRANSFORM_FAILED mapping error codes for structured SM4 runtime failures"
  - "Integration test wires plain objects over embedded H2 rather than a full Spring Boot context for determinism"

patterns-established:
  - "TransformStep SPI: String type() + String apply(TransformContext) — body in/out"
  - "SM4 key length validated (16-byte UTF-8); BouncyCastle provider registered once at class load"

requirements-completed: [MAP-01, MAP-05]

duration: 58 min
completed: 2026-06-18
---

# Phase 2 Plan 06: Transform Pipeline (SM4) + JDBC Publish Reload Summary

**TransformStep SPI with BouncyCastle SM4/ECB encrypt/decrypt steps, publish-time transform[] validation (inline key forbidden), and a JDBC SPEC_JSON persist→reload integration test proving mapping applies without restart.**

## Performance

- **Duration:** ~58 min
- **Started:** 2026-06-17T23:22:00+08:00
- **Completed:** 2026-06-18T00:20:00+08:00
- **Tasks:** 3 (+ 1 cross-module compile fix)
- **Files modified:** 18 (10 created, 8 modified)

## Accomplishments

- `TransformStep` SPI + `TransformContext` + `TransformPipeline` execute `ConnectorSpec.transform[]` ordered by direction, separate from `mapping.*` blocks (D-20, D-22, ADR-002).
- `Sm4EncryptTransformStep` / `Sm4DecryptTransformStep` use `SM4/ECB/PKCS5Padding` + Base64 over BouncyCastle, resolving the key from `keyRef` credentials only (Pitfall 6); round-trip + golden-vector unit tests green.
- `StubTransformStep` recognizes `business_envelope` but fails with `TRANSFORM_UNSUPPORTED` when `enabled:true` (D-20 deferred).
- `MappingSpecValidator` now validates `transform[]` on publish: unknown type rejected, `sm4_*` require `keyRef` and forbid inline `key`, `business_envelope` enabled rejected — wired through `TransformStepRegistry`.
- `MappingPublishIntegrationTest` (H2) proves SPEC_JSON round-trips the mapping + transform blocks and `reloadFromStore()` republishes so rules apply without restart (MAP-05, D-27..D-29).

## Task Commits

1. **Task 1: TransformStep SPI + pipeline + registry + SM4 + stub + test** - `0e3aaad` (feat)
2. **Task 2: transform[] publish validation** - `d74787c` (feat)
3. **Task 3: JDBC mapping publish integration test** - `16ab23f` (test, includes store key-retrieval fix)
4. **Cross-module fix: API exception handler transform codes** - `41b7c8d` (fix)

_Plan metadata commit follows this SUMMARY._

## Files Created/Modified

- `mapping/spi/TransformStep.java` - transform SPI (`String type()`, `String apply(TransformContext)`)
- `mapping/TransformContext.java` - body + stepConfig + credentials + direction record
- `mapping/TransformPipeline.java` - ordered `applyRequest`/`applyResponse` by direction (D-21 documented)
- `mapping/TransformStepRegistry.java` - type → step resolution from Spring List
- `mapping/transform/Sm4Cipher.java` - shared SM4/ECB/PKCS5Padding + Base64 helper
- `mapping/transform/Sm4EncryptTransformStep.java`, `Sm4DecryptTransformStep.java` - keyRef-resolved crypto steps
- `mapping/transform/StubTransformStep.java` - business_envelope deferred stub
- `mapping/validation/MappingSpecValidator.java` - transform[] validation + registry injection
- `mapping/exception/MappingErrorCode.java`, `MappingExceptions.java` - TRANSFORM_KEY_MISSING / TRANSFORM_FAILED
- `engine/config/IntegrationEngineConfiguration.java` - SM4/stub/registry/pipeline beans; 3-arg publish listener
- `engine/ConnectorPublishListener.java` - registry-aware validator construction
- `persistence/jdbc/JdbcConnectorConfigStore.java` - generated-key retrieval fix (ID column only)
- `api/RuntimeApiExceptionHandler.java` - exhaustive MappingErrorCode switch for new codes
- `persistence/.../MappingPublishIntegrationTest.java`, `mapping/.../Sm4TransformStepTest.java` - tests

## Decisions Made

- Delivered transform as a distinct `TransformPipeline` bean with explicit `applyRequest`/`applyResponse`; the orchestrator wires the full `mapRequest → transform → auth` order in Phase 3 (D-21, MAP-06 out of scope here).
- Added `TRANSFORM_KEY_MISSING` and `TRANSFORM_FAILED` error codes so SM4 runtime failures surface structured details consistent with the mapping error model (D-14).
- Integration test uses plain objects over an embedded H2 (`EmbeddedDatabaseBuilder` + `db/schema.sql`) for deterministic wiring without a full Spring context.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Blocking bug] JDBC generated-key retrieval failed on H2**
- **Found during:** Task 3 (MappingPublishIntegrationTest)
- **Issue:** `JdbcConnectorConfigStore.upsertClient` used `Statement.RETURN_GENERATED_KEYS`; H2 returns multiple generated columns (ID, CREATED_AT, UPDATED_AT) so `keyHolder.getKey()` threw `InvalidDataAccessApiUsageException`.
- **Fix:** Request only the `ID` generated column via `prepareStatement(sql, new String[]{"ID"})`; removed now-unused `Statement` import.
- **Files modified:** api-connector-persistence/.../JdbcConnectorConfigStore.java
- **Verification:** MappingPublishIntegrationTest passes (2/2); full `clean verify` green.
- **Committed in:** `16ab23f`

**2. [Rule 1 - Blocking bug] Non-exhaustive MappingErrorCode switch in API handler**
- **Found during:** `clean verify` phase gate (api-connector-api compile)
- **Issue:** Adding `TRANSFORM_KEY_MISSING`/`TRANSFORM_FAILED` broke the exhaustive `switch` in `RuntimeApiExceptionHandler.handleMapping`.
- **Fix:** Mapped `TRANSFORM_KEY_MISSING`→400 and `TRANSFORM_FAILED`→502.
- **Files modified:** api-connector-api/.../RuntimeApiExceptionHandler.java
- **Verification:** `clean verify` BUILD SUCCESS across all 14 modules.
- **Committed in:** `41b7c8d`

---

**Total deviations:** 2 auto-fixed (2 blocking bugs surfaced by new tests/enum). 
**Impact on plan:** Both fixes necessary for correctness/portability; no scope creep. The store fix also improves real MySQL portability.

## Issues Encountered

- `.\mvnw-jdk21.ps1` requires `-am` (upstream modules are not installed to the local repo) and `-Dsurefire.failIfNoSpecifiedTests=false` for module-scoped `-Dtest=` runs; PowerShell requires the `-pl a,b` module list to be quoted.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 2 complete (6/6 plans). `MappingEngine` + `TransformPipeline` exist as distinct beans ready for orchestrator wiring.
- Phase 3 wires `mapRequest → transform → auth` ordering and the MAP-06 integration test (deferred per D-21/D-26).

---
*Phase: 02-data-mapping-engine*
*Completed: 2026-06-18*
