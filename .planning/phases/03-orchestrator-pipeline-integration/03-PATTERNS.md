# Phase 3: Orchestrator Pipeline Integration - Pattern Map

**Mapped:** 2026-06-18
**Files analyzed:** 15 (10 source/config + 5 test)
**Analogs found:** 15 / 15 (every file modifies or sits beside an existing analog — this is a wiring phase, no greenfield class without a sibling)

> **Key framing:** Phase 3 builds almost nothing new. 9 of 10 source/config targets are **edits to existing files** — the file *is its own analog*. The single genuinely-new class (`ResolvedMappingCache`) copies the `ConcurrentHashMap` idiom already used in `ConnectorRegistry`/`TokenCache`. All excerpts below are real, line-numbered, from the current `dev` tree.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `engine/DefaultIntegrationOrchestrator.java` (MODIFY) | service (orchestrator) | request-response | itself — current `invoke()`/`invokeStream()` + private `authenticate(...)` | exact (self) |
| `engine/ResolvedMappingCache.java` (NEW) | utility (cache) | transform | `ConnectorRegistry` `ConcurrentHashMap` fields (l.130-140) | role-match |
| `engine/ConnectorPublishListener.java` (MODIFY) | config (lifecycle hook) | event-driven | itself — `onPublish` → `tokenCache.evictForConnector` (l.62-76) | exact (self) |
| `engine/config/IntegrationEngineConfiguration.java` (MODIFY) | config | — | itself — `integrationOrchestrator(...)` bean (l.99-106) | exact (self) |
| `api/service/IntegrationInvokeService.java` (MODIFY) | service | request-response | itself — `doInvoke`/`doStream` + `clientAddress()` (l.182-242) | exact (self) |
| `api/invoke/InvokeAuditEvent.java` (MODIFY) | model (record) | — | itself — record + compat ctor (l.9-31) | exact (self) |
| `api/invoke/InvokeAuditLogger.java` (MODIFY) | utility (logger) | — | itself — `key=value` SLF4J line (l.18-30) | exact (self) |
| `api/config/IntegrationInvokeProperties.java` (MODIFY) | config | — | itself — `auditEnabled` boolean prop (l.20,46-52) | exact (self) |
| `api/RuntimeApiExceptionHandler.java` (REUSE, no edit) | middleware (advice) | request-response | itself — `handleMapping`/`handleAuth` (l.48-73) | exact (self) — D-20 reuse |
| `app/resources/application.yml` (MODIFY) | config | — | existing `integration.invoke.*` block | exact (self) |
| `engine/.../DefaultIntegrationOrchestratorTest.java` (MODIFY) | test (unit) | request-response | itself — fake `HttpTransport` + `AuthEngine` (l.44-79) | exact (self) |
| `app/.../InvokeIntegrationTest.java` (MODIFY) | test (integration) | request-response | itself — WireMock `verify(...withHeader(...))` (l.187-214) | exact (self) |
| `app/.../LegacyCompatIntegrationTest.java` (MODIFY) | test (integration parity) | request-response | itself — legacy URL `GET /idps/...` (l.74-85) | exact (self) |
| `api/.../IntegrationInvokeServiceTest.java` (NEW) | test (unit) | request-response | `DefaultIntegrationOrchestratorTest` fakes + `InvokeIntegrationTest` MDC assert | role-match |
| `api/.../InvokeAuditLoggerTest.java` (NEW) | test (unit) | — | `InvokeAuditLogger.log` + a logback `ListAppender` capture | role-match |

---

## Pattern Assignments

### `engine/DefaultIntegrationOrchestrator.java` (service/orchestrator, request-response) — MODIFY

**Analog:** itself (the current pipeline is the skeleton; insert stages around `authenticate(...)`).

**Constructor injection pattern to extend** (`DefaultIntegrationOrchestrator.java` l.36-58) — add `MappingEngine`, `TransformPipeline`, `ResolvedMappingCache`, `boolean mappingEnabled` as new `final` fields + ctor params (keep the existing 4-arg ctor or add a wider one; tests use the 4-arg form l.60-61, so keep a backward-compat ctor that defaults mapping off / passthrough):

