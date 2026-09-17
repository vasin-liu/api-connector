# Phase 3: Orchestrator Pipeline Integration - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Make `DefaultIntegrationOrchestrator` the single invoke pipeline that applies
mapping → transform → auth in the correct order, shared identically by the
unified invoke API and legacy URL routes, with per-invoke audit (correlation id)
and preserved streaming.

**In scope:** MAP-06 (pipeline ordering), PIPE-01 (unified orchestration:
resolve → mapRequest → auth → HTTP → evaluate → mapResponse), PIPE-02 (legacy
URL routes through same pipeline), PIPE-03 (audit with correlation id),
PIPE-04 (streaming preserved).

**Out of scope (own phases):** admin BFF / gateway metadata (Phase 4),
new transform step types beyond Phase 2's SM4 + stub, Micrometer/metrics
observability stack, retry/idempotency on transient HTTP failures.
</domain>

<decisions>
## Implementation Decisions

### Pipeline Ordering & Response Side (MAP-06)
- **D-01:** Request order is locked from Phase 2 and unchanged: `mapRequest → transform(encrypt) → auth(sign)`. HMAC/signature is computed on the mapped+transformed body.
- **D-02:** Response side mirrors the request strictly: `transform(SM4 decrypt) → ResponseEvaluator (success/parse) → mapResponse (on success) / mapping.error (on failure)`. Decrypt happens before success evaluation.
- **D-03:** Error-mapping trigger reuses Phase 2's `ErrorMappingTrigger(httpStatus, businessSuccess)` built from the `ResponseEvaluator` result; `!success || http>=400` routes to `mapping.error`, otherwise `mapResponse`. Reuse existing `shouldMapError` logic — do not reimplement the gate.
- **D-04:** `DefaultIntegrationOrchestrator` gains `MappingEngine` + `TransformPipeline` via constructor injection. It calls `MappingConfigResolver.resolve(spec, endpoint)` once at the top to obtain `ResolvedMapping`. When `hasAnyMapping() == false` (passthrough, D-26 from Phase 2), the engine is skipped entirely — zero overhead.
- **D-05:** `mapRequest`/`mapResponse` operate on the **body only**. `query` and `headers` are left untouched by mapping and continue to be populated by the auth stage. (Consistent with Phase 2 `MappingContext`.)

