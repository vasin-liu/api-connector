---
phase: 03-orchestrator-pipeline-integration
plan: 04
subsystem: engine
tags: [spring-boot, maven, orchestrator, streaming, sse, mapping, hmac, wiremock, junit5, logback]

# Dependency graph
requires:
  - phase: 03-01
    provides: invoke() request/response pipeline, ResolvedMappingCache, mapping-enabled toggle, MappingEngine+TransformPipeline wiring
provides:
  - invokeStream request-side pipeline (mapRequest -> transform.applyRequest -> auth) before exchangeStream (PIPE-04 / MAP-06 / D-15)
  - Raw SSE chunk passthrough with no mapResponse/decrypt/buffering (D-16 / SC#5)
  - Warn-once when response mapping is configured for a streamed endpoint (D-17)
  - Pre-stream mapping/transform/auth failures throw before any chunk (D-18)
affects: [phase-04-admin-bff, streaming-invoke-clients]

# Tech tracking
tech-stack:
  added: []
  patterns: [reuse invoke() request-side gate in invokeStream, ConcurrentHashMap+AtomicBoolean warn-once per endpoint, ListAppender warn assertion]

key-files:
  created: []
  modified:
    - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
    - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestratorTest.java
    - api-connector-app/src/test/java/com/suntek/apiconnector/app/InvokeIntegrationTest.java

key-decisions:
  - "Streaming reuses the same request-side gate/helpers as invoke() (resolvedMappingCache + mappingActive + applyRequest) rather than inventing a parallel path"
  - "D-17 warn-once keyed by code3rd:endpointId via ConcurrentHashMap<AtomicBoolean>; call still streams raw"
  - "No StringBuilder/chunk accumulation in invokeStream — SC#5 / Pitfall 4 held"

patterns-established:
  - "Request-side only on streams: finalize outboundBody before authenticate; response side stays raw onLine"
  - "Warn-once for ignored stream response mapping, never reject at publish"

requirements-completed: [PIPE-04, MAP-06]

coverage:
  - id: D1
    description: "Streaming applies mapRequest -> transform -> auth before opening the stream so HMAC signs the mapped body"
    requirement: PIPE-04
    verification:
      - kind: integration
        ref: "api-connector-app/.../InvokeIntegrationTest.java#streamAppliesRequestSideChunksRawWarnOnce"
        status: pass
      - kind: unit
        ref: "api-connector-engine/.../DefaultIntegrationOrchestratorTest.java#preStreamMappingFailureThrowsNoChunk"
        status: pass
    human_judgment: false
  - id: D2
    description: "Streamed chunks pass RAW; response mapping ignored with a single warn-once when configured"
    requirement: PIPE-04
    verification:
      - kind: integration
        ref: "api-connector-app/.../InvokeIntegrationTest.java#streamAppliesRequestSideChunksRawWarnOnce"
        status: pass
    human_judgment: false
  - id: D3
    description: "Pre-stream mapping failure throws before any chunk is written (D-18)"
    requirement: PIPE-04
    verification:
      - kind: unit
        ref: "api-connector-engine/.../DefaultIntegrationOrchestratorTest.java#preStreamMappingFailureThrowsNoChunk"
        status: pass
    human_judgment: false
  - id: D4
    description: "MAP-06 ordering extended to streaming (mapped body signed)"
    requirement: MAP-06
    verification:
      - kind: integration
        ref: "api-connector-app/.../InvokeIntegrationTest.java#streamAppliesRequestSideChunksRawWarnOnce"
        status: pass
    human_judgment: false

# Metrics
duration: 34min
completed: 2026-07-19
status: complete
---

# Phase 03 Plan 04: Streaming Request-Side Pipeline Summary

**invokeStream now runs mapRequest → transform → auth before opening the SSE stream so HMAC signs the mapped body, while response chunks stay raw with a single warn-once when response mapping is configured**

## Performance

- **Duration:** 34 min
- **Started:** 2026-07-19T08:33:00Z
- **Completed:** 2026-07-19T09:06:46Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- `invokeStream` applies the same request-side pipeline as `invoke()` before `authenticate` / `exchangeStream` (D-15, MAP-06, PIPE-04)
- Vendor SSE chunks continue to pass raw via `HttpStreamHandler.onLine` — no buffering, no mapResponse, no decrypt (D-16, SC#5)
- Response mapping on a streamed endpoint is allowed but ignored with a single `LOG.warn` per `code3rd:endpointId` (D-17)
- Pre-stream `mapRequest` failures throw before `exchangeStream` is called; no chunk reaches the sink (D-18)

## Task Commits

Each task was committed atomically:

1. **Task 1: Failing streaming + pre-stream-failure tests (RED)** - `5cac6b0` (test)
2. **Task 2: Apply request side in invokeStream; raw chunks; warn-once (GREEN)** - `a95a846` (feat)

**Plan metadata:** see final `docs(03-04)` commit.

## Files Created/Modified
- `api-connector-engine/.../DefaultIntegrationOrchestrator.java` - Request-side pipeline in `invokeStream`; `streamResponseMappingWarned` ConcurrentHashMap; DEBUG stage timings reused
- `api-connector-engine/.../DefaultIntegrationOrchestratorTest.java` - `preStreamMappingFailureThrowsNoChunk` (D-18)
- `api-connector-app/.../InvokeIntegrationTest.java` - `streamAppliesRequestSideChunksRawWarnOnce` (D-15/16/17 via WireMock + ListAppender)

## Decisions Made
- Reused the `invoke()` request-side gate (`pipelineActive` / `mappingActive` / cache resolve) inside `invokeStream` rather than extracting a shared helper — minimal diff, identical ordering.
- Warn-once uses `ConcurrentHashMap<String, AtomicBoolean>` keyed by `code3rd:endpointId`; does not reject the call.
- Left the chunk path untouched (no `StringBuilder`) to satisfy SC#5 / Pitfall 4.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Windows PowerShell + Surefire `-Dtest=A+B` pattern did not reliably execute both classes in one reactor run; verified GREEN via separate targeted runs (`DefaultIntegrationOrchestratorTest` 8/8, `InvokeIntegrationTest#streamAppliesRequestSideChunksRawWarnOnce` 1/1).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 03 plans 01–04 complete: unified invoke, audit/correlation, legacy parity, and streaming request-side are wired.
- Ready for phase verification / next phase (Admin BFF & Gateway Metadata).

---
*Phase: 03-orchestrator-pipeline-integration*
*Completed: 2026-07-19*

## Self-Check: PASSED

- Key files exist on disk (orchestrator + both test files + this SUMMARY).
- Commits verified: `5cac6b0` (test RED), `a95a846` (feat GREEN).
- Unit: `DefaultIntegrationOrchestratorTest` — 8 tests, 0 failures (includes `preStreamMappingFailureThrowsNoChunk`).
- Integration: `InvokeIntegrationTest#streamAppliesRequestSideChunksRawWarnOnce` — pass.
- No `StringBuilder` in `DefaultIntegrationOrchestrator` (SC#5).
- requirements-completed = [PIPE-04, MAP-06] (verbatim from PLAN frontmatter).
