---
phase: 03-orchestrator-pipeline-integration
plan: 02
subsystem: api
tags: [spring-boot, slf4j, mdc, correlation-id, audit, logback, junit5, assertj, log-injection]

# Dependency graph
requires:
  - phase: 03-orchestrator-pipeline-integration
    provides: Unified invoke()/invokeStream() pipeline, mapping-enabled toggle, InvokeAuditEvent/InvokeAuditLogger seam (03-01)
provides:
  - Per-invoke correlation id resolved X-Request-Id -> X-Trace-Id -> server UUID (D-10), sanitized before use (T-03-03)
  - MDC requestId set at entry / removed in finally on both doInvoke and doStream (D-11), never forwarded to vendor (D-12)
  - InvokeAuditEvent extended with requestId + outcome (+ 9-arg back-compat ctor); audit line carries requestId=/outcome= on sync AND stream paths (D-13)
  - outcome classification SUCCESS / VENDOR_ERROR / PIPELINE_ERROR(<stage>) with pipeline-error rethrow to RuntimeApiExceptionHandler (D-14/D-19/D-20)
  - IntegrationInvokeProperties.mappingEnabled typed accessor for integration.invoke.mapping-enabled (D-22)
affects: [03-03-legacy-route, 03-04-streaming, 05-react-console-observability]

# Tech tracking
tech-stack:
  added: []
  patterns: [first MDC usage in codebase (try/finally ThreadLocal hygiene), record-widening back-compat constructor, ListAppender logback assertion, package-private static seam for testability]

key-files:
  created:
    - api-connector-api/src/test/java/com/suntek/apiconnector/api/service/IntegrationInvokeServiceTest.java
    - api-connector-api/src/test/java/com/suntek/apiconnector/api/invoke/InvokeAuditLoggerTest.java
  modified:
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/invoke/InvokeAuditEvent.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/invoke/InvokeAuditLogger.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/config/IntegrationInvokeProperties.java
    - api-connector-api/src/main/java/com/suntek/apiconnector/api/service/IntegrationInvokeService.java

key-decisions:
  - "Correlation id passed EXPLICITLY into InvokeAuditEvent at both doInvoke and logStreamAudit (not via MDC inheritance) so stream-callback thread attribution never falls back to requestId=- (A1/Q2, D-13)"
  - "logStreamAudit gained a String correlationId + String outcome parameter; threads complete()->SUCCESS / fail()->VENDOR_ERROR through the inline auditing sink (SC#4 streaming)"
  - "sanitizeCorrelationId strips CR/LF and caps at 128 chars before MDC.put — log-injection hardening (T-03-03/ASVS V7)"
  - "pipelineOutcome derives stage from exception type (MappingException TRANSFORM* -> transform, else mapping; AuthException -> auth); audits vendorHttpStatus=0 then rethrows (D-19/D-20)"

patterns-established:
  - "MDC try/finally: MDC.put(requestId) in try, MDC.remove(requestId) in finally on every entry point (virtual-thread ThreadLocal safety, Pitfall 3)"
  - "Record back-compat widening: new record components appended after existing ones with a delegating constructor defaulting requestId=-/outcome=SUCCESS"
  - "Audit lines log metadata only (code3rd/endpointId/method/path/status/latency/client/context/requestId/outcome); never body/credentials (T-03-02/Security V7)"

requirements-completed: [PIPE-03]

# Metrics
duration: 18min
completed: 2026-06-18
---

# Phase 03 Plan 02: Correlation-ID Capture & Audit Extension Summary

