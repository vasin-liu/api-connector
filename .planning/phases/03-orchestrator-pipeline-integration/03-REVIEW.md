---
status: issues_found
phase: 03
reviewed: 2026-07-19T09:20:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ResolvedMappingCache.java
  - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
  - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
  - api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
  - api-connector-engine/pom.xml
  - api-connector-engine/src/test/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestratorTest.java
  - api-connector-app/src/main/resources/application.yml
  - api-connector-app/src/test/java/com/suntek/apiconnector/app/InvokeIntegrationTest.java
  - api-connector-app/src/test/java/com/suntek/apiconnector/app/LegacyCompatIntegrationTest.java
  - api-connector-api/src/main/java/com/suntek/apiconnector/api/service/IntegrationInvokeService.java
  - api-connector-api/src/main/java/com/suntek/apiconnector/api/invoke/InvokeAuditEvent.java
  - api-connector-api/src/main/java/com/suntek/apiconnector/api/invoke/InvokeAuditLogger.java
  - api-connector-api/src/main/java/com/suntek/apiconnector/api/config/IntegrationInvokeProperties.java
  - api-connector-api/src/main/java/com/suntek/apiconnector/api/legacy/ThirdpartLegacyDispatcher.java
  - api-connector-api/src/test/java/com/suntek/apiconnector/api/service/IntegrationInvokeServiceTest.java
  - api-connector-api/src/test/java/com/suntek/apiconnector/api/invoke/InvokeAuditLoggerTest.java
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
---

# Phase 03: Code Review Report

**Reviewed:** 2026-07-19T09:20:00Z
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

Phase 03 (orchestrator pipeline, correlation/audit, legacy parity, streaming request-side) largely meets plan must_haves: sync invoke orders `mapRequest → transform → auth` before HTTP (MAP-06/PIPE-01), cache invalidates on publish (D-21), correlation id is sanitized and MDC-scoped (PIPE-03), legacy adapter resolves unique endpointId then shares `invoke()` (PIPE-02), and streaming applies request-side mapping with raw chunk passthrough + warn-once (PIPE-04).

Two warnings remain on the **streaming + audit** seam: pre-stream pipeline failures skip `PIPELINE_ERROR` audit, and D-18’s “standard error response” is unlikely once work runs inside `StreamingResponseBody`. No critical security issues found (no credential logging; CRLF sanitize present; legacy path has no mapping engine calls).

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Stream path omits PIPELINE_ERROR audit on pre-stream failures

**File:** `api-connector-api/src/main/java/com/suntek/apiconnector/api/service/IntegrationInvokeService.java:112-162`
**Issue:** `doInvoke` catches `MappingException | AuthException`, audits `PIPELINE_ERROR(<stage>)` with `vendorHttpStatus=0`, then rethrows (D-19/D-20). `doStream` has no equivalent catch — only `complete()→SUCCESS` / `fail()→VENDOR_ERROR` via the auditing sink. Pre-stream mapping/transform/auth failures throw from `DefaultIntegrationOrchestrator.invokeStream` **before** `exchangeStream` and **before** `sink.fail`, so no audit line is emitted. This gaps D-13/D-14 (audit on every stream with outcome) and the stream half of SC#4 when PIPE-04 request-side fails.
**Fix:** Mirror sync handling around `orchestrator.invokeStream(...)`:

```java
try {
    MDC.put(MDC_REQUEST_ID, correlationId);
    // ... auditing sink ...
    orchestrator.invokeStream(..., auditing);
} catch (MappingException | AuthException ex) {
    if (invokeProperties.isAuditEnabled()) {
        auditLogger.log(new InvokeAuditEvent(
                code3rd, resolved.endpointId(), resolved.method(), resolved.path(),
                false, 0, System.currentTimeMillis() - start,
                clientAddress(), context.name() + "_STREAM",
                correlationId, pipelineOutcome(ex)));
    }
    throw ex;
} finally {
    MDC.remove(MDC_REQUEST_ID);
}
```

Add a unit test that forces pre-stream `MappingException` and asserts one audit line with `outcome=PIPELINE_ERROR(mapping)`.

### WR-02: Pre-stream failures inside StreamingResponseBody cannot reliably satisfy D-18 “standard error response”

