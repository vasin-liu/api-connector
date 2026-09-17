---
phase: 03-orchestrator-pipeline-integration
verified: 2026-07-19T09:45:00Z
status: human_needed
score: 14/17 must-haves verified
behavior_unverified: 3
overrides_applied: 0
re_verification: false
behavior_unverified_items:
  - truth: "ResolvedMapping is resolved once and cached per connector+endpoint, invalidated on connector publish (D-21)"
    test: "Publish a mapped connector twice; assert MappingConfigResolver.resolve is not re-called between invokes, then after publish assert next invoke re-resolves"
    expected: "Cache hit on second invoke; cache miss after ConnectorPublishListener.onPublish"
    why_human: "ResolvedMappingCache.get/evict are wired, but no unit/integration test asserts cache hit or publish-time eviction (ConnectorPublishListenerTest still uses 2-arg ctor without the cache)"
  - truth: "Global flag integration.invoke.mapping-enabled (default true) forces full passthrough when false (D-22)"
    test: "Invoke a mapped endpoint with mappingEnabled=false (ctor or property); assert MappingEngine.mapRequest is never called and raw body is signed/sent"
    expected: "Full passthrough despite hasAnyMapping==true"
    why_human: "Flag is wired (@Value + application.yml default true) but no test exercises the false path"
  - truth: "outcome distinguishes SUCCESS vs VENDOR_ERROR vs PIPELINE_ERROR(<stage>) (D-14)"
    test: "Drive doInvoke with a MappingException/AuthException; assert audit line contains outcome=PIPELINE_ERROR(mapping|transform|auth)"
    expected: "Audit emits PIPELINE_ERROR(<stage>) with vendorHttpStatus=0 then exception rethrows"
    why_human: "SUCCESS/VENDOR_ERROR covered by stream audit tests; PIPELINE_ERROR path is coded in catch but has no ListAppender assertion"
human_verification:
  - test: "Confirm mapping-enabled=false runtime toggle (ops smoke)"
    expected: "With integration.invoke.mapping-enabled=false, mapped connectors pass body through without mapRequest/mapResponse"
    why_human: "No automated test for the false branch (D-22)"
  - test: "Confirm publish invalidates ResolvedMappingCache"
    expected: "After admin/publish of a connector, next invoke picks up new mapping without restart"
    why_human: "Eviction wired in ConnectorPublishListener; not covered by ConnectorPublishListenerTest"
  - test: "Confirm PIPELINE_ERROR audit outcome on request-side failure"
    expected: "Failed mapRequest/auth produces audit outcome=PIPELINE_ERROR(<stage>) then structured API error"
    why_human: "Catch/rethrow path present; audit outcome string not asserted in tests"
---

# Phase 3: Orchestrator Pipeline Integration — Verification Report

**Phase Goal:** Single invoke pipeline applies mapping and auth in correct order for proxy API and legacy URL routes.

**Verified:** 2026-07-19T09:45:00Z  
**Status:** human_needed  
**Re-verification:** No — initial verification  
**Mode note:** ROADMAP marks `Mode: mvp`, but the phase goal is not a User Story (`As a …, I want to …, so that …`). Technical verification proceeded against ROADMAP Success Criteria + PLAN must_haves. If MVP UAT framing is required, reformat the goal via `/gsd-mvp-phase 3`.

## Goal Achievement

Phase goal is **observably achieved** in code and tests: one orchestrator pipeline (map → transform → auth → HTTP → response mirror) is shared by unified invoke and legacy routes; streaming applies request-side only with raw chunks. Three supporting must-haves are present and wired but lack behavioral tests → status `human_needed` (not `passed`).

### Observable Truths

