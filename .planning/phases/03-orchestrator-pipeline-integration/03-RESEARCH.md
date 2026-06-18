# Phase 3: Orchestrator Pipeline Integration - Research

**Researched:** 2026-06-18
**Domain:** Spring Boot HTTP integration pipeline — ordered mapping/transform/auth stages, shared unified+legacy invoke path, MDC correlation-id audit, SSE streaming
**Confidence:** HIGH (primary method: direct codebase inspection of all named classes; no external packages installed this phase)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Pipeline Ordering & Response Side (MAP-06)
- **D-01:** Request order is locked from Phase 2 and unchanged: `mapRequest → transform(encrypt) → auth(sign)`. HMAC/signature is computed on the mapped+transformed body.
- **D-02:** Response side mirrors the request strictly: `transform(SM4 decrypt) → ResponseEvaluator (success/parse) → mapResponse (on success) / mapping.error (on failure)`. Decrypt happens before success evaluation.
- **D-03:** Error-mapping trigger reuses Phase 2's `ErrorMappingTrigger(httpStatus, businessSuccess)` built from the `ResponseEvaluator` result; `!success || http>=400` routes to `mapping.error`, otherwise `mapResponse`. Reuse existing `shouldMapError` logic — do not reimplement the gate.
- **D-04:** `DefaultIntegrationOrchestrator` gains `MappingEngine` + `TransformPipeline` via constructor injection. It calls `MappingConfigResolver.resolve(spec, endpoint)` once at the top to obtain `ResolvedMapping`. When `hasAnyMapping() == false` (passthrough, D-26 from Phase 2), the engine is skipped entirely — zero overhead.
- **D-05:** `mapRequest`/`mapResponse` operate on the **body only**. `query` and `headers` are left untouched by mapping and continue to be populated by the auth stage. (Consistent with Phase 2 `MappingContext`.)

