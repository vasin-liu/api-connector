---
phase: 01-auth-plugin-architecture
plan: "06"
subsystem: auth
tags: [legacy-auth-inventory, Wave-1, WireMock, oauth2, hmac, AUTH-01]

requires:
  - phase: 01-03
    provides: TokenCache and publish-time compile hook
  - phase: 01-05
    provides: structured AuthException and integration error tests
provides:
  - docs/legacy-auth-inventory.md D-03 audit matrix (ROADMAP SC#6)
  - Wave 1 L2 profile inventory gate (no new Java L2 providers required)
  - ProductionConnectorCatalogsTest wave1CatalogAuthTypesMatchLegacyInventory
  - InvokeIntegrationTest WireMock OAuth query + HMAC header + groovy compile-once proof
affects: [phase-7-migration, phase-2-mapping]

tech-stack:
  added: []
  patterns: [inventory-gated L2 implementation, WireMock outbound auth verification]

key-files:
  created:
    - docs/legacy-auth-inventory.md
  modified:
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    - api-connector-connectors/src/test/java/com/suntek/apiconnector/connectors/ProductionConnectorCatalogsTest.java
    - api-connector-app/src/test/java/com/suntek/apiconnector/app/InvokeIntegrationTest.java
    - api-connector-scripting/src/main/java/com/suntek/apiconnector/scripting/ScriptCompileService.java

key-decisions:
  - "Wave 1 does not require oauth2_password, bearer_from_login, or sm3_header_sign_v1 Java providers — deferred to Wave 2 per inventory gate (D-01, D-04)"
  - "Hikvision, 大华, 讯飞 documented as Groovy L3 path per D-13/D-14"
  - "AUTH-01 satisfied via five existing Wave 1 catalogs + built-in providers"

patterns-established:
  - "Pattern: legacy-auth-inventory.md gates new Java L2 work; skip with documented reason in inventory notes"
  - "Pattern: InvokeIntegrationTest WireMock verifies outbound access_token query (BAIDU_WENXIN) and X-Auth-Signature (DEMO_AKSK)"

requirements-completed: [AUTH-01]

duration: 55min
completed: 2026-06-17
---

# Phase 1 Plan 06 Summary

**Legacy auth inventory from system-thirdpart audit with Wave 1 catalog auth proof via WireMock OAuth and HMAC integration tests**

## Performance

- **Duration:** ~55 min
- **Started:** 2026-06-17T14:00:00Z
- **Completed:** 2026-06-17T07:16:00Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- Created `docs/legacy-auth-inventory.md` with 16 vendor rows covering Wave 1 (IDPS, Gaode, Baidu, Hikvision, TrafficBrain, 大华, 讯飞) and Wave 2+ deferrals
- Inventory gate confirmed no Wave 1 Java L2 profiles needed; `oauth2_password`, `bearer_from_login`, `sm3_header_sign_v1` deferred
- Extended `ProductionConnectorCatalogsTest.wave1CatalogAuthTypesMatchLegacyInventory` for five Wave 1 code3rd auth types
- Added `InvokeIntegrationTest` WireMock proofs: BAIDU_WENXIN `access_token` query, DEMO_AKSK `X-Auth-Signature`, groovy compile-once cache (ROADMAP SC#2/SC#5)
- `.\mvnw-jdk21.ps1 clean verify` exits 0

## Task Commits

1. **Task 1: Produce docs/legacy-auth-inventory.md** - `d8b1417` (docs)
2. **Task 2: Implement Wave 1 L2 Java profiles per inventory** - `ee58cc5` (feat)
3. **Task 3: Verify Wave 1 catalogs and OAuth/HMAC integration test** - `30b505d` (test)

## Files Created/Modified

- `docs/legacy-auth-inventory.md` — D-03 audit matrix with profile_id, wave, path columns
- `IntegrationEngineConfiguration.java` — inventory gate comment for deferred L2 beans
- `ProductionConnectorCatalogsTest.java` — Wave 1 auth type assertions linked to inventory
- `InvokeIntegrationTest.java` — OAuth query, HMAC header, groovy compile-once integration tests
- `ScriptCompileService.java` — `compiledScriptCacheSize()` for AUTH-03 integration assertion

## Decisions Made

- Skipped `OAuth2PasswordAuthProvider`, `BearerFromLoginAuthProvider`, `Sm3HeaderSignV1AuthProvider` — no Wave 1 vendor requires them (Keda, GZ parking, Shuyun are Wave 2+)
- L3 vendors Hikvision, 大华, 讯飞 marked Groovy per D-13; PF4J and Transform/SM4 deferred per D-15/D-16

## Deviations from Plan

### Conditional L2 providers skipped (not implemented)

- **Plan listed:** `OAuth2PasswordAuthProvider.java`, `BearerFromLoginAuthProvider.java`, conditional `Sm3HeaderSignV1AuthProvider.java`
- **Implemented:** Inventory gate — none required for Wave 1; documented skip in inventory Implementation Gate section
- **Impact:** AUTH-01 met via existing five catalogs; L2 providers implement when Wave 2 vendors migrate

### ProductionConnectorCatalogsTest in connectors module

- **Plan listed:** `api-connector-app/.../ProductionConnectorCatalogsTest.java`
- **Implemented:** Extended existing `api-connector-connectors` test (canonical location since plan 01-02)
- **Impact:** Same acceptance coverage; app module invokes connectors catalog at runtime

### ScriptCompileService public cache size accessor

- **Plan listed:** compile counter spy in integration test
- **Implemented:** `compiledScriptCacheSize()` public method instead of exposing package-private `cache()`
- **Impact:** Minimal API surface for AUTH-03 integration assertion

## Issues Encountered

- PowerShell HEREDOC commit syntax unsupported — used `-m` twice for commit bodies
- `ScriptCompileService.cache()` package-private — added `compiledScriptCacheSize()` for app-module test access
- Missing `urlEqualTo` import after WireMock static import refactor

## User Setup Required

None

## Next Phase Readiness

- Phase 1 complete (6/6 plans); ready for Phase 2 mapping engine
- Wave 2 migration can implement deferred L2 profiles when inventory rows activate
- Phase 7 can use inventory for Hikvision/大华/讯飞 Groovy script authoring

## Self-Check: PASSED

- `docs/legacy-auth-inventory.md` exists with ≥40 lines and Wave 1 rows
- `.\mvnw-jdk21.ps1 clean verify` exits 0
- `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test` exits 0
- WireMock captures signed outbound auth (OAuth query + HMAC headers)

---
*Phase: 01-auth-plugin-architecture*
*Completed: 2026-06-17*