| # | Truth | Source | Status | Evidence |
|---|-------|--------|--------|----------|
| 1 | Unified invoke runs full pipeline end-to-end | ROADMAP SC#1 / PIPE-01 | ✓ VERIFIED | `InvokeIntegrationTest.unifiedInvokeRunsFullPipeline` (green); `DefaultIntegrationOrchestrator.invoke` stages map→transform→auth→HTTP→transform→evaluate→map |
| 2 | Legacy URL hits same pipeline with identical core outcome | ROADMAP SC#2 / PIPE-02 | ✓ VERIFIED | `LegacyCompatIntegrationTest.legacyAndUnifiedCoreResultParity` (green); `ThirdpartLegacyDispatcher.forward` → `InvokeContext.LEGACY` + `resolveEndpointId` |
| 3 | HMAC signs body after request mapping | ROADMAP SC#3 / MAP-06 | ✓ VERIFIED | `requestBodyIsMappedBeforeAuthSigns`; `hmacSignsMappedRequestBody` WireMock `$.b` + `X-Auth-Signature` |
| 4 | Audit line: code3rd, endpointId, latency, outcome, requestId | ROADMAP SC#4 / PIPE-03 | ✓ VERIFIED | `InvokeAuditLogger` pattern + `InvokeAuditLoggerTest`; sync `doInvoke` + stream `logStreamAudit` pass `correlationId`/`outcome` |
| 5 | Streaming delivers chunks without mapping buffer regression | ROADMAP SC#5 / PIPE-04 | ✓ VERIFIED | `streamAppliesRequestSideChunksRawWarnOnce` — raw `"raw"` chunks, no `"mapped"`; no `StringBuilder` in `invokeStream` |
| 6 | Passthrough (`hasAnyMapping==false`) skips MappingEngine | 03-01 D-04 | ✓ VERIFIED | `passthroughSkipsMappingEngine` — mapRequest/mapResponse/mapError call counts == 0 |
| 7 | Response: transform → evaluate → mapError/mapResponse via `shouldMapError` | 03-01 D-02/D-03 | ✓ VERIFIED | `responseTransformRunsBeforeEvaluateAndMapsSuccess`; `responseErrorRoutesToMapError` |
| 8 | ResolvedMapping cached; invalidated on publish | 03-01 D-21 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | `ResolvedMappingCache` + `ConnectorPublishListener` `evict` wired; no test asserts hit/evict |
| 9 | `mapping-enabled=false` forces full passthrough | 03-01 D-22 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | `@Value` + `application.yml` default true; false branch untested |
| 10 | Correlation id: X-Request-Id → X-Trace-Id → UUID; sanitized | 03-02 D-10 | ✓ VERIFIED | `IntegrationInvokeServiceTest` resolution + CR/LF + length cap |
| 11 | MDC `requestId` put at entry / remove in finally (invoke + stream) | 03-02 D-11 | ✓ VERIFIED | `invokeSetsAndClearsMdcRequestId`; both `doInvoke`/`doStream` use try/finally |
| 12 | Correlation id internal only — not outbound vendor header | 03-02 D-12 | ✓ VERIFIED | `correlationId` only to MDC + `InvokeAuditEvent`; never added to outbound header maps |
| 13 | outcome SUCCESS / VENDOR_ERROR / PIPELINE_ERROR(&lt;stage&gt;) | 03-02 D-14 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | SUCCESS/VENDOR_ERROR asserted on stream path; PIPELINE_ERROR catch path untested |
| 14 | Streaming request-side before open; warn-once if response mapping | 03-04 D-15/D-17 | ✓ VERIFIED | Same stream IT: mapped body signed + single WARN for response mapping |
| 15 | Pre-stream mapping failure throws; no chunk written | 03-04 D-18 | ✓ VERIFIED | `preStreamMappingFailureThrowsNoChunk` (green) |
| 16 | LegacySpecialHandler adapters never replace mapping | 03-03 D-07 | ✓ VERIFIED | No `MappingEngine`/`TransformPipeline` under `api/legacy/*`; handlers call `invoke(..., LEGACY)` |
| 17 | Error JSON mapped once; formatter envelope-only | 03-03 D-09 | ✓ VERIFIED | `legacyErrorMappedOnceEnvelopeOnly`; `LegacyCompatResponseFormatter` wraps only |