**File:** `api-connector-api/src/main/java/com/suntek/apiconnector/api/controller/IntegrationProxyController.java:101-110`
**Also:** `api-connector-engine/.../DefaultIntegrationOrchestrator.java:231-328`
**Issue:** D-18 requires pre-stream mapping/auth failures to yield a **standard** structured error (via `RuntimeApiExceptionHandler`), with no partial stream. The controller returns `StreamingResponseBody` and only then calls `streamEndpoint`. Spring commits SSE status/content-type before the callback runs, so a `MappingException`/`AuthException` thrown inside the callback typically cannot be remapped to JSON 4xx/5xx the way sync invoke is. Orchestrator unit test `preStreamMappingFailureThrowsNoChunk` correctly proves no chunk/no `exchangeStream`, but does not prove HTTP-level D-18 semantics.
**Fix (advisory):** Run request-side resolve/map/transform/auth **before** returning `StreamingResponseBody` (e.g. a `prepareStream` step that throws into `@RestControllerAdvice`), and only open the SSE body after that succeeds; or catch pre-stream failures in the controller and write a committed JSON error when the response is still uncommitted. Keep raw chunk passthrough unchanged for the happy path.

## Info

### IN-01: Duplicated request-side pipeline in invoke vs invokeStream

**File:** `api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java:124-154` and `:242-282`
**Issue:** Gate (`pipelineActive` / `mappingActive`), `mapRequest`, and `transform.applyRequest` are copy-pasted between `invoke` and `invokeStream`. Future ordering fixes risk drifting (MAP-06 / D-15).
**Fix:** Extract a private helper (e.g. `finalizeOutboundBody(...)`) used by both entry points.

### IN-02: Stream response-mapping warn-once map never cleared on publish

**File:** `api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java:58-59,252-263`
**Issue:** `streamResponseMappingWarned` grows for every `code3rd:endpointId` that warns and is not evicted alongside `ResolvedMappingCache` / token cache on publish. Low risk (bounded by endpoint count) but inconsistent with D-21 lifecycle.
**Fix:** Evict keys for `code3rd` from `ConnectorPublishListener.onPublishInternal` (or clear matching prefix when mapping cache evicts).

### IN-03: Stream path forwards client request headers; sync path does not

**File:** `api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java:162-169` vs `:284-296`
**Issue:** Sync `exchange` sends only `auth.headers()`. Stream merges `request.headers()` then auth. D-12 is satisfied for the **resolved** correlation id (never injected outbound), but if a client puts `X-Request-Id` / other tracing headers in `EndpointInvokeRequest.headers`, stream will forward them to the vendor while sync will not.
**Fix:** Either strip known correlation/tracing headers before outbound merge, or document intentional pass-through and align sync if clients need the same behavior.

## Must-have coverage (phase 03)

| Must-have | Verdict |
|-----------|---------|
| PIPE-01 full sync pipeline order | Pass — orchestrator + tests |
| MAP-06 HMAC over mapped body (sync + stream) | Pass — WireMock integration coverage |
| D-04 passthrough / null endpointId | Pass — gate + legacy Option A |
| D-21 ResolvedMappingCache + publish evict | Pass |
| D-22 mapping-enabled toggle | Pass — engine `@Value` + yml default true |
| PIPE-03 correlation + MDC + sanitize | Pass on sync; stream SUCCESS/VENDOR_ERROR pass; pre-stream PIPELINE_ERROR gap (WR-01) |
| PIPE-02 legacy core parity / no double error map | Pass — adapter + tests; no mapping in `api/legacy/*` |
| PIPE-04 request-side stream + raw chunks + warn-once | Pass at orchestrator/integration; HTTP D-18 gap (WR-02) |

## Review Summary

| Severity | Count | Status |
|----------|-------|--------|
| CRITICAL | 0     | pass   |
| HIGH/WARNING | 2  | warn   |
| MEDIUM/INFO | 3   | info   |
| LOW      | 0     | —      |

**Verdict:** WARNING — no blockers; address WR-01 (stream PIPELINE_ERROR audit) before treating PIPE-03 as fully closed on streaming; WR-02 is advisory for D-18 HTTP semantics.

---

_Reviewed: 2026-07-19T09:20:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Advisory only — no source fixes applied_