#### Legacy Path Unification (PIPE-02)
- **D-06:** Keep the current wiring — legacy continues calling `IntegrationInvokeService.invoke(code3rd, request, InvokeContext.LEGACY)`, so mapping/transform/auth are applied identically to the unified API automatically once the orchestrator is wired. No parallel pipeline.
- **D-07:** `LegacySpecialHandler` / `IdpsLegacySpecialHandler` run as pre/post **adapters** around the shared pipeline — request adaptation before `EndpointResolver.resolve`, response formatting after `mapResponse`. They never replace mapping.
- **D-08:** "Identical outcome" (SC#2) is verified by an integration test asserting the legacy URL and the unified API produce the same **core result** (`success`, `vendorCode`, mapped `data`, `body`) for an equivalent endpoint. The outer envelope (`LegacySuntekResult` shape via `LegacyCompatResponseFormatter`) is allowed to differ — byte-identity is NOT required.
- **D-09:** No double-mapping of errors. `mapping.error` owns the business error JSON shape; `LegacyCompatResponseFormatter` only handles transport-layer envelope (HTTP status, Content-Type, response style).

#### Audit & Correlation ID (PIPE-03)
- **D-10:** Correlation id is sourced from inbound header `X-Request-Id` (fallback `X-Trace-Id`); if absent, a server-side UUID is generated. Honors upstream tracing.
- **D-11:** The id is written to MDC (`key=requestId`) at pipeline entry and cleared in a `finally` block, covering both unified API and legacy paths so all log lines carry it.
- **D-12:** The correlation id is **internal only** — NOT forwarded as an outbound header to the vendor (avoids vendors rejecting unknown headers).
- **D-13:** Add a `requestId` field to `InvokeAuditEvent`, populated on both the `invoke` and `stream` audit paths. Audit log line is structured `key=value` single-line output.
- **D-14:** Audit `outcome` distinguishes `PIPELINE_ERROR(<stage>)` vs `VENDOR_ERROR` vs `SUCCESS`.

#### Streaming & Mapping (PIPE-04)
- **D-15:** Streaming applies the request side before opening the stream: `mapRequest → transform → auth`, so HMAC is signed on the mapped body (body finalized once, no per-chunk work).
- **D-16:** Response chunks are passed through **raw** — no `mapResponse` and no transform-decrypt on streamed chunks (avoids buffering; satisfies SC#5 "no mapping buffer regression").
- **D-17:** If an endpoint has response mapping configured AND is invoked via streaming, the call is allowed but response mapping is **ignored** (passthrough), with a single `warn` log on first occurrence. Not rejected at publish.
- **D-18:** Pre-stream failures (mapping/transform/auth) throw before any chunk is written → standard error response (no partial stream). Failures after streaming has started go to `sink.fail`.

#### Pipeline Failure Semantics
- **D-19:** Request-side pipeline failures (`mapRequest`/`transform`/`auth` errors, before any HTTP is sent) are treated as server-side config/mapping errors → return **5xx** with a structured error code (`MAPPING_*` / `AUTH_*`). Clearly distinguished from vendor business errors (which carry the real `vendorHttpStatus`; pipeline-internal failures set `vendorHttpStatus=0`/empty).
- **D-20:** Reuse the existing `RuntimeApiExceptionHandler` `MappingException` mapping for error translation rather than adding a new handler path.

#### ResolvedMapping Caching
- **D-21:** Cache `ResolvedMapping` keyed by connector + endpoint, invalidated on the existing publish event (same lifecycle as the Phase 2 script-compile cache in `ConnectorPublishListener`/registry). Avoids re-resolving on every invoke for mapped endpoints; passthrough endpoints stay zero-overhead via the `hasAnyMapping` fast path.

#### Global Mapping Toggle
- **D-22:** Provide a single global config flag `integration.invoke.mapping-enabled` (default `true`). When off, the entire pipeline runs passthrough — a one-switch rollback for integration/rollout safety. No per-direction split.

#### Stage Observability
- **D-23:** Stage timings (`mapRequest`/`transform`/`auth`/`http`) are emitted only at **DEBUG** log level. No metrics/Micrometer dependency introduced this phase (kept lightweight; full observability deferred).

### Claude's Discretion
- Exact class boundaries for inserting the mapping/transform stages inside `DefaultIntegrationOrchestrator` (inline vs small private helpers) — planner/executor decide, as long as ordering D-01/D-02 and passthrough D-04 hold.
- Cache implementation detail (e.g., `ConcurrentHashMap` vs existing registry structure) for D-21.
- Exact structured error code names under the `MAPPING_*` / `AUTH_*` families (D-19), reusing Phase 2 `MappingErrorCode` where applicable.

### Deferred Ideas (OUT OF SCOPE)
- Per-direction mapping toggles (request/response/transform individually) — D-22 chose a single global flag; finer granularity can come later if needed.
- Micrometer/metrics-based stage timing and dashboards — D-23 keeps it to DEBUG logs; full observability is its own concern.
- Forwarding correlation id to the vendor (end-to-end distributed tracing across the vendor boundary) — D-12 keeps it internal; revisit if a vendor supports trace propagation.
- Per-chunk streaming response mapping — D-16/D-17 chose passthrough; a future phase could add line-level transforms if a vendor requires it.
- Retry/idempotency on transient HTTP failures within the pipeline.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MAP-06 | Invoke pipeline applies mappings in correct order relative to auth signing (body finalized before HMAC) | `DefaultIntegrationOrchestrator.invoke()` is the single insertion point. Insert `mapRequest → transform.applyRequest → authenticate(...)` so the body handed to `authenticate(...)` (and thus to HMAC signing) is the mapped+encrypted body. See Pattern 1 + Code Examples. Response side mirrors: `transform.applyResponse → responseEvaluator.evaluate → mapResponse/mapError`. `TransformPipeline.applyRequest/applyResponse` already exist (Phase 2, 02-06). |
| PIPE-01 | Unified invoke API orchestrates resolve → mapRequest → auth → HTTP → evaluate → mapResponse | Order already partially present in `invoke()` (resolve→auth→HTTP→evaluate). Insert mapping/transform around auth + after evaluate. `MappingConfigResolver.resolve(spec, endpoint)` + `hasAnyMapping()` gate per D-04. `MappingEngine` facade (`mapRequest`/`mapResponse`/`mapError`) is injectable. |
| PIPE-02 | Legacy URL paths route through the same orchestration pipeline as unified API | `ThirdpartLegacyDispatcher.forward(...)` already calls `IntegrationInvokeService.invoke(code3rd, request, InvokeContext.LEGACY)` → same `orchestrator.invoke()`. Once the orchestrator is wired (PIPE-01), legacy gets mapping/transform/auth automatically (D-06). No parallel pipeline needed. |
| PIPE-03 | Invoke audit log records code3rd, endpointId, latency, outcome, and correlation id | `InvokeAuditEvent` record + `InvokeAuditLogger` already emit `code3rd/endpointId/method/path/success/vendorHttpStatus/latencyMs/client/context`. Add `requestId` field (D-13) + outcome enum (D-14). Capture requestId at `IntegrationInvokeService` entry (servlet access for header) → MDC (D-11). |
| PIPE-04 | Streaming invoke path remains supported for endpoints that require it | `DefaultIntegrationOrchestrator.invokeStream()` + `ServletStreamingInvocationSink` exist. Apply request-side (`mapRequest → transform → auth`) before `httpTransport.exchangeStream` (D-15); chunks pass raw (D-16); warn-once if response mapping configured (D-17). |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

These directives carry the same authority as locked decisions. The planner MUST NOT recommend approaches that contradict them.

- **Stack:** Java 21 + Spring Boot 4.0.6; virtual threads enabled; single fat JAR, single port (19090). No new external runtime deps this phase. [VERIFIED: CLAUDE.md / STACK]
- **Module dependency direction (hard boundary):** `domain` → no Spring; `spec` → domain; `auth` → domain, spec; `engine` → domain, spec, auth, spring-context; `api` → engine, spring-web (NOT direct auth); `app` → api, ui, persistence. **Engine depends on mapping** (already true — engine imports `com.suntek.apiconnector.mapping.*`). Do NOT introduce a circular `mapping → engine` edge. [VERIFIED: codebase imports + ARCHITECTURE]
- **Logging:** SLF4J via `LoggerFactory.getLogger()` — NOT Lombok `@Slf4j`. Named audit logger `LoggerFactory.getLogger("integration.invoke.audit")`. Structured `key=value` single-line audit output. [VERIFIED: `InvokeAuditLogger`, CONVENTIONS]
- **Error handling:** Throw `IllegalArgumentException` (→400/404), `IllegalStateException` (→502), dedicated typed exceptions (`AuthException`, `MappingException`) mapped in `@RestControllerAdvice` (`RuntimeApiExceptionHandler`). Platform error payload `{ "code", "message", "details" }` via `ApiErrorResponse`. Vendor HTTP status preserved separately from platform status. [VERIFIED: `RuntimeApiExceptionHandler`, CONVENTIONS]
- **Comments/Javadoc:** All `public` classes and methods require Javadoc with `@param`/`@return`; PCI copyright header on every Java file (`Copyright © 2021 - 2026 PCI Technology Group...`); `@author Gensokyo`, `@version`, `@since` on core types. [VERIFIED: every source file]
- **Naming:** `record` for immutable value objects; `final` classes with private constructor for utilities; PascalCase, no `I` prefix on interfaces; UPPER_SNAKE_CASE for connector codes and constants. [VERIFIED: CONVENTIONS]
- **Tests:** JUnit 5 + AssertJ + Spring Boot Test + WireMock 3.13.1; package-private test classes (`class FooTest`, not `public`); text blocks for JSON. Build via `.\mvnw-jdk21.ps1`. [VERIFIED: STACK, `InvokeIntegrationTest`]
- **Secrets:** SM4 keys resolved via `keyRef` → credentials only, never inline in spec/DB/logs (Phase 2 Pitfall 6). Do not log request/response bodies or credentials in audit/stage-timing logs. [VERIFIED: 02-06-SUMMARY, CONVENTIONS]
- **GSD workflow:** No direct repo edits outside a GSD command. (Applies to execution, not this research doc.)

## Summary

Phase 3 is a **pure wiring phase inside the engine layer** — every collaborator it needs (`MappingEngine`, `TransformPipeline`, `MappingConfigResolver`, `ErrorMappingTrigger`, `ResponseEvaluator`, `AuthEngine`) already exists and is already a Spring bean wired in `IntegrationEngineConfiguration`. There are **no new external packages**. The work is: (1) inject `MappingEngine` + `TransformPipeline` + a `ResolvedMapping` cache + the global toggle into `DefaultIntegrationOrchestrator`; (2) insert the request-side stages (`mapRequest → transform.applyRequest → auth`) and response-side stages (`transform.applyResponse → evaluate → mapResponse/mapError`) in the strict mirror order D-01/D-02 demand, gated by the `hasAnyMapping()` passthrough fast path (D-04); (3) thread a correlation id from `IntegrationInvokeService` (where servlet access lives) into MDC + an extended `InvokeAuditEvent`; (4) apply only the request side in `invokeStream()` and pass chunks through raw.

The single most important constraint is **ordering correctness**: the body that reaches `authenticate(...)` (and therefore HMAC/SM3 signing) MUST be the post-`mapRequest`, post-`transform-encrypt` body. The Phase 2 team deliberately kept `TransformPipeline` a distinct bean with explicit `applyRequest`/`applyResponse` signatures specifically so Phase 3 cannot silently reorder crypto relative to signing — honor that. The orchestrator currently builds the outbound body inside the private `authenticate(...)` helper (`auth.body()`), so the mapped+transformed body must be computed *before* `authenticate(...)` is called and passed in as the `requestBody` argument.

The legacy path needs **no pipeline code at all** — `ThirdpartLegacyDispatcher` already delegates to `IntegrationInvokeService.invoke(..., InvokeContext.LEGACY)`, which calls the same `orchestrator.invoke()`. Wiring the orchestrator once (PIPE-01) gives PIPE-02 for free; the only Phase 3 work on the legacy side is an integration test proving core-result parity (D-08) and confirming `LegacyCompatResponseFormatter` stays envelope-only (D-09). Correlation-id/MDC and the audit `requestId` field are genuinely new (no MDC usage exists anywhere in the codebase today).

**Primary recommendation:** Insert mapping/transform as two private helper methods inside `DefaultIntegrationOrchestrator` (`applyRequestPipeline` returning the finalized body, `applyResponsePipeline` returning the mapped body), call `MappingConfigResolver.resolve` once at method top behind a cached lookup + `hasAnyMapping`/global-toggle guard, compute the request body BEFORE `authenticate(...)`, and capture the correlation id at `IntegrationInvokeService` entry into MDC with a `try/finally` clear. Reuse `ErrorMappingTrigger.shouldMapError` verbatim and reuse `RuntimeApiExceptionHandler` for failures.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Pipeline stage ordering (map→transform→auth→HTTP→eval→map) | Engine (`DefaultIntegrationOrchestrator`) | — | Orchestrator is the domain `IntegrationOrchestrator` impl; it already owns auth→HTTP→evaluate. Mapping/transform belong here, not in the API layer (which must not depend on auth directly). |
| Mapping execution (declarative + Groovy) | Mapping module (`MappingEngineImpl`) | Engine (caller) | Facade already built in Phase 2; engine only orchestrates it. |
| Crypto transform (SM4 encrypt/decrypt) | Mapping module (`TransformPipeline`) | Engine (caller) | Distinct bean by design to prevent crypto/auth reorder (Phase 2 Pitfall 1). |
| Mapping config resolution + passthrough gate | Engine (`MappingConfigResolver`) | — | Already engine-layer static resolver; merges connector default + endpoint override. |
| `ResolvedMapping` cache + invalidation | Engine (`ConnectorPublishListener`/registry lifecycle) | — | Same publish lifecycle as Phase 2 script-compile cache; invalidate on `onPublish`. |
| Correlation-id capture (read inbound header) | API (`IntegrationInvokeService`) | — | Only the API/servlet layer has `HttpServletRequest` access (`RequestContextHolder`); engine is framework-agnostic-ish. |
| MDC put/clear | API (`IntegrationInvokeService`) | — | Must wrap the whole invoke in API layer so all downstream engine log lines inherit it; clear in `finally`. |
| Audit emission (`requestId`, outcome) | API (`InvokeAuditLogger`/`InvokeAuditEvent`) | — | Audit already lives in API layer `doInvoke`/`doStream`. |
| Pipeline-failure → HTTP error translation | API (`RuntimeApiExceptionHandler`) | Mapping/Auth (exception types) | `@RestControllerAdvice` already maps `MappingException`/`AuthException`; reuse (D-20). |
| Streaming request-side pipeline | Engine (`DefaultIntegrationOrchestrator.invokeStream`) | API (`ServletStreamingInvocationSink`) | Request finalize before stream open (D-15); sink stays raw passthrough (D-16). |

## Standard Stack

> No external packages are installed this phase. The tables below reflect **existing** libraries already on the classpath that this phase uses. Versions are from `api-connector-dependencies/pom.xml` / STACK.md (codebase-verified, not `npm`).

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot (context, web) | 4.0.6 | Bean wiring (`IntegrationEngineConfiguration`), `@ConfigurationProperties` for the toggle, `@RestControllerAdvice` | Project framework; already wires every collaborator. [VERIFIED: codebase] |
| SLF4J API (`org.slf4j`) | (managed by Spring Boot) | `Logger`, **`MDC`** for correlation id (D-11), DEBUG stage timings (D-23), `integration.invoke.audit` logger | Already the mandated logging API; `MDC` is part of the same artifact — no new dependency. [VERIFIED: `InvokeAuditLogger` uses SLF4J] |
| Jayway JsonPath | 2.10.0 | `ResponseEvaluator` business-success / data extraction (response side, between transform-decrypt and mapResponse) | Already in `api-connector-engine`. [VERIFIED: STACK] |
| BouncyCastle bcprov-jdk18on | 1.80 | SM4 encrypt/decrypt inside `TransformPipeline` steps (no orchestrator-level crypto code) | Phase 2 transform steps; orchestrator only calls `applyRequest/applyResponse`. [VERIFIED: 02-06-SUMMARY] |
| Jackson databind | (managed) | `MappingContext.bodyAsMap()`, `LegacyCompatResponseFormatter` envelope serialization | Already used across modules. [VERIFIED: codebase] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit Jupiter 5 | (managed) | Unit tests for orchestrator ordering, trigger routing | All new unit tests. [VERIFIED: STACK] |
| AssertJ | (managed) | Fluent assertions in spec/engine tests | Alongside JUnit. [VERIFIED: STACK] |
| Spring Boot Test | 4.0.6 | `@SpringBootTest(RANDOM_PORT)` end-to-end invoke + legacy parity tests | PIPE-01/PIPE-02/PIPE-03 integration tests. [VERIFIED: `InvokeIntegrationTest`] |
| WireMock | 3.13.1 (standalone, test scope) | Stub vendor HTTP; verify outbound signed body / streamed chunks | Integration tests asserting HMAC-on-mapped-body and streaming. [VERIFIED: STACK, `InvokeIntegrationTest`] |
| JDK HttpClient (`JdkHttpTransport`) | JDK 21 | Existing transport; `exchange` (sync) + `exchangeStream` (SSE) | No change needed; orchestrator calls it after auth. [VERIFIED: codebase] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| SLF4J `MDC` for correlation id | A custom `ThreadLocal<String>` holder | MDC is already available, integrates with log patterns automatically, and is the standard. Custom ThreadLocal duplicates MDC and won't appear in log output without manual formatting. Use MDC. |
| Servlet `Filter`/`HandlerInterceptor` for MDC | Capture in `IntegrationInvokeService` entry | A filter would be cleaner for "all requests" but legacy + unified both funnel through `IntegrationInvokeService`, and the service already has `RequestContextHolder` access. D-11 scopes MDC to the invoke pipeline, so service-entry capture is sufficient and avoids touching security/filter chains. (A filter remains a valid Phase-5 refinement — see Open Questions Q3, RESOLVED.) |
| Micrometer `Timer` for stage timings | SLF4J DEBUG logs | D-23 explicitly defers Micrometer. DEBUG logs only. |
| Per-direction mapping flags | Single global `mapping-enabled` | D-22 chose one global flag for rollback simplicity. |

**Installation:** None — no external packages installed this phase. All collaborators are existing beans/classes on the classpath.

## Package Legitimacy Audit

**None — no external packages installed this phase.** This is a wiring phase that composes existing engine/mapping/api classes and uses already-present libraries (Spring Boot 4.0.6, SLF4J/MDC, JsonPath 2.10.0, BouncyCastle 1.80, Jackson). The Package Legitimacy Gate is skipped per phase scope (no `npm`/Maven dependency additions).

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                          INBOUND (via upstream gateway)
                                     │
            ┌────────────────────────┼─────────────────────────┐
            │                        │                          │
   Unified Proxy API          Legacy thirdpart URL        Streaming endpoint
 IntegrationProxyController   ThirdpartLegacyDispatcher   (SSE) controller
            │                        │ (pre-adapter D-07)        │
            │                        ▼                           │
            │            ProxyInvokeRequest(LEGACY)              │
            └───────────┬────────────┘                          │
                        ▼                                        ▼
          ┌─────────────────────────────────────────────────────────────┐
          │  IntegrationInvokeService  (API layer)                        │
          │  • capture correlationId: X-Request-Id → X-Trace-Id → UUID    │ D-10
          │  • MDC.put("requestId", id)              try { ... } finally{} │ D-11
          │  • rate limit, strict-endpoints, EndpointResolver.resolve     │
          │  • emit InvokeAuditEvent(requestId, outcome, latency)         │ D-13/14
          └─────────────────────────────┬───────────────────────────────┘
                                         │ orchestrator.invoke / invokeStream
                                         ▼
          ┌─────────────────────────────────────────────────────────────┐
          │  DefaultIntegrationOrchestrator  (engine layer)               │
          │                                                               │
          │  resolved = cache.get(connector,endpoint)  ──► MappingConfigResolver.resolve
          │  if !mappingEnabled || !hasAnyMapping(resolved):  PASSTHROUGH │ D-04/D-22
          │                                                               │
          │  REQUEST SIDE (D-01):                                         │
          │    body1 = mapRequest(ctx REQUEST, resolved)                  │
          │    body2 = transformPipeline.applyRequest(body1, spec.transform(), creds)
          │    auth  = authenticate(... requestBody = body2 ...)  ◄── HMAC signs body2
          │                                                               │
          │    httpResp = httpTransport.exchange(auth.body, auth.headers, auth.query)
          │                                                               │
          │  RESPONSE SIDE (D-02 strict mirror):                          │
          │    decoded   = transformPipeline.applyResponse(httpResp.body, …)
          │    evaluation = responseEvaluator.evaluate(spec.response, decoded)
          │    trigger    = new ErrorMappingTrigger(httpStatus, evaluation.success())
          │    if shouldMapError(trigger) && resolved.hasError(): mapError(...)   D-03
          │    else if evaluation.success():                      mapResponse(...) 
          │    else:                                              decoded (raw)
          └─────────────────────────────┬───────────────────────────────┘
                                         ▼
                                  JdkHttpTransport → VENDOR
                                         │
                (streaming: chunks pass RAW through ServletStreamingInvocationSink; D-16)
```

### Recommended Project Structure

No new modules or packages. Edits land in existing files:

```
api-connector-engine/.../engine/
├── DefaultIntegrationOrchestrator.java   # +MappingEngine, +TransformPipeline, +cache, +toggle; insert stages
├── MappingConfigResolver.java            # unchanged (called by orchestrator)
├── ConnectorPublishListener.java         # +invalidate ResolvedMapping cache on onPublish (D-21)
├── ResolvedMappingCache.java (NEW, optional)  # ConcurrentHashMap<key,ResolvedMapping> — Claude's discretion
└── config/IntegrationEngineConfiguration.java  # update integrationOrchestrator(...) bean signature

api-connector-api/.../api/
├── service/IntegrationInvokeService.java # correlationId capture + MDC try/finally; pass to audit
├── invoke/InvokeAuditEvent.java          # +requestId field, +outcome
├── invoke/InvokeAuditLogger.java         # +requestId, +outcome in key=value line
├── config/IntegrationInvokeProperties.java # +mappingEnabled (default true)
└── RuntimeApiExceptionHandler.java       # reuse existing MappingException/AuthException handlers (D-20)

api-connector-app/src/main/resources/application.yml  # integration.invoke.mapping-enabled: true (documented default)
```

### Pattern 1: Insert mapping/transform around the existing auth call (request side)
**What:** Compute the finalized outbound body BEFORE calling `authenticate(...)`, so HMAC signs the mapped+transformed body (MAP-06 / D-01).
**When to use:** `DefaultIntegrationOrchestrator.invoke()` and the request side of `invokeStream()`.
**Example:**
```java
// Source: derived from existing DefaultIntegrationOrchestrator.invoke() (engine)
ResolvedMapping resolved = resolvedMappingCache.get(spec, endpointSpec); // D-21 cached resolve
boolean mappingActive = mappingEnabled && MappingConfigResolver.hasAnyMapping(resolved); // D-04/D-22

String outboundBody = request.body();
if (mappingActive) {
    MappingContext reqCtx = new MappingContext(
            spec.code3rd(), MappingDirection.REQUEST, outboundBody,
            /* authSnapshot */ null, endpointMeta(resolved, endpointSpec));
    outboundBody = mappingEngine.mapRequest(reqCtx, resolved);           // D-01 step 1
}
outboundBody = transformPipeline.applyRequest(                            // D-01 step 2 (encrypt)
        outboundBody, spec.transform(), credentials);

// auth signs the FINAL body (D-01 step 3) — pass outboundBody as requestBody
AuthenticatedInvocation auth = authenticate(
        spec, resolved_, endpointSpec, credentials, request.query(), outboundBody);
```
**Anti-pattern note:** Do NOT map/transform inside the private `authenticate(...)` helper or after it — that would sign a stale body. Compute `outboundBody` first, then pass it in.

### Pattern 2: Strict-mirror response side (D-02 / D-03)
**What:** Reverse order on the response: decrypt → evaluate → map. Decrypt before success evaluation so `ResponseEvaluator` sees plaintext JSON.
**Example:**
```java
// Source: derived from existing DefaultIntegrationOrchestrator.invoke() response handling
String decoded = mappingActive || hasResponseTransform(spec)
        ? transformPipeline.applyResponse(httpResp.body(), spec.transform(), credentials)
        : httpResp.body();                                               // D-02 step 1

ResponseEvaluation evaluation = responseEvaluator.evaluate(spec.response(), decoded); // step 2

String finalBody = decoded;
if (mappingActive) {
    ErrorMappingTrigger trigger =
            new ErrorMappingTrigger(httpResp.statusCode(), evaluation.success()); // D-03
    MappingContext respCtx = new MappingContext(
            spec.code3rd(),
            ErrorMappingTrigger.shouldMapError(trigger) ? MappingDirection.ERROR : MappingDirection.RESPONSE,
            decoded, auth.snapshot(), endpointMeta(resolved, endpointSpec));
    if (ErrorMappingTrigger.shouldMapError(trigger) && resolved.hasError()) {
        finalBody = mappingEngine.mapError(respCtx, resolved, trigger);  // D-03 error route
    } else if (evaluation.success()) {
        finalBody = mappingEngine.mapResponse(respCtx, resolved);        // D-03 success route
    }
}
// build InvocationResult from finalBody + evaluation (rawBody = finalBody)
```
**Note:** `MappingEngineImpl.mapError` already internally re-checks `shouldMapError` + `hasError()` and returns `ctx.rawBody()` otherwise — calling it is safe, but the explicit gate keeps the success/error branch readable and avoids redundant `mapResponse` work.

### Pattern 3: Correlation id → MDC at service entry (D-10/D-11)
**What:** Read inbound header at the API layer (only place with servlet access), generate UUID fallback, put in MDC, clear in `finally`.
**Example:**
```java
// Source: new code in IntegrationInvokeService (API layer); uses existing RequestContextHolder pattern
private static final String MDC_KEY = "requestId";

private String resolveCorrelationId() {
    ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
        HttpServletRequest req = attrs.getRequest();
        String id = firstNonBlank(req.getHeader("X-Request-Id"), req.getHeader("X-Trace-Id")); // D-10
        if (id != null) return id;
    }
    return java.util.UUID.randomUUID().toString();                       // D-10 fallback
}

// wrap doInvoke / doStream:
String correlationId = resolveCorrelationId();
MDC.put(MDC_KEY, correlationId);                                         // D-11
try {
    // ... existing invoke logic; pass correlationId into InvokeAuditEvent (D-13)
} finally {
    MDC.remove(MDC_KEY);                                                 // D-11 clear
}
```
**Note (virtual threads):** `spring.threads.virtual.enabled: true`. SLF4J's default `MDC` uses a `ThreadLocal`; each request runs on its own (virtual) thread, so put/clear at service entry/exit is correct. The orchestrator runs on the same thread (synchronous `exchange`), so engine-layer DEBUG logs inherit the MDC value. (The async/streaming handler callback also runs on the same calling thread in `JdkHttpTransport.exchangeStream`'s line loop — confirm during execution; see Open Questions Q2, RESOLVED.)

### Pattern 4: Audit outcome classification (D-14)
**What:** Distinguish `SUCCESS` / `VENDOR_ERROR` / `PIPELINE_ERROR(<stage>)`.
**Example:**
```java
// SUCCESS: evaluation.success() && httpStatus < 400
// VENDOR_ERROR: HTTP completed but !success || httpStatus >= 400 (real vendorHttpStatus carried)
// PIPELINE_ERROR(stage): MappingException/AuthException thrown before/around HTTP — vendorHttpStatus=0
// In doInvoke, on caught typed exception, emit audit with outcome="PIPELINE_ERROR(mapRequest)" etc.,
// then rethrow so RuntimeApiExceptionHandler (D-20) produces the 4xx/5xx body.
```

### Anti-Patterns to Avoid
- **Signing before mapping/transform:** computing auth on `request.body()` then mapping afterward — breaks MAP-06. Always finalize body first.
- **Mapping the body inside `authenticate(...)`:** hides ordering and couples auth to mapping. Keep stages explicit and sequential in `invoke()`.
- **Re-resolving `ResolvedMapping` on every invoke for passthrough endpoints:** `hasAnyMapping()` fast path + cache (D-04/D-21) — do not call `mapRequest`/`mapResponse` when no mapping is configured.
- **Buffering the stream to map response chunks:** violates SC#5. Chunks pass raw (D-16); warn-once if response mapping configured (D-17).
- **Forwarding `requestId` to the vendor as a header:** D-12 forbids it. MDC + audit only.
- **Adding a new exception handler for pipeline failures:** reuse `RuntimeApiExceptionHandler` (D-20).
- **Logging request/response bodies in stage-timing DEBUG lines:** leaks SM4 plaintext/credentials. Log stage name + millis only.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Correlation-id propagation to logs | Custom `ThreadLocal` + manual log-line interpolation | SLF4J `MDC` (D-11) | MDC is on the classpath, integrates with log pattern `%X{requestId}`, standard. |
| Error-routing gate (when to map error vs response) | New `if (status>=400 || !success)` logic | `ErrorMappingTrigger.shouldMapError(...)` (Phase 2) | D-03 mandates reuse; already unit-tested. |
| Connector-default + endpoint-override merge | New resolution code | `MappingConfigResolver.resolve(spec, endpoint)` | Already built (Phase 2), passthrough-aware. |
| SM4 encrypt/decrypt ordering | Crypto calls in orchestrator | `TransformPipeline.applyRequest/applyResponse` | Distinct bean by design; orchestrator must not embed crypto. |
| Declarative + Groovy mapping dispatch | New mapping executor | `MappingEngine` facade (`MappingEngineImpl`) | Already dispatches rules vs compiled script per direction. |
| Pipeline-failure → HTTP status | New `@RestControllerAdvice` | Existing `RuntimeApiExceptionHandler` (MappingException/AuthException) | D-20. Already maps codes to 400/502. |
| Cache invalidation on publish | New listener | Extend `ConnectorPublishListener.onPublish` (D-21) | Same lifecycle as Phase 2 script-compile + token-evict cache. |

**Key insight:** Phase 3 builds almost nothing new — it *composes* Phase 1 (auth) and Phase 2 (mapping/transform) assets in the correct order. The risk surface is ordering/wiring, not algorithm implementation. Treat any new utility class as a smell unless it is the `ResolvedMapping` cache (the one genuinely new mechanism, and even that is a `ConcurrentHashMap`).

## Runtime State Inventory

> Phase 3 is a wiring/integration phase, not a rename/refactor/migration. Inventory is included because it adds a runtime-cached structure (`ResolvedMapping` cache) and MDC ThreadLocal state.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no datastore keys/collections renamed or added. `ResolvedMapping` cache is in-memory only, rebuilt from registry. | None |
| Live service config | New `ResolvedMapping` cache must invalidate on connector publish (same event as `ConnectorPublishListener.onPublish` → token evict + script compile). | Code edit: invalidate/evict cache entry for `code3rd` in `onPublish` (D-21). |
| OS-registered state | None | None |
| Secrets/env vars | New config key `integration.invoke.mapping-enabled` (boolean, default true). No secret. SM4 `keyRef` credential resolution unchanged (handled inside transform steps). | Code edit: add field to `IntegrationInvokeProperties`; document default in `application.yml`. |
| Build artifacts / installed packages | None — no new modules, no dependency changes, no fat-JAR layout change. | None |
| In-memory thread state (added) | MDC `requestId` ThreadLocal set per invoke (D-11). Under virtual threads, must be cleared in `finally` to avoid leakage if a carrier/platform thread is ever reused. | Code edit: `MDC.remove("requestId")` in `finally` on both invoke and stream paths. |

**Nothing found in category:** Stored data, OS-registered state, build artifacts — confirmed none by codebase inspection (no DB migrations, no new modules in scope).

## Common Pitfalls

### Pitfall 1: Body finalized after signing (MAP-06 violation)
**What goes wrong:** Mapping/transform applied to the response of `authenticate(...)` or interleaved so the HMAC is computed on the pre-mapping body. Vendor rejects signature.
**Why it happens:** The current `invoke()` builds the outbound body inside `authenticate(...)` (`auth.body()` = `authOutcome.mutatedBody() ?? requestBody`). Inserting mapping "near auth" without ordering discipline.
**How to avoid:** Compute `outboundBody = transform.applyRequest(mapRequest(...))` BEFORE calling `authenticate(...)`, and pass it as the `requestBody` argument (Pattern 1). Add an integration test (WireMock) asserting the signed outbound body equals the mapped body.
**Warning signs:** WireMock `verify(...withRequestBody(...))` shows unmapped body; vendor 401/signature-mismatch in tests.

### Pitfall 2: Transform direction confusion (encrypt vs decrypt)
**What goes wrong:** Calling `applyRequest` on the response, or response transform steps not configured with `direction: response`, so SM4 decrypt never runs and `ResponseEvaluator` parses ciphertext (always `success=false`).
**Why it happens:** `TransformPipeline` filters by step `direction` (default `request`). A connector that needs response decrypt must declare `direction: response` steps; the orchestrator calls `applyResponse`.
**How to avoid:** Orchestrator calls `applyResponse(httpResp.body(), spec.transform(), creds)` on the response side (Pattern 2). Document that decrypt steps need `direction: response` in spec. Test SM4 round-trip end-to-end.
**Warning signs:** `evaluation.success()` always false for encrypted vendors; garbled `parsedData`.

### Pitfall 3: MDC leak under virtual threads
**What goes wrong:** `requestId` from a prior request appears in an unrelated request's logs.
**Why it happens:** MDC `ThreadLocal` not cleared if an exception bypasses the clear, or thread reuse.
**How to avoid:** `try { MDC.put } finally { MDC.remove }` around the entire invoke/stream body (Pattern 3). Never put in a code path without a matching finally.
**Warning signs:** Cross-request `requestId` in audit logs; flaky log assertions.

### Pitfall 4: Streaming response mapping silently buffering
**What goes wrong:** Someone "completes" PIPE-04 by buffering streamed chunks to apply `mapResponse`, reintroducing the latency/memory regression SC#5 forbids.
**Why it happens:** Symmetry temptation ("response side should map too").
**How to avoid:** D-16 — chunks pass raw through `ServletStreamingInvocationSink`. If `resolved.hasResponse()` AND streaming, log a single `warn` (D-17) and skip. Only the request side runs in `invokeStream` (D-15).
**Warning signs:** `invokeStream` accumulates a `StringBuilder` of chunks; first-chunk latency rises.

### Pitfall 5: Legacy path diverging into a parallel pipeline
**What goes wrong:** Adding mapping/transform calls inside `ThirdpartLegacyDispatcher` instead of relying on the shared orchestrator → double-mapping or inconsistent behavior.
**Why it happens:** Misreading PIPE-02 as "legacy needs its own wiring."
**How to avoid:** D-06 — legacy already calls `IntegrationInvokeService.invoke(..., LEGACY)` → same `orchestrator.invoke()`. Do nothing on the dispatcher except keep pre/post adapters (D-07). `LegacyCompatResponseFormatter` stays envelope-only (D-09). Prove with a parity test (D-08).
**Warning signs:** Mapping logic appears in `api/legacy/*`; legacy and unified produce different `data`.

### Pitfall 6: Passthrough endpoints paying mapping cost
**What goes wrong:** `mapRequest`/`mapResponse` invoked (and `ResolvedMapping` re-resolved) for endpoints with no mapping → overhead regression vs Phase 2's zero-overhead passthrough (MAP-07).
**Why it happens:** Skipping the `hasAnyMapping()` guard.
**How to avoid:** D-04 — gate all mapping behind `mappingEnabled && hasAnyMapping(resolved)`; cache `ResolvedMapping` (D-21) so even the resolve is amortized.
**Warning signs:** Latency increase on `DEMO_NONE`-style endpoints; profiler shows `MappingConfigResolver.resolve` per invoke.

## Code Examples

### Extending InvokeAuditEvent with requestId + outcome (D-13/D-14)
```java
// Source: extend existing api-connector-api/.../invoke/InvokeAuditEvent.java (record)
public record InvokeAuditEvent(
        String code3rd, String endpointId, String method, String path,
        boolean success, int vendorHttpStatus, long latencyMillis,
        String clientAddress, String context,
        String requestId,      // D-13 NEW
        String outcome) {       // D-14 NEW: SUCCESS | VENDOR_ERROR | PIPELINE_ERROR(<stage>)
    // keep a backward-compatible convenience constructor defaulting requestId/outcome if needed
}
```

### Audit log line (D-13 structured key=value)
```java
// Source: extend InvokeAuditLogger.log(...) — append requestId & outcome to existing pattern
AUDIT.info("code3rd={} endpointId={} method={} path={} success={} vendorHttpStatus={} "
        + "latencyMs={} client={} context={} requestId={} outcome={}",
        e.code3rd(), e.endpointId(), e.method(), e.path(), e.success(),
        e.vendorHttpStatus(), e.latencyMillis(), e.clientAddress(), e.context(),
        e.requestId(), e.outcome());
```

### Global toggle (D-22)
```java
// Source: add to api-connector-api/.../config/IntegrationInvokeProperties.java
private boolean mappingEnabled = true;   // integration.invoke.mapping-enabled
public boolean isMappingEnabled() { return mappingEnabled; }
public void setMappingEnabled(boolean v) { this.mappingEnabled = v; }
```
```yaml
# api-connector-app/src/main/resources/application.yml
integration:
  invoke:
    platform-http-status: PLATFORM_OK
    audit-enabled: true
    mapping-enabled: true        # D-22 one-switch rollback
```
> **Wiring note:** `IntegrationInvokeProperties` lives in `api-connector-api`, but `DefaultIntegrationOrchestrator` is in `api-connector-engine` (api → engine, not the reverse). Pass the boolean into the orchestrator via its constructor/bean wiring in `IntegrationEngineConfiguration` (read from an engine-level `@Value`/property or a small engine-side properties bean), OR gate the mapping at the engine bean using a constructor flag set from config. Do **not** import the api-layer properties class into the engine. (See Open Questions Q1, RESOLVED.)

### Orchestrator bean signature update
```java
// Source: api-connector-engine/.../config/IntegrationEngineConfiguration.java
@Bean
public IntegrationOrchestrator integrationOrchestrator(
        ConnectorRegistry registry, AuthEngine authEngine, HttpTransport httpTransport,
        MappingEngine mappingEngine, TransformPipeline transformPipeline,
        @Value("${integration.invoke.mapping-enabled:true}") boolean mappingEnabled) {
    return new DefaultIntegrationOrchestrator(
            registry, authEngine, httpTransport, new ResponseEvaluator(),
            mappingEngine, transformPipeline, mappingEnabled);
}
```

### Integration test skeleton: HMAC signs mapped body (SC#3, MAP-06)
```java
// Source: pattern from api-connector-app/.../InvokeIntegrationTest.java (WireMock + RANDOM_PORT)
// 1. register connector with request mapping (rename a→b) + aksk_hmac auth + WireMock vendor
// 2. POST {"a":1} to /api/v1/integrations/{code}/endpoints/{id}/invoke
// 3. wireMock.verify(postRequestedFor(...).withRequestBody(matchingJsonPath("$.b"))
//        .withHeader("X-Auth-Signature", matching("[0-9a-f]{64}")));
//    → proves body mapped BEFORE signature computed
```

### Integration test skeleton: legacy/unified parity (SC#2, D-08)
```java
// Source: pattern from api-connector-app/.../LegacyCompatIntegrationTest.java
// Invoke the SAME endpoint via (a) unified API and (b) legacy URL.
// Assert equal CORE result: success, vendorCode, parsed data, mapped body.
// Do NOT assert byte-identity of outer envelope (LegacySuntekResult may differ) — D-08.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Orchestrator: `Spec → Auth → HTTP → Response-eval` (no mapping/transform) | `Spec → mapRequest → transform → Auth → HTTP → transform → eval → mapResponse/mapError` | This phase (Phase 3) | Mapping/transform become first-class pipeline stages; auth signs mapped body. |
| Mapping/transform existed as beans but **unwired** (Phase 2 D-26 deferred wiring) | Wired into `DefaultIntegrationOrchestrator` | This phase | Closes MAP-06/PIPE-01. |
| Audit line: 9 fields, no correlation id | +`requestId` (MDC-backed) +`outcome` | This phase (D-13/D-14) | PIPE-03; cross-line traceability via `%X{requestId}`. |
| No MDC usage anywhere in codebase | MDC `requestId` set at invoke entry | This phase (D-11) | First MDC use; logging pattern may need `%X{requestId}` (see Open Questions Q4, RESOLVED). |

**Deprecated/outdated:** none — this phase adds, it does not deprecate. Phase 2 deliberately left orchestrator wiring as a stub (`TransformPipeline` Javadoc: "the orchestrator wires the full ordering in Phase 3").

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `JdkHttpTransport.exchangeStream` invokes the `HttpStreamHandler.onLine` callback on the **same calling thread** (so MDC set at service entry is visible in streaming audit logs). | Pattern 3 / Open Questions Q2 | If transport dispatches callbacks on a worker thread, streaming audit `requestId` could be empty. Mitigation: pass `correlationId` explicitly into the audit event (already planned via D-13) rather than relying solely on MDC inheritance in the stream callback. Risk LOW (audit value is passed explicitly). |
| A2 | The mandatory mapping `MappingContext` for the request side can pass `authSnapshot = null` (auth runs after mapRequest, so no snapshot exists yet). Response-side mapping passes the real `auth.snapshot()`. | Pattern 1/2 | If a request-mapping Groovy script reads `ctx.authSnapshot()`, it would NPE. Phase 1/2 contract: AuthContextSnapshot is available to mapping for the *same* pipeline — but on the request side auth has not run. Recommend documenting that request-mapping scripts must not depend on auth output (auth depends on mapped body, not vice versa). Risk MEDIUM — confirm with planner whether any request-mapping script needs auth snapshot (AUTH-05 wording). |

**Note:** A1/A2 are the only genuinely-unverified items. Everything else is `[VERIFIED: codebase]` from direct source inspection.

## Open Questions

1. **How does the engine-layer orchestrator read the `integration.invoke.mapping-enabled` flag without importing the api-layer `IntegrationInvokeProperties`?**
   - What we know: `IntegrationInvokeProperties` is in `api-connector-api`; orchestrator is in `api-connector-engine`; dependency direction is api→engine (engine must not depend on api).
   - What's unclear: nothing blocking — multiple valid wirings.
   - **RESOLVED — Recommendation:** Inject the boolean into `DefaultIntegrationOrchestrator`'s constructor and supply it in `IntegrationEngineConfiguration` via `@Value("${integration.invoke.mapping-enabled:true}")` on the `@Bean` factory method (engine config already reads Spring context). No cross-module type dependency. Keep `IntegrationInvokeProperties.mappingEnabled` for the api layer's own use / documentation if desired, but the engine reads the raw property.

2. **Does `JdkHttpTransport.exchangeStream` call back on the calling thread (MDC visibility for streaming audit)?**
   - What we know: `IntegrationInvokeService.doStream` builds an `auditing` sink and calls `orchestrator.invokeStream` synchronously; audit is emitted in the sink's `complete()`/`fail()`.
   - What's unclear: whether the JDK HttpClient line loop runs on the caller thread.
   - **RESOLVED — Recommendation:** Do not rely on MDC inheritance for the streaming audit value. Capture `correlationId` as a local at service entry and pass it explicitly into the `InvokeAuditEvent` (D-13 already requires the field). MDC is still set for any synchronous engine DEBUG logs. This makes correctness independent of the callback thread. (Execution should still verify by reading `JdkHttpTransport.exchangeStream`.)

3. **Should MDC capture be a servlet `Filter`/interceptor instead of service-entry code?**
   - What we know: D-11 scopes MDC to the invoke pipeline; both unified and legacy funnel through `IntegrationInvokeService`.
   - **RESOLVED — Recommendation:** Service-entry capture (Pattern 3) satisfies D-11 with the least surface area and keeps legacy + unified covered by one code path. A global filter is a valid Phase-5 observability refinement but is **out of scope** here (would also catch admin/console requests, which D-11 does not require).

4. **Does the logging pattern need `%X{requestId}` to surface the MDC value in non-audit log lines?**
   - What we know: The audit line passes `requestId` explicitly (not via `%X`). General app log pattern is Spring Boot default.
   - **RESOLVED — Recommendation:** PIPE-03 only requires the **audit** line to carry the correlation id, which is done explicitly via the new field — no logback pattern change is strictly required to satisfy the requirement. Optionally add `%X{requestId}` to the console/file pattern in `application.yml` (`logging.pattern.level` or `logging.pattern.console`) so DEBUG stage-timing lines (D-23) also show it. Treat the pattern tweak as a nice-to-have, not a blocker.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | All compilation/runtime | ✓ | 21 (Zulu) | — |
| Maven Wrapper (`mvnw-jdk21.ps1`) | Build/test | ✓ | 3.9.11 | — |
| Spring Boot | Bean wiring, config props | ✓ | 4.0.6 | — |
| WireMock | Integration tests | ✓ | 3.13.1 (test scope) | — |
| BouncyCastle | SM4 transform (existing) | ✓ | 1.80 | — |
| SLF4J + MDC | Correlation id / audit | ✓ | bundled w/ Spring Boot | — |

**Missing dependencies with no fallback:** none — all required tooling/libraries already present.
**Missing dependencies with fallback:** none.

## Validation Architecture

> nyquist_validation is ENABLED (`workflow.nyquist_validation: true` in config.json).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + AssertJ; Spring Boot Test 4.0.6 for integration; WireMock 3.13.1 (standalone, test scope) for vendor stubbing |
| Config file | none (Maven Surefire convention; `api-connector-parent/pom.xml` configures Surefire). No `pytest.ini`/`jest.config` equivalent — Maven discovers `*Test.java`. |
| Quick run command | `.\mvnw-jdk21.ps1 -pl api-connector-engine test -Dtest=DefaultIntegrationOrchestratorTest` (unit, ordering) |
| Full suite command | `.\mvnw-jdk21.ps1 clean verify` (all modules, includes Spring Boot integration tests) |

> Module-scoped integration runs need `-am` to build upstream modules: `.\mvnw-jdk21.ps1 -pl api-connector-app -am test -Dtest=InvokeIntegrationTest`.

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MAP-06 | Request mapping applied before auth signing (HMAC over mapped body) | integration | `.\mvnw-jdk21.ps1 -pl api-connector-app -am test -Dtest=InvokeIntegrationTest` (add test method `hmacSignsMappedRequestBody`) | ⚠️ file exists, method ❌ Wave 0 |
| MAP-06 | Stage ordering unit (mapRequest→transform→auth) with fakes | unit | `.\mvnw-jdk21.ps1 -pl api-connector-engine test -Dtest=DefaultIntegrationOrchestratorTest` (add ordering test) | ⚠️ file exists, method ❌ Wave 0 |
| PIPE-01 | Full pipeline resolve→map→auth→HTTP→eval→mapResponse end-to-end | integration | `...-Dtest=InvokeIntegrationTest` (add `unifiedInvokeRunsFullPipeline`) | ⚠️ file exists, method ❌ Wave 0 |
| PIPE-01 | Passthrough endpoint skips mapping (zero overhead, D-04) | unit | `...-Dtest=DefaultIntegrationOrchestratorTest` (add `passthroughSkipsMappingEngine` with mock verify never-called) | ❌ Wave 0 |
| PIPE-02 | Legacy URL + unified API produce identical core result (D-08) | integration | `.\mvnw-jdk21.ps1 -pl api-connector-app -am test -Dtest=LegacyCompatIntegrationTest` (add `legacyAndUnifiedCoreResultParity`) | ⚠️ file exists, method ❌ Wave 0 |
| PIPE-03 | Audit line carries requestId + outcome | unit/integration | `.\mvnw-jdk21.ps1 -pl api-connector-api test -Dtest=InvokeAuditLoggerTest` (NEW) + assert via log capture or event fields | ❌ Wave 0 |
| PIPE-03 | Correlation id sourced from X-Request-Id / fallback UUID (D-10) | unit | `.\mvnw-jdk21.ps1 -pl api-connector-api test -Dtest=IntegrationInvokeServiceTest` (NEW, `resolveCorrelationId`) | ❌ Wave 0 |
| PIPE-04 | Streaming applies request side, passes chunks raw, warn-once on response mapping (D-15/16/17) | integration | `...-Dtest=InvokeIntegrationTest` (add SSE streaming test w/ WireMock chunked stub) | ⚠️ file exists, method ❌ Wave 0 |
| PIPE-04 | Pre-stream failure throws before any chunk (D-18) | unit | `...-Dtest=DefaultIntegrationOrchestratorTest` (add `preStreamMappingFailureThrowsNoChunk`) | ❌ Wave 0 |
| MAP-06/D-02 | Response side: SM4 decrypt before evaluate; error→mapError, success→mapResponse (D-03) | unit | `...-Dtest=DefaultIntegrationOrchestratorTest` (add response-mirror + error-routing tests) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `.\mvnw-jdk21.ps1 -pl api-connector-engine test -Dtest=DefaultIntegrationOrchestratorTest` (fast unit feedback on ordering).
- **Per wave merge:** `.\mvnw-jdk21.ps1 -pl api-connector-app -am test` (engine + api + app integration tests via WireMock).
- **Phase gate:** `.\mvnw-jdk21.ps1 clean verify` green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `DefaultIntegrationOrchestratorTest.java` — exists; ADD methods for request ordering, response mirror, error routing, passthrough skip, pre-stream failure (REQ MAP-06, PIPE-01, PIPE-04, D-02/D-03).
- [ ] `InvokeIntegrationTest.java` — exists; ADD `hmacSignsMappedRequestBody`, `unifiedInvokeRunsFullPipeline`, SSE streaming test (REQ MAP-06, PIPE-01, PIPE-04).
- [ ] `LegacyCompatIntegrationTest.java` — exists; ADD `legacyAndUnifiedCoreResultParity` (REQ PIPE-02, D-08).
- [ ] `IntegrationInvokeServiceTest.java` — NEW; correlation-id resolution + MDC put/clear (REQ PIPE-03, D-10/D-11).
- [ ] `InvokeAuditLoggerTest.java` — NEW (or extend existing audit coverage); assert requestId + outcome in line (REQ PIPE-03, D-13/D-14).
- [ ] No new framework install needed — JUnit 5 / Spring Boot Test / WireMock all present.

## Security Domain

> security_enforcement ENABLED (`security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high`).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Outbound vendor auth via `AuthEngine`/`AuthProvider` (HMAC SHA-256, SM3 header sign, OAuth2, AK/SK). Phase 3 must sign the **finalized** body (MAP-06) so the signature is valid. Caller auth is upstream gateway's concern (out of scope). |
| V3 Session Management | no | Stateless proxy; no server session. Correlation id is a trace id, not a session token. |
| V4 Access Control | partial | Strict-endpoints enforcement (`StrictEndpointsEnforcer`) + optional API-key filter exist; Phase 3 does not change them. Ensure legacy path keeps the same `InvokeContext.LEGACY` access posture (no privilege escalation via legacy URL). |
| V5 Input Validation | yes | Mapping spec validated at publish (`MappingSpecValidator`); JsonPath parsing in `ResponseEvaluator` is defensive (catches parse errors → success=false). Pipeline failures return structured `MAPPING_*` codes, not stack traces (D-19/D-20). |
| V6 Cryptography | yes | SM4 via BouncyCastle inside `TransformPipeline` (never hand-rolled in orchestrator). SM4 key via `keyRef`→credentials, never inline (Phase 2 Pitfall 6). Orchestrator must call `applyRequest`/`applyResponse`, not implement crypto. |
| V7 Error Handling & Logging | yes | Structured audit (`key=value`); add `requestId`/`outcome`. **Do not log bodies/credentials** in DEBUG stage timings (D-23) or audit. Clear MDC in `finally` (no cross-request leakage). |

### Known Threat Patterns for Java/Spring HTTP proxy pipeline
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Signature computed on wrong (pre-mapping) body → forged/invalid outbound auth or replayable mismatch | Tampering / Spoofing | MAP-06: finalize body (map+transform) before `authenticate(...)`; integration test asserts HMAC over mapped body. |
| Sensitive data (SM4 plaintext, credentials, request bodies) leaked into logs | Information Disclosure | D-23: stage timings log stage+millis only; audit logs metadata only; never log `body`/`credentials`/`keyRef` values. |
| Correlation-id log injection / forgery via inbound `X-Request-Id` header (CRLF, oversized) | Tampering / Repudiation | Treat inbound id as untrusted: it is written to MDC only (not to outbound vendor header — D-12). Recommend sanitizing (strip CR/LF, cap length, allow-list charset) before `MDC.put` to prevent log-forging. (Add to plan as a small hardening task.) |
| MDC ThreadLocal leakage under virtual threads → wrong requestId attributed | Repudiation | `try/finally` clear (D-11); pass correlation id explicitly into audit event so attribution is independent of thread reuse. |
| Legacy URL bypasses validation/strict-endpoints that unified path enforces | Elevation of Privilege | D-06/D-07: legacy routes through the *same* `IntegrationInvokeService.invoke(..., LEGACY)`; adapters do not skip auth/mapping. Parity test (D-08) guards against divergence. |
| Pipeline-failure stack traces returned to caller | Information Disclosure | D-19/D-20: structured `{code,message,details}` via `RuntimeApiExceptionHandler`; 5xx for server-side config errors, vendor status preserved separately. |
| Groovy mapping/transform script abuse (resource exhaustion / injection) | Tampering / DoS | Scripts compiled+contract-validated at publish (`ConnectorPublishListener`); Phase 3 only executes already-validated compiled scripts. Sandboxing is a v2 concern (AUTH-V2-02), not this phase. |

**Security note for the planner:** Add a small hardening task to **sanitize the inbound correlation-id header** before `MDC.put` (strip `\r`/`\n`, cap length ~128 chars). This is the only net-new untrusted-input surface this phase introduces and maps directly to ASVS V7 / log-injection. `security_block_on: high` — log injection is typically medium, so it should not block, but include it.

## Sources

### Primary (HIGH confidence)
- Codebase (direct read) — `DefaultIntegrationOrchestrator`, `MappingConfigResolver`, `MappingEngine`/`MappingEngineImpl`, `TransformPipeline`, `ErrorMappingTrigger`, `ResolvedMapping`, `ResponseEvaluator`/`ResponseEvaluation`, `IntegrationInvokeService`, `InvokeAuditEvent`/`InvokeAuditLogger`, `IntegrationInvokeProperties`, `ThirdpartLegacyDispatcher`, `LegacyCompatResponseFormatter`, `ServletStreamingInvocationSink`, `RuntimeApiExceptionHandler`, `ConnectorRegistry`, `ConnectorPublishListener`, `IntegrationEngineConfiguration`, `EndpointResolver`, `MappingContext`, `MappingDirection`, `ConnectorSpec`, `InvokeContext` — all signatures and ordering verified.
- Tests (direct read) — `DefaultIntegrationOrchestratorTest`, `InvokeIntegrationTest` (WireMock + RANDOM_PORT patterns).
- `.planning/phases/03-orchestrator-pipeline-integration/03-CONTEXT.md` (locked D-01..D-23).
- `.planning/phases/02-data-mapping-engine/02-06-SUMMARY.md` (TransformPipeline handoff; "orchestrator wires order in Phase 3").
- `docs/adr/002-auth-and-transform.md` (auth/transform separation, SM4 first-class).
- `CLAUDE.md` (stack, module boundaries, conventions, error handling, logging).
- `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md`, `.planning/config.json`.
- `application.yml` / `application-prod.yml` (`integration.invoke.*` config block confirmed).

### Secondary (MEDIUM confidence)
- SLF4J `MDC` API usage under virtual threads — standard knowledge; cross-checked against the fact that the codebase already uses SLF4J and Spring Boot enables virtual threads. (Behavioral confirmation deferred to execution — Open Questions Q2.)

### Tertiary (LOW confidence)
- `JdkHttpTransport.exchangeStream` callback threading model (A1/Q2) — not re-read this session; mitigated by passing correlation id explicitly into the audit event.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already on classpath, versions from `dependencies` BOM / STACK; no installs.
- Architecture: HIGH — every collaborator and its signature read directly; ordering constraints come verbatim from locked CONTEXT decisions and Phase 2 summary.
- Pitfalls: HIGH — derived from actual current `invoke()` structure (body built inside `authenticate`), Phase 2 Pitfall notes, and virtual-thread MDC mechanics.
- Validation: HIGH — existing JUnit5/WireMock/Spring Boot Test infrastructure inspected; test files identified.
- Security: MEDIUM-HIGH — ASVS mapping grounded in actual auth/transform/validation code; log-injection hardening is a recommendation.

**Research date:** 2026-06-18
**Valid until:** 2026-07-18 (stable — internal codebase, no fast-moving external deps).
