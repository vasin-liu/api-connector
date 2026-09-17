---
phase: 01-auth-plugin-architecture
plan: "02"
subsystem: auth
tags: [groovy, groovy_auth_script, AuthProvider, spec, JSR-223]

requires:
  - phase: 01-01
    provides: ScriptCompileService with SHA-256 compile-once cache
provides:
  - GroovyAuthScriptProvider AuthProvider adapter for groovy_auth_script profile
  - AuthScript functional interface for Groovy script contract
  - EndpointSpec.authOverride with Groovy-only parse validation
  - CatalogAuth.script attribute for connector-level inline scripts
affects: [01-03, 01-04, 01-05, phase-2-mapping]

tech-stack:
  added: [api-connector-scripting dependency in api-connector-auth]
  patterns: [Groovy auth as AuthProvider profile type, Groovy-only endpoint authOverride, ctx binding in CompiledScript.eval]

key-files:
  created:
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/spi/AuthScript.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/GroovyAuthScriptProvider.java
    - api-connector-auth/src/test/java/com/suntek/apiconnector/auth/profile/GroovyAuthScriptProviderTest.java
    - api-connector-auth/src/test/resources/scripts/demo_auth.groovy
    - api-connector-auth/src/test/resources/connectors/groovy-demo.yaml
  modified:
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/EndpointSpec.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/ConnectorSpecParser.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/catalog/CatalogAuth.java
    - api-connector-spec/src/main/java/com/suntek/apiconnector/spec/catalog/CatalogConnectorScanner.java
    - api-connector-auth/pom.xml
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java

key-decisions:
  - "Groovy auth integrates as profile type groovy_auth_script via thin AuthProvider adapter (D-07)"
  - "Endpoint authOverride rejects non-Groovy types at parse time with Groovy-only message (D-05)"
  - "ScriptCompileService registered as Spring bean in IntegrationEngineConfiguration for provider injection"

patterns-established:
  - "Pattern: GroovyAuthScriptProvider compiles script via ScriptCompileService, eval with ctx binding"
  - "Pattern: Endpoint authOverride is Groovy-only; connector auth.script via CatalogAuth annotation"

requirements-completed: [AUTH-02]

duration: 30min
completed: 2026-06-17
---

# Phase 1 Plan 02 Summary

**Groovy auth as groovy_auth_script AuthProvider with spec extensions for connector script and Groovy-only endpoint override**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-06-17T05:08:00Z
- **Completed:** 2026-06-17T05:38:00Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Extended `EndpointSpec` with optional `authOverride` and Groovy-only parse validation in `ConnectorSpecParser`
- Added `CatalogAuth.script()` and scanner support for connector-level Groovy auth source
- Implemented `GroovyAuthScriptProvider` delegating to `ScriptCompileService` with `ctx` binding
- Registered `ScriptCompileService` and `GroovyAuthScriptProvider` as Spring beans
- Proved AUTH-02 with passing `GroovyAuthScriptProviderTest` using inline and resource-loaded demo script

## Task Commits

1. **Task 1: Extend spec models for groovy_auth_script and Groovy-only endpoint override** - `1e1188c` (feat)
2. **Task 2: Implement AuthScript contract and GroovyAuthScriptProvider** - `04e5ae1` (feat)
3. **Task 3: AUTH-02 unit test with demo Groovy script** - `9d51aa2` (test)

## Files Created/Modified

- `GroovyAuthScriptProvider.java` — AuthProvider adapter for `groovy_auth_script` profile
- `AuthScript.java` — Groovy script entry contract `AuthOutcome apply(AuthContext ctx)`
- `EndpointSpec.java` — optional `authOverride()` field
- `ConnectorSpecParser.java` — parses and validates Groovy-only endpoint override
- `CatalogAuth.java` / `CatalogConnectorScanner.java` — connector-level `script` attribute
- `IntegrationEngineConfiguration.java` — Spring bean registration
- `GroovyAuthScriptProviderTest.java` — AUTH-02 unit tests with demo script resources

## Decisions Made

- Runtime errors from Groovy scripts wrapped in `IllegalStateException` (full `AuthException` deferred to Plan 05 per plan)
- Added parse-time validation tests in `ConnectorSpecParserTest` alongside spec module verify

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- Multi-module `-Dtest` filter failed on upstream modules without `surefire.failIfNoSpecifiedTests=false` — resolved by quoting Maven properties for PowerShell

## User Setup Required

None

## Next Phase Readiness

- Plan 01-03 can add publish-time compile hooks and central `TokenCache`
- `AuthConfigResolver` in engine (Plan 04) can wire endpoint `authOverride` into invoke path
- Groovy demo connector YAML available for future integration tests

## Self-Check: PASSED

- `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test "-Dtest=GroovyAuthScriptProviderTest" "-Dsurefire.failIfNoSpecifiedTests=false"` exits 0
- `.\mvnw-jdk21.ps1 -pl api-connector-spec -am test` exits 0
- Endpoint authOverride rejects non-groovy types at parse

---
*Phase: 01-auth-plugin-architecture*
*Completed: 2026-06-17*