**Score:** 14/17 truths verified (3 present, behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `DefaultIntegrationOrchestrator.java` | Wired invoke + invokeStream pipeline | ✓ VERIFIED | Request/response stages + stream request-side + warn-once |
| `ResolvedMappingCache.java` | ConcurrentHashMap cache + `evict` | ✓ VERIFIED | `get`/`evict` substantive |
| `ConnectorPublishListener.java` | Evict mapping cache on publish | ✓ VERIFIED | `resolvedMappingCache.evict` beside token evict |
| `IntegrationEngineConfiguration.java` | Bean wiring + `mapping-enabled` | ✓ VERIFIED | Orchestrator + cache beans |
| `application.yml` | `mapping-enabled: true` | ✓ VERIFIED | Under `integration.invoke` |
| `InvokeAuditEvent.java` / `InvokeAuditLogger.java` | requestId + outcome | ✓ VERIFIED | Record + key=value line |
| `IntegrationInvokeService.java` | Correlation + MDC + audit both paths | ✓ VERIFIED | Sync + stream |
| `IntegrationInvokeProperties.java` | `mappingEnabled` binding | ✓ VERIFIED | Default true |
| `ThirdpartLegacyDispatcher.java` | Shared LEGACY invoke + endpointId resolve | ✓ VERIFIED | Option A `resolveEndpointId` |
| Phase tests (orchestrator, invoke IT, legacy IT, audit/service) | Behavioral guards | ✓ VERIFIED | All spot-checks green (see below) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `DefaultIntegrationOrchestrator.invoke` | `authenticate` | `outboundBody` after `applyRequest` | ✓ WIRED | Lines ~147–158 |
| `DefaultIntegrationOrchestrator.invoke` | `ErrorMappingTrigger.shouldMapError` | Response routing | ✓ WIRED | Lines ~184–199 |
| `ConnectorPublishListener.onPublishInternal` | `ResolvedMappingCache.evict` | Publish invalidation | ✓ WIRED | Lines ~94–96 |
| `ThirdpartLegacyDispatcher.forward` | `IntegrationInvokeService.invoke(..., LEGACY)` | Shared service | ✓ WIRED | + `resolveEndpointId` |
| `IntegrationInvokeService.doInvoke` | MDC / `InvokeAuditEvent` | try/finally + explicit fields | ✓ WIRED | Not back-compat defaults |
| `IntegrationInvokeService.logStreamAudit` | `InvokeAuditEvent` | Captured correlationId + outcome | ✓ WIRED | SUCCESS / VENDOR_ERROR |
| `DefaultIntegrationOrchestrator.invokeStream` | `exchangeStream` | Mapped body then raw `onLine` | ✓ WIRED | No chunk buffering |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| Orchestrator invoke | `outboundBody` | `mapRequest` → `applyRequest` → `auth.body()` | Yes — mapped JSON in WireMock | ✓ FLOWING |
| Orchestrator response | `finalBody` | `applyResponse` → evaluate → mapResponse/mapError | Yes — IT asserts mapped fields | ✓ FLOWING |
| Audit | `requestId` / `outcome` | `resolveCorrelationId` + classify | Yes — ListAppender asserts | ✓ FLOWING |
| Legacy parity | mapped body | Shared orchestrator | Yes — unified.rawBody == legacy body | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Orchestrator ordering / passthrough / stream fail | `-pl api-connector-engine -am test -Dtest=DefaultIntegrationOrchestratorTest` | Tests run: 8, Failures: 0 | ✓ PASS |
| Correlation + audit | `-pl api-connector-api -am test -Dtest=InvokeAuditLoggerTest,IntegrationInvokeServiceTest` | Tests run: 9, Failures: 0 | ✓ PASS |
| HMAC / full pipeline / SSE / legacy parity | `-pl api-connector-app -am test -Dtest=InvokeIntegrationTest,LegacyCompatIntegrationTest` | Tests run: 12, Failures: 0 | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | — | No phase-declared probes | SKIP |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| MAP-06 | 03-01, 03-04 | Body finalized before HMAC | ✓ SATISFIED | Sync + stream WireMock HMAC-on-mapped-body |
| PIPE-01 | 03-01 | resolve → mapRequest → auth → HTTP → evaluate → mapResponse | ✓ SATISFIED | Orchestrator + `unifiedInvokeRunsFullPipeline` |
| PIPE-02 | 03-03 | Legacy through same orchestration | ✓ SATISFIED | Dispatcher → LEGACY + parity IT; Option A endpointId |
| PIPE-03 | 03-02 | Audit code3rd, endpointId, latency, outcome, correlation id | ✓ SATISFIED | Event/logger + service MDC; stream audit tests |
| PIPE-04 | 03-04 | Streaming supported without buffer regression | ✓ SATISFIED | Request-side map+auth; raw chunks; warn-once |

**Coverage:** 5/5 phase requirement IDs satisfied (no orphans). REQUIREMENTS.md maps MAP-06, PIPE-01..04 → Phase 3 only; all appear in PLAN frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No `TODO`/`FIXME`/`XXX`/`TBD` in phase production files scanned | — | — |
| `ConnectorPublishListenerTest.java` | ctor | Tests still use 2-arg ctor (null mapping cache) | ⚠️ Warning | Publish→mapping-cache eviction untested |
| `pipelineOutcome` | ~293–300 | Stage labels `mapping`/`transform`/`auth` vs plan example `mapRequest` | ℹ️ Info | Still matches `PIPELINE_ERROR(<stage>)` contract |

**Anti-patterns:** 0 blockers, 1 warning, 1 info

### Human Verification Required

### 1. mapping-enabled=false toggle
**Test:** Restart/run with `integration.invoke.mapping-enabled=false`; invoke a connector that has request mapping.  
**Expected:** Outbound body equals inbound (no rename); MappingEngine not applied.  
**Why human:** False branch has no automated test.

### 2. Publish invalidates ResolvedMappingCache
**Test:** Publish an endpoint mapping change; invoke again without process restart.  
**Expected:** New mapping applies immediately (cache evicted).  
**Why human:** Eviction wired but not asserted in `ConnectorPublishListenerTest`.

### 3. PIPELINE_ERROR audit outcome
**Test:** Force a mapping/auth failure on sync invoke with audit enabled; inspect `integration.invoke.audit` line.  
**Expected:** `outcome=PIPELINE_ERROR(mapping|transform|auth)` and platform error response.  
**Why human:** Catch path rethrows correctly; outcome string not covered by unit test.

### Gaps Summary

No blockers to the phase goal. Three supporting behaviors (cache invalidation proof, mapping-enabled=false, PIPELINE_ERROR audit label) are implemented and wired but lack automated behavioral coverage → `human_needed` rather than `passed`.

**Regression awareness (phases 01–02):** Auth snapshot / HMAC / MappingEngine / TransformPipeline / ErrorMappingTrigger remain intact; Phase 2 deferred MAP-06 is closed here. No regressions observed in spot-check suites.

**Deferred:** None — remaining ADMIN/GW/UI requirements belong to later phases and are out of Phase 3 scope.

---

_Verified: 2026-07-19T09:45:00Z_  
_Verifier: Claude (gsd-verifier)_
