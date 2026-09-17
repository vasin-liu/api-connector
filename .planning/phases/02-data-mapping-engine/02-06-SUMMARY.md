---
phase: 02-data-mapping-engine
plan: "06"
subsystem: api
tags: [transform-pipeline, sm4, bouncycastle, keyRef, jdbc, spec-json, reload, MAP-05]

requires:
  - phase: 02-05
    provides: MappingEngineImpl, MappingConfigResolver, ConnectorPublishListener validation hook
  - phase: 02-01
    provides: ConnectorSpec.transform[], JdbcConnectorConfigStore, ConnectorConfigSyncService
provides:
  - TransformStep SPI executing transform[] separate from mapping.* blocks (D-20, D-22, ADR-002)
  - TransformPipeline with applyRequest/applyResponse direction filtering and ordered reduce (D-21)
  - TransformStepRegistry resolving step type from Spring List injection
  - Sm4EncryptTransformStep / Sm4DecryptTransformStep (SM4/ECB/PKCS5Padding + Base64, keyRef only)
  - StubTransformStep for business_envelope rejecting enabled:true (D-20 deferred)
  - MappingSpecValidator transform[] validation (type registered, keyRef required, inline key forbidden)
  - MappingPublishIntegrationTest proving JDBC SPEC_JSON persist + reload without restart (MAP-05)
affects: [Phase 3 orchestrator wiring, DefaultIntegrationOrchestrator, MAP-06]

tech-stack:
  added: [bouncycastle SM4 provider]
  patterns: [transform pipeline SPI, keyRef credential resolution, registry-backed publish validation]

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
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    - api-connector-persistence/src/main/java/com/suntek/apiconnector/persistence/jdbc/JdbcConnectorConfigStore.java

key-decisions:
  - "Body in/out as String through every TransformStep (D-22) — crypto stays opaque to JSON mapping"
  - "SM4 key is UTF-8 bytes of the resolved credential (16-byte/128-bit) for legacy CetcUtils parity"
  - "keyRef resolved against credentials map; inline key field forbidden at publish (Pitfall 6)"
  - "business_envelope registered but stubbed — enabled:true rejected at publish and at apply (D-20)"
  - "TransformPipeline delivers transform stage only; orchestrator wires mapRequest→transform→auth in Phase 3 (MAP-06 out of scope)"

patterns-established:
  - "Pattern 11: TransformStep SPI resolved by type() via TransformStepRegistry Spring List injection"
  - "Pattern 12: keyRef credential resolution — never inline secrets in transform[] config"
  - "Pattern 13: registry-backed publish validation rejects unknown/unsupported transform types"

requirements-completed: [MAP-01, MAP-05]

duration: 12min
completed: 2026-06-18
---

# Phase 2 Plan 06: Transform Pipeline (SM4) and JDBC Publish Reload Summary

**TransformStep SPI with BouncyCastle SM4 encrypt/decrypt as first transform, keyRef-only credential resolution, transform[] publish validation, and JDBC SPEC_JSON reload proven without restart (MAP-05)**

## Performance

- **Duration:** 12 min
- **Started:** 2026-06-18T00:12:00+08:00
- **Completed:** 2026-06-18T00:24:18+08:00
- **Tasks:** 3
- **Files modified:** 16 (10 created, 6 modified)

## Accomplishments

