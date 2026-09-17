# Phase 3: Orchestrator Pipeline Integration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-18
**Phase:** 3-Orchestrator Pipeline Integration
**Areas discussed:** Pipeline ordering & response side, Legacy path unification, Audit & correlation id, Streaming & mapping, Pipeline failure semantics, ResolvedMapping caching, Global mapping toggle, Stage observability

---

## Pipeline Ordering & Response Side (MAP-06)

| Option | Description | Selected |
|--------|-------------|----------|
| decrypt_first | transform decrypt → ResponseEvaluator → mapResponse/mapping.error (strict mirror) | ✓ |
| eval_first | evaluate raw → decrypt → mapResponse | |
| no_resp_transform | no response-side transform decrypt | |
| reuse_trigger | reuse ErrorMappingTrigger(httpStatus, businessSuccess) / shouldMapError | ✓ |
| http_only | gate by HTTP status only | |
| constructor_beans | inject MappingEngine + TransformPipeline; resolve up front; passthrough zero overhead | ✓ |
| facade | new PipelineStage facade wrapping mapping+transform | |
| body_only | mapping applies to body only; query/headers via auth | ✓ |
| body_query | mapping rewrites body + query | |

**User's choice:** decrypt_first, reuse_trigger, constructor_beans, body_only
**Notes:** Request order locked by Phase 2. Response strictly mirrors request.

---

## Legacy Path Unification (PIPE-02)

| Option | Description | Selected |
|--------|-------------|----------|
| same_service | legacy keeps calling IntegrationInvokeService.invoke(..., LEGACY); mapping auto-applied | ✓ |
| parallel | separate parallel legacy pipeline | |
| adapters | special handlers run as pre/post adapters, do not replace mapping | ✓ |
| bypass | special handlers may short-circuit mapping | |
| core_fields | identical = same core result (success/vendorCode/data/body); envelope may differ | ✓ |
| byte_identical | byte-identical responses including envelope | |
| no_double | mapping.error owns business error JSON; formatter only transport envelope | ✓ |
| formatter_owns | legacy skips mapping.error, formatter owns error shape | |

**User's choice:** same_service, adapters, core_fields, no_double
**Notes:** Confirmed legacy already routes through the shared service with InvokeContext.LEGACY.

---

## Audit & Correlation ID (PIPE-03)

| Option | Description | Selected |
|--------|-------------|----------|
| inbound_then_gen | prefer inbound X-Request-Id (then X-Trace-Id), else generate UUID | ✓ |
| always_gen | always generate server-side UUID | |
| mdc_yes | write to MDC (key=requestId), clear in finally, both paths | ✓ |
| mdc_no | audit field only, no MDC | |
| internal_only | id internal only, not forwarded to vendor | ✓ |
| forward | forward X-Request-Id to vendor | |
| add_field_kv | add requestId to InvokeAuditEvent (invoke+stream); key=value single-line log | ✓ |
| json_log | JSON one-line audit output | |

**User's choice:** inbound_then_gen, mdc_yes, internal_only, add_field_kv

---

## Streaming & Mapping (PIPE-04)

| Option | Description | Selected |
|--------|-------------|----------|
| apply_request | apply mapRequest → transform → auth before opening stream | ✓ |
| skip_all | streaming bypasses mapping entirely | |
| passthrough_chunks | response chunks passthrough, no mapResponse/transform-decrypt | ✓ |
| per_line | per-line chunk mapping | |
| ignore_warn | allow + ignore response mapping for streams, warn once | ✓ |
| reject_publish | reject response-mapping + streaming combo at publish | |
| fail_before | pre-stream failures throw → standard error; post-stream → sink.fail | ✓ |
| always_sink | all failures via sink.fail | |

**User's choice:** apply_request, passthrough_chunks, ignore_warn, fail_before

---

## Pipeline Failure Semantics

| Option | Description | Selected |
|--------|-------------|----------|
| config_5xx | request-side pipeline errors → 5xx + structured code (MAPPING_*/AUTH_*), distinct from vendor | ✓ |
| config_4xx | 4xx when caller-input issue, else 5xx | |
| outcome_field | audit outcome distinguishes PIPELINE_ERROR(stage)/VENDOR_ERROR/SUCCESS; reuse RuntimeApiExceptionHandler | ✓ |
| success_only | record success=false only, no stage source | |

**User's choice:** config_5xx, outcome_field

---

## ResolvedMapping Caching

| Option | Description | Selected |
|--------|-------------|----------|
| cache_publish | cache by connector+endpoint, invalidate on publish event (same lifecycle as script-compile cache) | ✓ |
| resolve_each | re-resolve every invoke (simplest, relies on hasAnyMapping fast path) | |

**User's choice:** cache_publish

---

## Global Mapping Toggle

| Option | Description | Selected |
|--------|-------------|----------|
| global_flag | single flag integration.invoke.mapping-enabled (default true), off = passthrough | ✓ |
| per_dir | split toggles for request/response/transform | |
| no_toggle | no toggle, rely on config passthrough | |

**User's choice:** global_flag

---

## Stage Observability

| Option | Description | Selected |
|--------|-------------|----------|
| debug_log | DEBUG-level stage timings, no metric deps | ✓ |
| micrometer | Micrometer Timer metrics | |
| defer_metrics | defer entirely to an observability phase | |

**User's choice:** debug_log

---

## Claude's Discretion

- Exact class boundaries for inserting mapping/transform stages in DefaultIntegrationOrchestrator.
- Cache implementation detail for ResolvedMapping (e.g., ConcurrentHashMap vs registry structure).
- Exact structured error code names under MAPPING_* / AUTH_* families, reusing Phase 2 MappingErrorCode where applicable.

## Deferred Ideas

- Per-direction mapping toggles (request/response/transform individually).
- Micrometer/metrics-based stage timing and dashboards.
- Forwarding correlation id to the vendor for end-to-end tracing.
- Per-chunk streaming response mapping.
- Retry/idempotency on transient HTTP failures within the pipeline.
