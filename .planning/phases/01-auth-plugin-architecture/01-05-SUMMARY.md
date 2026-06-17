---
phase: 01-auth-plugin-architecture
plan: "05"
subsystem: auth
tags: [AuthException, AuthErrorCode, ApiErrorResponse, structured-errors, AUTH-06]

requires:
  - phase: 01-02
    provides: GroovyAuthScriptProvider and groovy_auth_script profile type
  - phase: 01-03
    provides: ConnectorPublishListener compile-on-publish hook
  - phase: 01-04
    provides: AuthEngine wired through orchestrator invoke path
provides:
  - AuthErrorCode enum with platform auth failure codes (D-17)
  - AuthException with immutable details map
  - AuthExceptions factory helpers for consistent details shape
  - ApiErrorResponse.details field and AuthException HTTP mapping (D-18)
  - Integration test proving AUTH_PROFILE_MISSING in invoke JSON response
affects: [01-06, phase-2-mapping, phase-3-pipeline]

tech-stack:
  added: []
  patterns: [AuthExceptions factory, AuthException → ApiErrorResponse with details, HTTP status by AuthErrorCode]

key-files:
  created:
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/exception/AuthErrorCode.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/exception/AuthException.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/exception/AuthExceptions.java
    - api-connector-auth/src/test/java/com/suntek/apiconnector/auth/AuthEngineTest.java
  modified:
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/AuthEngine.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/GroovyAuthScriptProvider.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/OAuth2ClientCredentialsAuthProvider.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/OAuth2TokenInQueryAuthProvider.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/ConnectorPublishListenerTest.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/dto/ApiErrorResponse.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/RuntimeApiExceptionHandler.java
    - api-connector-app/src/test/java/com/suntek/apiconnector/app/InvokeIntegrationTest.java

key-decisions:
  - "AuthExceptions factory centralizes details map construction; call sites throw via helpers not raw constructors"
  - "AUTH_PROFILE_MISSING and AUTH_SCRIPT_COMPILE_ERROR map to HTTP 400; UPSTREAM_AUTH_FAILED and AUTH_SCRIPT_RUNTIME_ERROR map to HTTP 502"
  - "Legacy IllegalStateException handler retained for backward compat; new auth path uses AuthException exclusively"

patterns-established:
  - "Pattern: AuthException(code, details) with Map.copyOf for immutable details"
  - "Pattern: RuntimeApiExceptionHandler maps ex.code().name() to ApiErrorResponse.code and ex.details() to details field"
  - "Pattern: OAuth token HTTP failures throw UPSTREAM_AUTH_FAILED with code3rd, profileType, httpStatus in details"

requirements-completed: [AUTH-06]

duration: 35min
completed: 2026-06-17
---

# Phase 1 Plan 05 Summary

**Platform AuthErrorCode enum with AuthException details map surfaced through ApiErrorResponse and invoke integration test**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-06-17T06:14:00Z
- **Completed:** 2026-06-17T06:37:00Z
- **Tasks:** 3
- **Files modified:** 13

## Accomplishments

- Added `AuthErrorCode` enum (`AUTH_PROFILE_MISSING`, `UPSTREAM_AUTH_FAILED`, `AUTH_SCRIPT_COMPILE_ERROR`, `AUTH_SCRIPT_RUNTIME_ERROR`)
- Implemented `AuthException` with immutable `details()` map and `AuthExceptions` factory helpers
- Replaced `IllegalStateException` in `AuthEngine`, Groovy provider, OAuth providers, and publish listener with structured `AuthException`
- Extended `ApiErrorResponse` with optional `details` field; added `@ExceptionHandler(AuthException.class)` with status mapping per code
- Added `AuthEngineTest.missingProviderThrowsAuthProfileMissing` and `InvokeIntegrationTest.invokeUnknownAuthProfileReturnsStructuredError`

## Task Commits

1. **Task 1: AuthErrorCode enum and AuthException type** - `c885409` (feat)
2. **Task 2: Replace IllegalStateException throws in auth path** - `f91aa32` (feat)
3. **Task 3: Extend ApiErrorResponse and integration test AUTH-06** - `f284770` (test)

## Files Created/Modified

- `AuthErrorCode.java` — platform auth error codes per D-17
- `AuthException.java` — typed runtime exception with code and immutable details
- `AuthExceptions.java` — factory for profileMissing, upstreamFailed, scriptCompileError, scriptRuntimeError
- `AuthEngine.java` — throws `AUTH_PROFILE_MISSING` when provider not registered
- `GroovyAuthScriptProvider.java` — wraps compile/runtime failures as AuthException
- `OAuth2*AuthProvider.java` — token HTTP failures as `UPSTREAM_AUTH_FAILED` with httpStatus
- `ConnectorPublishListener.java` — `ScriptCompileException` wrapped as `AUTH_SCRIPT_COMPILE_ERROR`
- `ApiErrorResponse.java` — optional `details` map in JSON error body
- `RuntimeApiExceptionHandler.java` — maps AuthException to HTTP status and structured JSON
- `AuthEngineTest.java` — unit assertion for missing provider code and details.profileType
- `InvokeIntegrationTest.java` — HTTP 400 with `"code":"AUTH_PROFILE_MISSING"` and profileType in body

## Decisions Made

- Introduced `AuthExceptions` factory class (not in plan) to avoid duplicated details-map construction across auth and engine modules
- Kept legacy `IllegalStateException` handler for backward compatibility; new auth failures use `AuthException` path
- OAuth upstream failures include optional `httpStatus` in details when available from HTTP response

## Deviations from Plan

### AuthExceptions factory helper class

- **Plan specified:** Direct `AuthException` construction at call sites
- **Implemented:** `AuthExceptions` static factory with `profileMissing`, `upstreamFailed`, `scriptCompileError`, `scriptRuntimeError`
- **Impact:** Consistent details shape; reduces duplication across AuthEngine, OAuth providers, Groovy provider, and ConnectorPublishListener

### ScriptCompileService unchanged

- **Plan listed:** `ScriptCompileService.java` in files_modified
- **Implemented:** Compile error wrapping done in `ConnectorPublishListener` and `GroovyAuthScriptProvider` via `AuthExceptions.scriptCompileError(ScriptCompileException)`
- **Impact:** Same behavior; no changes needed in ScriptCompileService itself

### ConnectorPublishListenerTest extended

- **Plan listed:** Only `AuthEngineTest` for Task 2 verification
- **Implemented:** `ConnectorPublishListenerTest` updated to assert `AUTH_SCRIPT_COMPILE_ERROR` on publish failure
- **Impact:** Additional unit coverage for publish-path compile errors

## Issues Encountered

- Maven `-Dtest=InvokeIntegrationTest` with `-am` fails on modules without matching tests; resolved with `-Dsurefire.failIfNoSpecifiedTests=false`
- PowerShell requires quoting `-Dtest` and `-Dsurefire.*` arguments

## User Setup Required

None

## Next Phase Readiness

- Plan 01-06 can add Wave 1 L2 profiles, legacy auth inventory, and WireMock OAuth/HMAC proof (ROADMAP SC#5)
- Phase 2 mapping can rely on platform error codes; legacy vendor JSON deferred to Phase 6 per D-17

## Self-Check: PASSED

- `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test` exits 0
- `.\mvnw-jdk21.ps1 -pl api-connector-app -am test -Dtest=InvokeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` exits 0
- No auth path throws raw `IllegalStateException` for missing provider

---
*Phase: 01-auth-plugin-architecture*
*Completed: 2026-06-17*