```36:58:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
    private final ConnectorRegistry registry;
    private final AuthEngine authEngine;
    private final HttpTransport httpTransport;
    private final ResponseEvaluator responseEvaluator;

    /**
     * 构造编排器。
     * ...
     */
    public DefaultIntegrationOrchestrator(
            ConnectorRegistry registry,
            AuthEngine authEngine,
            HttpTransport httpTransport,
            ResponseEvaluator responseEvaluator) {
        this.registry = registry;
        this.authEngine = authEngine;
        this.httpTransport = httpTransport;
        this.responseEvaluator = responseEvaluator;
    }
```

**Request-side insertion point** — the current `invoke()` passes `request.body()` straight into `authenticate(...)` (l.75-76). Per **D-01 / Pitfall 1**, compute the finalized body FIRST, then pass it as the `requestBody` arg:

```64:106:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
    public InvocationResult invoke(InvocationRequest request) {
        long start = System.currentTimeMillis();
        ConnectorSpec spec = registry.require(request.connectorCode().value());
        Map<String, String> credentials = registry.credentials(request.connectorCode().value());

        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(
                spec, request.endpointId(), request.method().name(), request.path());
        EndpointSpec endpointSpec = EndpointResolver.endpoint(spec, request.endpointId());
        AuthenticatedInvocation auth = authenticate(
                spec, resolved, endpointSpec, credentials, request.query(), request.body());
        // ↑ INSERT mapRequest→transform.applyRequest BEFORE this call; pass result as last arg.

        HttpTransportResponse httpResp = httpTransport.exchange(new HttpTransportRequest(
                spec.baseUrl(), resolved.method(), resolved.path(),
                auth.query(), auth.headers(), auth.body(), spec.transport()));

        ResponseEvaluation evaluation = responseEvaluator.evaluate(spec.response(), httpResp.body());
        // ↑ INSERT transform.applyResponse BEFORE evaluate (D-02); INSERT mapResponse/mapError AFTER.
        return new InvocationResult(httpResp.statusCode(), evaluation.success(), ...);
    }
```