**Per-invoke correlation id (X-Request-Id -> X-Trace-Id -> UUID, sanitized) flows through MDC and is passed explicitly into an extended structured audit line carrying requestId + SUCCESS/VENDOR_ERROR/PIPELINE_ERROR outcome on both the sync and streaming paths**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-06-18T12:58:53+08:00
- **Completed:** 2026-06-18T13:17:06+08:00
- **Tasks:** 3
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- `resolveCorrelationId()` resolves X-Request-Id -> X-Trace-Id -> server UUID (D-10); header-sourced values pass through `sanitizeCorrelationId()` (strip CR/LF, cap 128) to block log injection (T-03-03)
- MDC `requestId` is the first MDC use in the codebase — `put` at entry / `remove` in `finally` on both `doInvoke` and `doStream` (D-11); the id is MDC + audit-event only, never an outbound vendor header (D-12)
- `InvokeAuditEvent` gained `requestId` + `outcome` record components with a 9-arg back-compat constructor (defaults `requestId="-"`/`outcome="SUCCESS"`) so old call sites compile; `InvokeAuditLogger` line appends `requestId={} outcome={}`
- Both audit construction sites carry the captured correlationId + computed outcome explicitly — sync `doInvoke` and streaming `logStreamAudit` (threaded through the inline `auditing` sink's `complete()`->SUCCESS / `fail()`->VENDOR_ERROR), never the back-compat defaults (D-13, SC#4 streaming)
- outcome classification SUCCESS / VENDOR_ERROR / PIPELINE_ERROR(<stage>) (D-14); pipeline catch of `MappingException`/`AuthException` audits with `vendorHttpStatus=0` then rethrows to `RuntimeApiExceptionHandler` (D-19/D-20)
- `IntegrationInvokeProperties.mappingEnabled` typed accessor (default true) for `integration.invoke.mapping-enabled` (D-22)

## Task Commits

Each task was committed atomically (RED -> contract -> GREEN):

1. **Task 1: Add failing correlation-id + audit-line tests (RED)** - `9f6e985` (test)
2. **Task 2: Extend InvokeAuditEvent + InvokeAuditLogger + properties (contract)** - `f1fa177` (feat)
3. **Task 3: Correlation-id capture + MDC + outcome classification in service (GREEN)** - `5a5d1b7` (feat)

**Plan metadata:** see final `docs(03-02)` commit.

## Files Created/Modified
- `api-connector-api/.../service/IntegrationInvokeServiceTest.java` - NEW: resolveCorrelationId precedence + CRLF/length sanitization + MDC put/clear, plus streaming-audit guard (requestId from header, SUCCESS on complete / VENDOR_ERROR on fail, `_STREAM` context)
- `api-connector-api/.../invoke/InvokeAuditLoggerTest.java` - NEW: ListAppender on `integration.invoke.audit` asserts the line contains `requestId=` and `outcome=`
- `api-connector-api/.../invoke/InvokeAuditEvent.java` - Added `requestId`/`outcome` record components; kept 8-arg and added 9-arg back-compat ctors
- `api-connector-api/.../invoke/InvokeAuditLogger.java` - Appended `requestId={} outcome={}` to the key=value SLF4J line (logger name unchanged)
- `api-connector-api/.../config/IntegrationInvokeProperties.java` - Added `mappingEnabled` boolean (default true) + `isMappingEnabled()`/`setMappingEnabled(boolean)`
- `api-connector-api/.../service/IntegrationInvokeService.java` - `resolveCorrelationId()`, `sanitizeCorrelationId()`, MDC try/finally on doInvoke + doStream, `classifyOutcome`/`pipelineOutcome`, `logStreamAudit` widened with correlationId + outcome params; both audit events constructed with explicit values

## Decisions Made
- The streaming `InvokeAuditEvent` is built in `IntegrationInvokeService.logStreamAudit` (the inline `auditing` `StreamingInvocationSink` in `doStream` delegates `complete()`/`fail()` there); `ServletStreamingInvocationSink` is a pure SSE transport writer and constructs no audit event, so it stays out of `files_modified`.
- Correlation id is threaded explicitly into the audit event rather than read from MDC inside the callback, because the stream callback thread may differ from the request thread (RESEARCH A1/Q2) — MDC alone would attribute `requestId="-"`.
- `pipelineOutcome` distinguishes `transform` vs `mapping` stage via `MappingException.code()` name prefix, and `auth` for `AuthException`.

## Deviations from Plan

None - plan executed exactly as written.

This plan ran as a **continuation close-out**: a prior executor was interrupted after committing all 3 implementation tasks (`9f6e985`, `f1fa177`, `5a5d1b7`) but before producing this SUMMARY and updating tracking files. This run verified the three commits, confirmed the implementation matches the tasks and honors the `<threat_model>` (sanitization, MDC try/finally, no vendor forwarding, metadata-only audit lines, pipeline rethrow), re-ran the plan-level verification GREEN, and completed the close-out. No re-implementation, no new fix commits, no gaps found.

---

**Total deviations:** 0
**Impact on plan:** None — implementation already complete and correct; this run performed verification and atomic close-out only.

## Issues Encountered
- Module-scoped Maven run used `-am` (build upstream sibling modules) and `-Dsurefire.failIfNoSpecifiedTests=false` (avoid failing modules lacking the named test) per the build-infra notes — Maven invocation only, no source impact.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Wave 1 of Phase 3 is complete (03-01 pipeline + 03-02 audit/correlation). Wave 2 (03-03 legacy/unified parity, 03-04 streaming request-side mapping) is unblocked.
- The correlation id + structured audit line are the first cross-line traceability mechanism and the foundation for the Phase 5 log viewer (UI-04 / observability).
- Note for 03-04: `doStream` now wraps MDC + audit, but request-side mapping/transform on the streaming path is still 03-04 scope.

---
*Phase: 03-orchestrator-pipeline-integration*
*Completed: 2026-06-18*

## Self-Check: PASSED

- 03-02-SUMMARY.md exists on disk.
- All task commits verified via `git log --oneline --all`: `9f6e985` (Task 1 RED), `f1fa177` (Task 2 contract), `5a5d1b7` (Task 3 GREEN).
- Plan-level verification GREEN: `.\mvnw-jdk21.ps1 -pl api-connector-api -am test -Dtest=IntegrationInvokeServiceTest+InvokeAuditLoggerTest` — IntegrationInvokeServiceTest 8/8, InvokeAuditLoggerTest 1/1, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- requirements-completed = [PIPE-03] (verbatim from PLAN frontmatter).