### Legacy Path Unification (PIPE-02)
- **D-06:** Keep the current wiring — legacy continues calling `IntegrationInvokeService.invoke(code3rd, request, InvokeContext.LEGACY)`, so mapping/transform/auth are applied identically to the unified API automatically once the orchestrator is wired. No parallel pipeline.
- **D-07:** `LegacySpecialHandler` / `IdpsLegacySpecialHandler` run as pre/post **adapters** around the shared pipeline — request adaptation before `EndpointResolver.resolve`, response formatting after `mapResponse`. They never replace mapping.
- **D-08:** "Identical outcome" (SC#2) is verified by an integration test asserting the legacy URL and the unified API produce the same **core result** (`success`, `vendorCode`, mapped `data`, `body`) for an equivalent endpoint. The outer envelope (`LegacySuntekResult` shape via `LegacyCompatResponseFormatter`) is allowed to differ — byte-identity is NOT required.
- **D-09:** No double-mapping of errors. `mapping.error` owns the business error JSON shape; `LegacyCompatResponseFormatter` only handles transport-layer envelope (HTTP status, Content-Type, response style).

### Audit & Correlation ID (PIPE-03)
- **D-10:** Correlation id is sourced from inbound header `X-Request-Id` (fallback `X-Trace-Id`); if absent, a server-side UUID is generated. Honors upstream tracing.
- **D-11:** The id is written to MDC (`key=requestId`) at pipeline entry and cleared in a `finally` block, covering both unified API and legacy paths so all log lines carry it.
- **D-12:** The correlation id is **internal only** — NOT forwarded as an outbound header to the vendor (avoids vendors rejecting unknown headers).
- **D-13:** Add a `requestId` field to `InvokeAuditEvent`, populated on both the `invoke` and `stream` audit paths. Audit log line is structured `key=value` single-line output.
- **D-14:** Audit `outcome` distinguishes `PIPELINE_ERROR(<stage>)` vs `VENDOR_ERROR` vs `SUCCESS`.

### Streaming & Mapping (PIPE-04)
- **D-15:** Streaming applies the request side before opening the stream: `mapRequest → transform → auth`, so HMAC is signed on the mapped body (body finalized once, no per-chunk work).
- **D-16:** Response chunks are passed through **raw** — no `mapResponse` and no transform-decrypt on streamed chunks (avoids buffering; satisfies SC#5 "no mapping buffer regression").
- **D-17:** If an endpoint has response mapping configured AND is invoked via streaming, the call is allowed but response mapping is **ignored** (passthrough), with a single `warn` log on first occurrence. Not rejected at publish.
- **D-18:** Pre-stream failures (mapping/transform/auth) throw before any chunk is written → standard error response (no partial stream). Failures after streaming has started go to `sink.fail`.

### Pipeline Failure Semantics
- **D-19:** Request-side pipeline failures (`mapRequest`/`transform`/`auth` errors, before any HTTP is sent) are treated as server-side config/mapping errors → return **5xx** with a structured error code (`MAPPING_*` / `AUTH_*`). Clearly distinguished from vendor business errors (which carry the real `vendorHttpStatus`; pipeline-internal failures set `vendorHttpStatus=0`/empty).
- **D-20:** Reuse the existing `RuntimeApiExceptionHandler` `MappingException` mapping for error translation rather than adding a new handler path.

### ResolvedMapping Caching
- **D-21:** Cache `ResolvedMapping` keyed by connector + endpoint, invalidated on the existing publish event (same lifecycle as the Phase 2 script-compile cache in `ConnectorPublishListener`/registry). Avoids re-resolving on every invoke for mapped endpoints; passthrough endpoints stay zero-overhead via the `hasAnyMapping` fast path.

### Global Mapping Toggle
- **D-22:** Provide a single global config flag `integration.invoke.mapping-enabled` (default `true`). When off, the entire pipeline runs passthrough — a one-switch rollback for integration/rollout safety. No per-direction split.

### Stage Observability
- **D-23:** Stage timings (`mapRequest`/`transform`/`auth`/`http`) are emitted only at **DEBUG** log level. No metrics/Micrometer dependency introduced this phase (kept lightweight; full observability deferred).

### Claude's Discretion
- Exact class boundaries for inserting the mapping/transform stages inside `DefaultIntegrationOrchestrator` (inline vs small private helpers) — planner/executor decide, as long as ordering D-01/D-02 and passthrough D-04 hold.
- Cache implementation detail (e.g., `ConcurrentHashMap` vs existing registry structure) for D-21.
- Exact structured error code names under the `MAPPING_*` / `AUTH_*` families (D-19), reusing Phase 2 `MappingErrorCode` where applicable.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture & Ordering
- `docs/adr/002-auth-and-transform.md` — auth + transform ordering and SPI contracts that MAP-06 ordering must honor
- `docs/adr/001-platform-overview.md` — platform/module overview
- `docs/ARCHITECTURE.md` — module boundaries (engine depends on mapping; avoid circular deps)

### Legacy Compatibility
- `docs/THIRDPART-MIGRATION.md` — legacy thirdpart routing/migration behavior the legacy path must preserve
- `docs/API-STYLE.md` — unified API conventions for the invoke endpoint

### Prior Phase Decisions
- `.planning/phases/01-*/01-CONTEXT.md` — Phase 1 auth decisions (AuthEngine, AuthContextSnapshot, HMAC signing)
- `.planning/phases/02-data-mapping-engine/02-CONTEXT.md` — Phase 2 mapping decisions, esp. D-26 (orchestrator wiring deferred to Phase 3) and passthrough
- `.planning/phases/02-data-mapping-engine/02-06-SUMMARY.md` — TransformStep/TransformPipeline handoff: orchestrator wires `mapRequest → transform → auth` (request) and `transform → mapResponse` (response)
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `api-connector-engine/.../DefaultIntegrationOrchestrator.java` — current pipeline (auth → HTTP → response-eval); mapping/transform inserted here.
- `api-connector-mapping/.../spi/MappingEngine.java` + `MappingEngineImpl` — request/response/error mapping facade to inject.
- `api-connector-mapping/.../TransformPipeline.java` — `applyRequest`/`applyResponse` direction-filtered transform stage (SM4).
- `api-connector-engine/.../MappingConfigResolver.java` — `resolve(spec, endpoint)` + `hasAnyMapping()` passthrough guard.
- `api-connector-mapping/.../ErrorMappingTrigger.java` — `shouldMapError` gate reused for response routing.
- `api-connector-api/.../service/IntegrationInvokeService.java` — shared entry (`invoke`, `invokeEndpoint`, `streamEndpoint`) with `InvokeContext` (RUNTIME/LEGACY); audit lives here today.
- `api-connector-api/.../invoke/InvokeAuditEvent.java` + `InvokeAuditLogger.java` — audit record/logger to extend with `requestId`.
- `api-connector-api/.../legacy/ThirdpartLegacyDispatcher.java` + `LegacyCompatResponseFormatter.java` — legacy adapter already calling the shared service with `InvokeContext.LEGACY`.
- `api-connector-engine/.../DefaultIntegrationOrchestrator.invokeStream` + `api-connector-api/.../invoke/ServletStreamingInvocationSink.java` — streaming path to extend with request-side mapping.
- `api-connector-api/.../RuntimeApiExceptionHandler.java` — existing `MappingException` handler reused for pipeline failures (D-20).

### Established Patterns
- Resolver pattern: `AuthConfigResolver` / `MappingConfigResolver` resolve connector-default + endpoint-override; reuse for the pipeline.
- `ConnectorPublishListener` validates + compiles scripts at publish and caches — extend the same lifecycle for `ResolvedMapping` cache (D-21).
- `InvokeContext` enum (RUNTIME/LEGACY/_STREAM suffix) already differentiates audit contexts.

### Integration Points
- Mapping/transform stages inserted inside `DefaultIntegrationOrchestrator.invoke()` and `invokeStream()`.
- `requestId` capture happens at `IntegrationInvokeService` entry (has servlet access for inbound header + client address) and flows into MDC + `InvokeAuditEvent`.
- Global toggle `integration.invoke.mapping-enabled` read alongside existing `IntegrationInvokeProperties`.
</code_context>

<specifics>
## Specific Ideas

- Request/response strict mirror is the guiding principle: whatever the request side does in order A→B→C, the response side undoes in C→B→A (decrypt before evaluate, evaluate before shape).
- Legacy must be a thin adapter over the same orchestrator — no logic duplication; tests prove core-result parity.
</specifics>

<deferred>
## Deferred Ideas

- Per-direction mapping toggles (request/response/transform individually) — D-22 chose a single global flag; finer granularity can come later if needed.
- Micrometer/metrics-based stage timing and dashboards — D-23 keeps it to DEBUG logs; full observability is its own concern.
- Forwarding correlation id to the vendor (end-to-end distributed tracing across the vendor boundary) — D-12 keeps it internal; revisit if a vendor supports trace propagation.
- Per-chunk streaming response mapping — D-16/D-17 chose passthrough; a future phase could add line-level transforms if a vendor requires it.
- Retry/idempotency on transient HTTP failures within the pipeline.

</deferred>

---

*Phase: 3-Orchestrator Pipeline Integration*
*Context gathered: 2026-06-18*
