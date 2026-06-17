---
phase: 01-auth-plugin-architecture
plan: "03"
subsystem: auth
tags: [oauth, token-cache, publish-listener, groovy, compile-on-publish]

requires:
  - phase: 01-01
    provides: ScriptCompileService with SHA-256 compile-once cache
  - phase: 01-02
    provides: GroovyAuthScriptProvider and groovy_auth_script spec support
provides:
  - Central TokenCache keyed by code3rd + profile + scope with 60s skew
  - OAuth providers delegating to TokenCache with ext map population
  - ConnectorPublishListener for compile-on-publish and token eviction
affects: [01-04, 01-05, 01-06, phase-2-mapping]

tech-stack:
  added: [api-connector-scripting dependency in api-connector-engine]
  patterns: [Central TokenCache single-flight, publish-time Groovy compile + contract validation, registry save/register hooks]

key-files:
  created:
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/cache/TokenCache.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/cache/TokenCacheKey.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/cache/CachedToken.java
    - api-connector-auth/src/test/java/com/suntek/apiconnector/auth/cache/TokenCacheTest.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/ConnectorPublishListenerTest.java
  modified:
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/OAuth2ClientCredentialsAuthProvider.java
    - api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/OAuth2TokenInQueryAuthProvider.java
    - api-connector-engine/pom.xml
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorRegistry.java
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java

key-decisions:
  - "TokenCache.getOrRefresh returns CachedToken (not String) so providers can populate ext on cache hits"
  - "Contract validation accepts AuthOutcome return or AuthScript.apply stub invocation"
  - "ROADMAP SC#2 compile-once invoke assertion deferred to Plan 06 InvokeIntegrationTest"

patterns-established:
  - "Pattern: TokenCache keyed by TokenCacheKey(code3rd, profileType, scope) with REFRESH_SKEW_SECONDS=60"
  - "Pattern: ConnectorRegistry.save/register invokes ConnectorPublishListener.onPublish after spec stored"

requirements-completed: [AUTH-01]

duration: 35min
completed: 2026-06-17
---

# Phase 1 Plan 03 Summary

**Central OAuth TokenCache with publish-time Groovy script compilation and connector republish token eviction**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-06-17T05:25:00Z
- **Completed:** 2026-06-17T06:00:00Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Implemented `TokenCache`, `TokenCacheKey`, and `CachedToken` with 60s proactive refresh skew and single-flight per key
- Refactored both OAuth built-in providers to use central cache and populate `ext` (`accessToken`, `tokenExpiresAt`, `oauthRawResponse`)
- Added `ConnectorPublishListener` to evict tokens and pre-compile `groovy_auth_script` sources on connector save/register
- Wired publish listener into `ConnectorRegistry` and Spring `IntegrationEngineConfiguration`

## Task Commits

1. **Task 1: Implement TokenCache and TokenCacheKey** - `f47002a` (feat)
2. **Task 2: Refactor OAuth providers to use TokenCache and populate ext map** - `633efb0` (feat)
3. **Task 3: ConnectorPublishListener — compile scripts on publish and evict tokens** - `b7312c6` (feat)

## Files Created/Modified

- `TokenCache.java` — in-memory OAuth store with `getOrRefresh` and `evictForConnector`
- `TokenCacheKey.java` / `CachedToken.java` — composite cache key and token record with optional raw JSON
- `OAuth2ClientCredentialsAuthProvider.java` / `OAuth2TokenInQueryAuthProvider.java` — delegate to shared `TokenCache`
- `ConnectorPublishListener.java` — scans connector/endpoint auth for Groovy scripts, validates contract, evicts tokens
- `ConnectorRegistry.java` — invokes publish listener after `save()` (including `register()` path)
- `IntegrationEngineConfiguration.java` — registers `TokenCache`, `ConnectorPublishListener`, updated OAuth beans
- `TokenCacheTest.java` / `ConnectorPublishListenerTest.java` — skew, eviction, single-flight, and publish hook tests

## Decisions Made

- `getOrRefresh` returns `CachedToken` so `ext` keys are populated on cache hits as well as fresh fetches
- Publish-time contract check accepts scripts returning `AuthOutcome` directly (current Groovy pattern) or `AuthScript` implementations
- No change to `ConnectorBootstrapConfiguration` — `registry.register()` already routes through `save()` which fires the listener

## Deviations from Plan

### Minor API shape adjustment

- **Plan specified:** `String getOrRefresh(TokenCacheKey, Supplier<CachedToken>)`
- **Implemented:** `CachedToken getOrRefresh(...)` so OAuth providers can populate `ext` on cache hits without a second lookup
- **Impact:** Behavior matches D-11/D-19; no functional regression

## Issues Encountered

- PowerShell does not support bash heredoc for git commits — used `-m` twice instead

## User Setup Required

None

## Next Phase Readiness

- Plan 01-04 can wire `AuthConfigResolver` and orchestrator snapshot carry-forward
- Plan 01-05 can replace `ScriptCompileException` strings with `AuthException` + `AUTH_SCRIPT_COMPILE_ERROR` enum
- Plan 01-06 should add ROADMAP SC#2 integration test (compile once on second invoke)

## Self-Check: PASSED

- `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test` exits 0
- `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test` exits 0
- OAuth providers delegate to `TokenCache` with `TokenCacheKey(code3rd, profile, scope)`

---
*Phase: 01-auth-plugin-architecture*
*Completed: 2026-06-17*
