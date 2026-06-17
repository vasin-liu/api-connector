---
phase: 01-auth-plugin-architecture
plan: "01"
subsystem: infra
tags: [groovy, jsr223, maven, compile-cache, sha256]

requires: []
provides:
  - api-connector-scripting Maven module
  - ScriptCompileService with SHA-256 compile-once cache
  - CompiledScriptCache and ScriptCompileException
affects: [01-02, 01-03, phase-2-mapping]

tech-stack:
  added: [org.apache.groovy:groovy 4.0.32, org.apache.groovy:groovy-jsr223 4.0.32]
  patterns: [JSR-223 Compilable.compile with content-hash cache, shared scripting infrastructure module]

key-files:
  created:
    - api-connector-scripting/pom.xml
    - api-connector-scripting/src/main/java/com/suntek/apiconnector/scripting/ScriptCompileService.java
    - api-connector-scripting/src/main/java/com/suntek/apiconnector/scripting/CompiledScriptCache.java
    - api-connector-scripting/src/main/java/com/suntek/apiconnector/scripting/ScriptCompileException.java
    - api-connector-scripting/src/test/java/com/suntek/apiconnector/scripting/ScriptCompileServiceTest.java
  modified:
    - pom.xml
    - api-connector-dependencies/pom.xml

key-decisions:
  - "Pinned Groovy 4.0.32 under org.apache.groovy (not org.codehaus.groovy)"
  - "Explicit groovy + groovy-jsr223 BOM entries to avoid transitive Groovy 5 pull"

patterns-established:
  - "Pattern: ScriptCompileService caches CompiledScript by SHA-256 of UTF-8 source (D-08)"
  - "Pattern: Scripting module has zero dependency on auth/engine/spec (D-06)"

requirements-completed: [AUTH-03]

duration: 25min
completed: 2026-06-17
---

# Phase 1 Plan 01 Summary

**Groovy 4 JSR-223 compile-once service with SHA-256 cache in new api-connector-scripting module**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-06-17T04:40:00Z
- **Completed:** 2026-06-17T05:05:00Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments

- Created `api-connector-scripting` module with Groovy 4.0.32 BOM pinning
- Implemented `ScriptCompileService` with `CompiledScriptCache` keyed by content hash
- Added `ScriptCompileException` with label and optional line number from compile errors
- Proved AUTH-03 compile-once behavior with 4 passing unit tests

## Task Commits

1. **Task 1: Scaffold module and Groovy BOM** - `b4c1e50` (feat)
2. **Task 2: Implement ScriptCompileService** - `b76f645` (feat)
3. **Task 3: AUTH-03 compile-once unit test** - `d58dcb8` (test)

## Files Created/Modified

- `api-connector-scripting/` — new shared scripting infrastructure module
- `ScriptCompileService.java` — JSR-223 compile with cache lookup by SHA-256
- `CompiledScriptCache.java` — ConcurrentHashMap-backed CompiledScript store
- `ScriptCompileException.java` — typed compile failure with diagnostics
- `ScriptCompileServiceTest.java` — cache hit, invalid script, distinct hash tests

## Decisions Made

- Added explicit `groovy` artifact to BOM to prevent groovy-jsr223 from pulling Groovy 5.x transitively
- Line number extraction parses `@ line N` from CompilationFailedException message (Groovy 4 API)

## Deviations from Plan

None - plan executed as written with minor Groovy version pinning adjustment for reproducible builds.

## Issues Encountered

- `CompilationFailedException.getLine()` unavailable in resolved Groovy version — fixed by regex parsing compile error message
- Initial transitive dependency pulled Groovy 5.0.5 — fixed by explicit BOM pin for both `groovy` and `groovy-jsr223`

## User Setup Required

None

## Next Phase Readiness

- Plan 01-02 can wire `GroovyAuthScriptProvider` against `ScriptCompileService`
- Compile cache foundation ready for publish-time script compilation in Plan 01-03

## Self-Check: PASSED

- `.\mvnw-jdk21.ps1 -pl api-connector-scripting -am test` exits 0
- Module listed in root pom.xml
- Groovy 4.0.32 pinned under org.apache.groovy in BOM

---
*Phase: 01-auth-plugin-architecture*
*Completed: 2026-06-17*