**New request helper** (Claude's discretion: inline vs private helper — RESEARCH Pattern 1). Use the resolved-mapping fast path + the existing collaborator signatures:
- `MappingConfigResolver.resolve(spec, endpoint)` → `ResolvedMapping` (`MappingConfigResolver.java` l.23-36)
- `MappingConfigResolver.hasAnyMapping(resolved)` gate (l.41-44)
- `mappingEngine.mapRequest(ctx, resolved)` (`MappingEngine.java` l.18)
- `transformPipeline.applyRequest(body, spec.transform(), credentials)` (`TransformPipeline.java` l.40-43)
- `MappingContext` ctor is **5-arg** `(code3rd, direction, rawBody, authSnapshot, endpoint)` (`MappingContext.java` l.14-19) — request side passes `authSnapshot=null` (Assumption A2), endpoint via `new EndpointMeta(id, method, path)` (`EndpointMeta.java` l.9).

**Response-side mirror** (D-02/D-03) — reuse `ErrorMappingTrigger` verbatim, do NOT reimplement the gate:

```12:23:api-connector-mapping/src/main/java/com/suntek/apiconnector/mapping/ErrorMappingTrigger.java
public record ErrorMappingTrigger(int httpStatus, boolean businessSuccess) {
    public static boolean shouldMapError(ErrorMappingTrigger trigger) {
        if (trigger == null) {
            return false;
        }
        return trigger.httpStatus() >= 400 || !trigger.businessSuccess();
    }
}
```
Build `new ErrorMappingTrigger(httpResp.statusCode(), evaluation.success())`, then `shouldMapError(t) && resolved.hasError()` → `mapError(ctx, resolved, trigger)`; else `evaluation.success()` → `mapResponse(ctx, resolved)`; else raw decoded body.

**Streaming (D-15/16/17)** — `invokeStream()` (l.108-162) already calls `authenticate(...)` at l.128-129; insert ONLY the request side before it (same as `invoke`). Chunks pass raw through the existing `HttpStreamHandler.onLine` (l.146-156) — do NOT buffer (Pitfall 4). If `resolved.hasResponse()` AND streaming, `warn`-once and skip (D-17).

**Anti-pattern (Pitfall 1):** never map/transform inside or after `authenticate(...)` (l.164-198) — `auth.body()` would sign a stale body.

---

### `engine/ResolvedMappingCache.java` (utility/cache, transform) — NEW (optional, D-21)

**Analog:** `ConnectorRegistry` in-memory `ConcurrentHashMap` idiom (l.130-140, l.142-146) + `TokenCache.evictForConnector` invalidation pattern.

**Pattern to copy** — `ConcurrentHashMap` keyed by `code3rd + ":" + endpointId`, `computeIfAbsent` to resolve-once, an `evict(code3rd)` that drops all keys for the connector (called from `ConnectorPublishListener.onPublish`). Mirror the registry's credential-map shape:

```130:140:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorRegistry.java
    private void mergeCredentials(String code3rd, Map<String, String> credentialPatch) {
        if (credentialPatch == null || credentialPatch.isEmpty()) {
            return;
        }
        Map<String, String> target = credentials.computeIfAbsent(code3rd, k -> new ConcurrentHashMap<>());
        ...
    }
```

PCI header + `@author Gensokyo`/`@version 1.0.0`/`@since 2026-06-18` + Javadoc on public methods are MANDATORY (CLAUDE.md). Keep it a `final` class.

---

### `engine/ConnectorPublishListener.java` (config/lifecycle, event-driven) — MODIFY (D-21)

**Analog:** itself — `onPublish` already evicts the token cache; add a symmetric `resolvedMappingCache.evict(spec.code3rd())` call right beside it.

```62:77:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    public void onPublish(ConnectorSpec spec) {
        try {
            onPublishInternal(spec);
        } ...
    }

    private void onPublishInternal(ConnectorSpec spec) {
        validateMappingSpec(spec);
        tokenCache.evictForConnector(spec.code3rd());   // ← ADD resolvedMappingCache.evict(spec.code3rd()) next to this
        ...
    }
```
Inject the cache via the constructor — follow the existing optional-collaborator overload pattern (l.46-57, where `transformStepRegistry` was added as a 3rd ctor arg with a 2-arg back-compat ctor). Add a 4-arg ctor; keep older ctors delegating.

---

### `engine/config/IntegrationEngineConfiguration.java` (config) — MODIFY

**Analog:** itself — extend the `integrationOrchestrator(...)` `@Bean` factory. `MappingEngine` (l.179-183) and `TransformPipeline` (l.206-208) beans already exist in this same file, so just add them as method params + the `@Value` toggle (RESEARCH Open Q1 RESOLVED — engine reads the raw property, never imports api-layer `IntegrationInvokeProperties`):

```99:106:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    @Bean
    public IntegrationOrchestrator integrationOrchestrator(
            ConnectorRegistry registry,
            AuthEngine authEngine,
            HttpTransport httpTransport) {
        return new DefaultIntegrationOrchestrator(
                registry, authEngine, httpTransport, new ResponseEvaluator());
    }
```
Target shape (add params): `MappingEngine mappingEngine, TransformPipeline transformPipeline, ResolvedMappingCache resolvedMappingCache, @Value("${integration.invoke.mapping-enabled:true}") boolean mappingEnabled`. Also register `ResolvedMappingCache` as a `@Bean` and pass it into `connectorPublishListener(...)` (l.57-63) for D-21 invalidation.

---

### `api/service/IntegrationInvokeService.java` (service, request-response) — MODIFY (D-10/D-11/D-13/D-14)

**Analog:** itself — the existing static `clientAddress()` (l.230-242) is the EXACT template for `resolveCorrelationId()` (same `RequestContextHolder`/`ServletRequestAttributes`/`getHeader` idiom). Copy it:

```230:242:api-connector-api/src/main/java/com/suntek/apiconnector/api/service/IntegrationInvokeService.java
    private static String clientAddress() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
```
New `resolveCorrelationId()`: `getHeader("X-Request-Id")` → fallback `getHeader("X-Trace-Id")` → `UUID.randomUUID().toString()` (D-10). **Sanitize** before MDC (Security note: strip `\r`/`\n`, cap ~128 chars — log-injection ASVS V7).

**MDC try/finally wrap** — both `doInvoke` (l.182-228) and `doStream` (l.102-146) already have the `start`/audit-emit structure; wrap their bodies. Add `import org.slf4j.MDC;` (SLF4J already on classpath via the audit logger). Pass `correlationId` + computed `outcome` explicitly into `InvokeAuditEvent` (do NOT rely on MDC inheritance in the stream callback — RESEARCH A1/Q2):

```215:226:api-connector-api/src/main/java/com/suntek/apiconnector/api/service/IntegrationInvokeService.java
        if (invokeProperties.isAuditEnabled()) {
            auditLogger.log(new InvokeAuditEvent(
                    code3rd,
                    resolved.endpointId(),
                    resolved.method(),
                    resolved.path(),
                    response.isSuccess(),
                    response.getVendorHttpStatus(),
                    System.currentTimeMillis() - start,
                    clientAddress(),
                    context.name()));   // ← add requestId + outcome args (D-13/D-14)
        }
```
Read toggle via existing `invokeProperties` if the api layer needs it; the engine gate is independent (Open Q1). Outcome classification (D-14): `SUCCESS` / `VENDOR_ERROR` / `PIPELINE_ERROR(<stage>)` — set in the `catch` of typed exceptions, then rethrow so `RuntimeApiExceptionHandler` produces the body.

---

### `api/invoke/InvokeAuditEvent.java` (model/record) — MODIFY (D-13/D-14)

**Analog:** itself — add `String requestId, String outcome` to the record header; the existing compat constructor (l.20-30) is the template for keeping old 9-arg call sites compiling (default `requestId=null/"-"`, `outcome="SUCCESS"`).

```9:31:api-connector-api/src/main/java/com/suntek/apiconnector/api/invoke/InvokeAuditEvent.java
public record InvokeAuditEvent(
        String code3rd, String endpointId, String method, String path,
        boolean success, int vendorHttpStatus, long latencyMillis,
        String clientAddress, String context) {

    public InvokeAuditEvent(
            String code3rd, String endpointId, String method, String path,
            boolean success, int vendorHttpStatus, long latencyMillis, String clientAddress) {
        this(code3rd, endpointId, method, path, success, vendorHttpStatus, latencyMillis, clientAddress, "RUNTIME");
    }
}
```

---

### `api/invoke/InvokeAuditLogger.java` (utility/logger) — MODIFY (D-13)

**Analog:** itself — append `requestId={} outcome={}` to the existing `key=value` SLF4J pattern (same arg-order discipline). Logger name stays `integration.invoke.audit`:

```18:30:api-connector-api/src/main/java/com/suntek/apiconnector/api/invoke/InvokeAuditLogger.java
    public void log(InvokeAuditEvent event) {
        AUDIT.info(
                "code3rd={} endpointId={} method={} path={} success={} vendorHttpStatus={} latencyMs={} client={} context={}",
                event.code3rd(), event.endpointId(), event.method(), event.path(),
                event.success(), event.vendorHttpStatus(), event.latencyMillis(),
                event.clientAddress(), event.context());
    }
```

---

### `api/config/IntegrationInvokeProperties.java` (config) — MODIFY (D-22)

**Analog:** itself — the `auditEnabled` boolean (l.20, getter/setter l.46-52) is the exact template for `mappingEnabled` (default `true`):

```46:52:api-connector-api/src/main/java/com/suntek/apiconnector/api/config/IntegrationInvokeProperties.java
    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }
```

---

### `api/RuntimeApiExceptionHandler.java` (middleware) — REUSE, NO EDIT (D-20)

**Analog:** itself — `handleMapping` (l.61-73) and `handleAuth` (l.48-59) already translate `MappingException`/`AuthException` to `{code,message,details}` via `ApiErrorResponse`. Pipeline failures throw these existing typed exceptions; no new handler path. Verify the `MappingErrorCode`/`AuthErrorCode` families cover the new `MAPPING_*`/`AUTH_*` codes (D-19) — reuse Phase 2 codes where applicable (Claude's discretion).

---

### `app/src/main/resources/application.yml` (config) — MODIFY (D-22)

**Analog:** existing `integration.invoke.*` block. Add `mapping-enabled: true` beside `audit-enabled` / `platform-http-status`. (Optional nice-to-have, not blocking: `%X{requestId}` in `logging.pattern.console` — Open Q4 RESOLVED.)

---

### Test patterns

**`DefaultIntegrationOrchestratorTest.java` (unit) — MODIFY.** Analog: itself. The anonymous-`HttpTransport`-fake + `AuthEngine(List.of(new NoneAuthProvider()))` + `ConnectorRegistry` setup (l.44-79) is the exact harness. For ordering/passthrough tests, pass spy/fake `MappingEngine` + `TransformPipeline` and assert `mapRequest` never called on passthrough (D-04); assert request-side body finalized before auth.

```44:62:api-connector-engine/src/test/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestratorTest.java
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpTransportResponse exchange(HttpTransportRequest request) {
                return new HttpTransportResponse(200, "{...}", Map.of());
            }
            @Override
            public void exchangeStream(HttpTransportRequest request, HttpStreamHandler handler) {
                throw new UnsupportedOperationException("not used in test");
            }
        };
        AuthEngine authEngine = new AuthEngine(List.of(new NoneAuthProvider()));
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry, authEngine, transport, new ResponseEvaluator());
```

**`InvokeIntegrationTest.java` (integration, WireMock) — MODIFY.** Analog: itself — the HMAC-header `verify` (l.187-214) is the EXACT template for `hmacSignsMappedRequestBody`: register a connector with request mapping + `aksk_hmac` auth, POST `{"a":1}`, then `verify(postRequestedFor(...).withRequestBody(matchingJsonPath("$.b")).withHeader("X-Auth-Signature", matching("[0-9a-f]{64}")))` — proves body mapped BEFORE signature. Reuse the `rebindBaseUrl` helper (l.265-277) + `@DynamicPropertySource` (l.64-69).

```200:214:api-connector-app/src/test/java/com/suntek/apiconnector/app/InvokeIntegrationTest.java
        assertEquals(200, response.statusCode());
        wireMock.verify(getRequestedFor(urlPathEqualTo("/get"))
                .withHeader("X-Auth-Key", equalTo("ak-test"))
                .withHeader("X-Auth-Signature", matching("[0-9a-f]{64}"))
                .withHeader("X-Auth-Algorithm", equalTo("aksk_hmac_sha256")));
```

**`LegacyCompatIntegrationTest.java` (parity) — MODIFY.** Analog: itself — `legacyIdpsGetProxiesVendorJson` (l.74-85) + `rebindIdpsToWireMock` (l.52-72) is the template for `legacyAndUnifiedCoreResultParity`: hit the SAME endpoint via legacy URL (`/idps/...`) and unified API, assert equal `success` + `vendorCode` + parsed `data` (NOT byte-identity of envelope — D-08). Note `integration.legacy.enabled=true` in `@DynamicPropertySource` (l.49).

**`IntegrationInvokeServiceTest.java` (unit) — NEW.** No exact analog (no existing `*ServiceTest` for this service). Compose: the orchestrator-test fake style + `MockHttpServletRequest` bound via `RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req))` to drive `resolveCorrelationId()` (X-Request-Id → X-Trace-Id → UUID) and assert MDC put/clear. Package-private `class IntegrationInvokeServiceTest` (CLAUDE.md test convention).

**`InvokeAuditLoggerTest.java` (unit) — NEW.** No exact analog. Use a logback `ListAppender<ILoggingEvent>` attached to logger `integration.invoke.audit`, call `log(event)` with a populated `requestId`/`outcome`, assert the formatted message contains `requestId=` and `outcome=`.

---

## Shared Patterns

### Constructor injection (no `@Autowired` field injection)
**Source:** `DefaultIntegrationOrchestrator.java` l.49-58, `IntegrationInvokeService.java` l.41-52, `ConnectorPublishListener.java` l.50-57
**Apply to:** orchestrator (new collaborators), publish listener (cache), service (MDC needs no new bean).
**Rule:** all collaborators are `private final`, set in the constructor; Spring beans wired explicitly in `IntegrationEngineConfiguration` (NOT component-scanned for engine classes). Provide a back-compat constructor when adding params so existing unit tests (which `new` the class directly) keep compiling.

### SLF4J logging (NOT Lombok `@Slf4j`)
**Source:** `InvokeAuditLogger.java` l.6-16
```6:16:api-connector-api/src/main/java/com/suntek/apiconnector/api/invoke/InvokeAuditLogger.java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InvokeAuditLogger {
    private static final Logger AUDIT = LoggerFactory.getLogger("integration.invoke.audit");
```
**Apply to:** every new/modified class that logs — DEBUG stage timings (D-23) use `private static final Logger LOG = LoggerFactory.getLogger(DefaultIntegrationOrchestrator.class);`. **Never log bodies/credentials** (D-23, Security V7) — stage name + millis only.

### MDC correlation id (NEW capability, first MDC use in codebase)
**Source:** none existing — derive from `clientAddress()` servlet-access idiom (`IntegrationInvokeService.java` l.230-242).
**Apply to:** `IntegrationInvokeService.doInvoke` + `doStream`. `MDC.put("requestId", id)` in `try`, `MDC.remove("requestId")` in `finally` (Pitfall 3, virtual-thread safety). Sanitize inbound header before put.

### Resolver / passthrough-gate reuse (don't hand-roll)
**Source:** `MappingConfigResolver.java` l.23-51, `ErrorMappingTrigger.java` l.17-22
**Apply to:** orchestrator request + response sides. Gate ALL mapping behind `mappingEnabled && MappingConfigResolver.hasAnyMapping(resolved)` (D-04 zero-overhead passthrough, Pitfall 6). Use `ErrorMappingTrigger.shouldMapError` verbatim for response routing (D-03).

### Typed-exception error translation (don't add a handler)
**Source:** `RuntimeApiExceptionHandler.java` l.48-73 (`AuthException`/`MappingException` → `ApiErrorResponse{code,message,details}`)
**Apply to:** all pipeline-failure paths — throw existing `MappingException`/`AuthException`; the advice already maps them (D-19/D-20).

### Mandatory file conventions (every Java file)
**Source:** PCI header on every file (e.g. `DefaultIntegrationOrchestrator.java` l.1-5), `@author Gensokyo`/`@version`/`@since` + Javadoc with `@param`/`@return` on public types (l.27-48).
**Apply to:** the one NEW source class (`ResolvedMappingCache`) and any new public method. Tests are package-private `class FooTest` (no PCI header required on test classes per existing `DefaultIntegrationOrchestratorTest` l.1).

### WireMock + RANDOM_PORT integration harness
**Source:** `InvokeIntegrationTest.java` l.45-91, `LegacyCompatIntegrationTest.java` l.28-72
**Apply to:** all new integration test methods — `@RegisterExtension static WireMockExtension`, `@LocalServerPort`, `@Autowired ConnectorRegistry`, `@DynamicPropertySource` (memory persistence, security off), `rebindBaseUrl(...)`/`registry.save(..., PUBLISHED)` to point a catalog connector at WireMock.

---

## No Analog Found

None. Every target is an edit to an existing file (its own best analog) or a new class/test whose idiom is directly copied from a named sibling. The two NEW classes are role-matched:

| File | Role | Data Flow | Nearest idiom |
|------|------|-----------|---------------|
| `ResolvedMappingCache.java` | utility/cache | transform | `ConnectorRegistry` `ConcurrentHashMap` + `TokenCache.evictForConnector` |
| `IntegrationInvokeServiceTest.java` | test/unit | request-response | `DefaultIntegrationOrchestratorTest` fakes + `MockHttpServletRequest`/`RequestContextHolder` |
| `InvokeAuditLoggerTest.java` | test/unit | — | logback `ListAppender` over `InvokeAuditLogger.log` |

---

## Metadata

**Analog search scope:** `api-connector-engine` (engine, config, transport, test), `api-connector-mapping` (spi, transform, ResolvedMapping, ErrorMappingTrigger), `api-connector-api` (service, invoke, config, legacy, advice), `api-connector-domain` (MappingContext, MappingDirection, EndpointMeta), `api-connector-app` (integration tests, application.yml).
**Files scanned:** 18 source/test files read in full (≤ 300 lines each, single pass).
**Pattern extraction date:** 2026-06-18