- Defined `TransformStep` SPI (`type()` + `String apply(TransformContext)`) and `TransformContext` record carrying body, stepConfig, credentials, direction (D-22)
- Implemented `TransformPipeline.applyRequest/applyResponse` with direction filtering (default `request`) and sequential body reduce, plus `TransformStepRegistry` from Spring List injection
- Implemented `Sm4EncryptTransformStep`/`Sm4DecryptTransformStep` over `Sm4Cipher` (`SM4/ECB/PKCS5Padding` + Base64, BouncyCastle provider) resolving the key from `keyRef` → credentials, never inline (Pitfall 6)
- `StubTransformStep` registers `business_envelope` but throws `TRANSFORM_UNSUPPORTED` when `enabled:true`; disabled is a no-op passthrough (D-20 deferred)
- Extended `MappingSpecValidator` to validate `transform[]`: type required + registered, `sm4_*` require `keyRef` and forbid inline `key`, `business_envelope` enabled:true rejected — wired into publish via `ConnectorPublishListener`
- `MappingPublishIntegrationTest` (H2) proves SPEC_JSON persist → `reloadFromStore` → publish listener → `MappingConfigResolver.resolve` applies rename/set rules and SM4 round-trips with reloaded credentials — no restart (MAP-05, ROADMAP SC#4, D-27..D-29)

## Task Commits

Each task was committed atomically:

1. **Task 1: TransformStep SPI, registry, and SM4 implementation** - `0e3aaad` (feat)
2. **Task 2: Publish validation for transform[] types** - `d74787c` (feat)
3. **Task 3: JDBC mapping publish integration test** - `16ab23f` (test)

## Files Created/Modified

- `api-connector-mapping/.../spi/TransformStep.java` - Transform pipeline SPI (`String type()`, `apply`)
- `api-connector-mapping/.../TransformContext.java` - Record: body, stepConfig, credentials, direction
- `api-connector-mapping/.../TransformPipeline.java` - Ordered transform[] execution by direction (D-21)
- `api-connector-mapping/.../TransformStepRegistry.java` - type → TransformStep map with `require`/`isRegistered`
- `api-connector-mapping/.../transform/Sm4Cipher.java` - SM4/ECB/PKCS5Padding + Base64 over BouncyCastle
- `api-connector-mapping/.../transform/Sm4EncryptTransformStep.java` - `sm4_encrypt` keyRef-resolved encrypt
- `api-connector-mapping/.../transform/Sm4DecryptTransformStep.java` - `sm4_decrypt` inverse for response
- `api-connector-mapping/.../transform/StubTransformStep.java` - `business_envelope` stub, enabled:true unsupported
- `api-connector-mapping/.../validation/MappingSpecValidator.java` - transform[] validation (registered type, keyRef, no inline key)
- `api-connector-mapping/.../exception/MappingErrorCode.java` + `MappingExceptions.java` - TRANSFORM_UNSUPPORTED / key-missing / transform-failed codes
- `api-connector-engine/.../ConnectorPublishListener.java` - injects TransformStepRegistry, validates on publish
- `api-connector-engine/.../config/IntegrationEngineConfiguration.java` - beans: TransformPipeline, SM4 steps, StubTransformStep, registry
- `api-connector-mapping/.../transform/Sm4TransformStepTest.java` - round-trip, fixed-vector, missing keyRef (5 tests)
- `api-connector-persistence/.../MappingPublishIntegrationTest.java` - JDBC persist + reload + round-trip (2 tests)
- `api-connector-persistence/.../jdbc/JdbcConnectorConfigStore.java` - SPEC_JSON transform[] persistence support

## Decisions Made

- Transform body is `String` in/out at every step (D-22) so crypto/envelope stay opaque to JSON mapping
- SM4 key is the 16-byte UTF-8 of the resolved credential for legacy CETC `CetcUtils` parity; non-16-byte keys raise a structured error
- Pipeline exposes explicit `applyRequest`/`applyResponse` so the Phase 3 orchestrator cannot silently reorder crypto relative to auth signing (Pitfall 1)
- Validator falls back to a builtin transform-type set when no registry is injected, so standalone validation still works

## Deviations from Plan

None - plan executed exactly as specified.

## Issues Encountered

None - all three task commits were already present from the execution session; full `clean verify` confirmed green.

## Verification

- `.\mvnw-jdk21.ps1 clean verify` → **BUILD SUCCESS** (08:15 min, exit 0)
- `Sm4TransformStepTest`: 5 passed
- `MappingSpecValidatorTest`: 12 passed
- `MappingPublishIntegrationTest`: 2 passed

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- TransformStep SPI + SM4 ready; Phase 3 orchestrator wires `mapRequest → transform → auth` (request) and `transform → mapResponse` (response) — MAP-06
- transform[] validated at publish separate from `mapping.*`; unknown types and inline secrets rejected
- MAP-05 JDBC persist + reload without restart proven on H2 — **Phase 2 complete**

---
*Phase: 02-data-mapping-engine*
*Completed: 2026-06-18*
