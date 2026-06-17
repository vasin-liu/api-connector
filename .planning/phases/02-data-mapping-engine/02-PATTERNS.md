# Phase 2: Data Mapping Engine — Pattern Mapping

**Mapped:** 2026-06-17  
**Sources:** `02-CONTEXT.md`, `02-RESEARCH.md`, Phase 1 codebase analogs  
**Purpose:** Guide planners/implementers with file roles, closest analogs, and concrete excerpts.

---

## Summary

Phase 2 **adds** a new `api-connector-mapping` module and **extends** spec models, publish hooks, and API error handling — mirroring Phase 1 auth symmetry. The brownfield stack already has `ConnectorSpec.transform[]` (placeholder), Jayway JsonPath in `ResponseEvaluator`, and `ScriptCompileService` for Groovy. Gaps to close:

| Gap | Current state | Phase 2 target |
|-----|---------------|----------------|
| JSON field mapping | None | `mapping.request/response/error` blocks + `DeclarativeRuleExecutor` |
| Groovy mapping | None | `GroovyMappingScriptProvider` + per-direction scripts |
| Endpoint mapping override | `EndpointSpec` has no `mappingOverride` | Full per-direction block override (mirror `authOverride`) |
| Mapping errors | N/A | `MappingException` + `MappingErrorCode` + API handler |
| Publish hooks | Auth scripts only | Compile mapping scripts + validate declarative rules |
| Transform pipeline | `transform[]` parsed, never executed | `TransformStep` SPI + SM4 implementation |
| Passthrough | N/A | `MappingConfigResolver.hasAnyMapping()` — orchestrator short-circuit (Phase 3) |
| JDBC persistence | `SPEC_JSON` blob round-trips `ConnectorSpec` | Add `mapping` field to parser serialize path |

**Pipeline order (Phase 3 wiring):** `mapRequest → transform[] → auth` on request; reverse on response. Phase 2 builds engines; orchestrator integration deferred.

---

## File Inventory

### NEW module: `api-connector-mapping`

| File | Role | Closest analog |
|------|------|----------------|
| `api-connector-mapping/pom.xml` | Maven module (domain, spec, scripting, json-path, BC) | `api-connector-auth/pom.xml` |
| `.../mapping/spi/MappingScript.java` | Groovy script entry contract | `AuthScript.java` |
| `.../mapping/spi/MappingEngine.java` | Request/response/error mapping facade | `AuthEngine.java` (dispatch only) |
| `.../mapping/spi/TransformStep.java` | Body crypto/envelope SPI | `AuthProvider.java` (type + apply) |
| `.../mapping/DeclarativeRuleExecutor.java` | Sequential op dispatch over Jayway | *No analog — greenfield* |
| `.../mapping/MappingEngineImpl.java` | Rules OR script per direction | `GroovyAuthScriptProvider` + rule layer |
| `.../mapping/GroovyMappingScriptProvider.java` | Compile-once script eval adapter | `GroovyAuthScriptProvider.java` |
| `.../mapping/CoerceHelper.java` | Strict type coercion | *No analog — greenfield* |
| `.../mapping/TransformPipeline.java` | Ordered `transform[]` execution | `AuthEngine` pipeline merge |
| `.../mapping/TransformStepRegistry.java` | Spring `Map<String, TransformStep>` | `AuthEngine.providersByType` |
| `.../mapping/transform/Sm4EncryptTransformStep.java` | SM4/ECB/PKCS5Padding encrypt | `AkskHmacSha256V1AuthProvider` (BC usage) |
| `.../mapping/transform/Sm4DecryptTransformStep.java` | Response decrypt | `Sm4EncryptTransformStep` |
| `.../mapping/transform/StubTransformStep.java` | `business_envelope` etc. fail/stub | *No analog* |
| `.../mapping/exception/MappingException.java` | Typed mapping failure | `AuthException.java` |
| `.../mapping/exception/MappingErrorCode.java` | Error code enum | `AuthErrorCode.java` |
| `.../mapping/exception/MappingExceptions.java` | Factory helpers | `AuthExceptions.java` |
| `.../spec/MappingSpecValidator.java` | Publish-time JSONPath + op validation | `ConnectorSpecParser.validateAuthOverride` |
| `.../test/DeclarativeRuleExecutorTest.java` | MAP-01 rename/coerce | `GroovyAuthScriptProviderTest.java` |
| `.../test/ArrayMapRuleTest.java` | MAP-02 nested arrays | `ResponseEvaluatorTest.java` |
| `.../test/GroovyMappingScriptProviderTest.java` | MAP-03 script + `authSnapshot` | `GroovyAuthScriptProviderTest.java` |
| `.../test/ErrorMappingEngineTest.java` | MAP-04 legacy shape | `LegacySuntekResult` fixture |
| `.../test/MappingPublishIntegrationTest.java` | MAP-05 JDBC reload | `ConnectorPublishListenerTest.java` |
| `.../test/resources/mapping/demo-rename.yaml` | Fixture spec | Catalog YAML pattern |

### NEW files in existing modules

| File | Role | Closest analog |
|------|------|----------------|
| `api-connector-domain/.../MappingContext.java` | Script + engine input record | `AuthContext.java` |
| `api-connector-domain/.../MappingDirection.java` | REQUEST / RESPONSE / ERROR enum | *No analog* |
| `api-connector-domain/.../EndpointMeta.java` | id, method, path for bindings | `EndpointSpec` subset |
| `api-connector-spec/.../model/MappingSpec.java` | Connector-level mapping model | `ResponseSpec.java` (typed block) |
| `api-connector-spec/.../model/DirectionMappingSpec.java` | Per-direction rules OR script | *No analog* |
| `api-connector-spec/.../model/MappingRule.java` | Single declarative rule record | *No analog* |
| `api-connector-engine/.../MappingConfigResolver.java` | Connector default + endpoint override | `AuthConfigResolver.java` |
| `api-connector-engine/.../ResolvedMapping.java` | Resolved per-direction config | `Map<String,Object>` from auth resolver |
| `api-connector-engine/.../MappingConfigResolverTest.java` | Override + `hasAnyMapping` | `AuthConfigResolverTest.java` |

### MODIFY files

| File | Role | Change |
|------|------|--------|
| `pom.xml` | Root module list | Add `api-connector-mapping` |
| `api-connector-dependencies/pom.xml` | BOM | Pin `api-connector-mapping` artifact |
| `api-connector-spec/.../ConnectorSpec.java` | Spec model | Add `mapping` field (`MappingSpec`) |
| `api-connector-spec/.../EndpointSpec.java` | Endpoint model | Add `mappingOverride` (mirror `authOverride`) |
| `api-connector-spec/.../ConnectorSpecParser.java` | Parse/serialize | `mapping`, `mappingOverride`; mutual exclusion validation |
| `api-connector-engine/.../ConnectorPublishListener.java` | Publish hook | Compile mapping scripts + `MappingSpecValidator` |
| `api-connector-engine/.../config/IntegrationEngineConfiguration.java` | Spring wiring | Register `MappingEngine`, `TransformPipeline`, `TransformStep` beans |
| `api-connector-engine/pom.xml` | Engine deps | Add `api-connector-mapping` |
| `api-connector-api/.../RuntimeApiExceptionHandler.java` | Exception mapping | `@ExceptionHandler(MappingException.class)` |
| `api-connector-engine/.../ConnectorPublishListenerTest.java` | Publish tests | Mapping script compile + invalid spec rejection |
| `api-connector-persistence/` | JDBC | No schema change — parser round-trip only |
| `api-connector-app/.../InvokeIntegrationTest.java` | Integration | Mapping publish + passthrough (Phase 3 orchestrator) |

### UNCHANGED (reused as-is)

| File | Role |
|------|------|
| `api-connector-scripting/.../ScriptCompileService.java` | Shared Groovy compile cache |
| `api-connector-engine/.../ConnectorRegistry.java` | Already calls `publishListener.onPublish()` on `save()` |
| `api-connector-engine/.../ResponseEvaluator.java` | `successWhen` gate for error mapping trigger |
| `api-connector-domain/.../AuthContextSnapshot.java` | Groovy binding `ctx.authSnapshot()` |
| `api-connector-api/.../legacy/LegacySuntekResult.java` | MAP-04 target JSON contract |
| `api-connector-persistence/.../ConnectorConfigSyncService.java` | `reloadFromStore()` → `registry.save()` |

---

## Pattern 1: MappingConfigResolver (mirror AuthConfigResolver)

**Role:** Resolve connector-level `mapping` vs endpoint `mappingOverride` (endpoint wins per direction). Expose `hasAnyMapping()` for orchestrator passthrough short-circuit (D-23, D-26).

**Closest analog:** `AuthConfigResolver` — static resolver, endpoint override first.

```14:31:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/AuthConfigResolver.java
public final class AuthConfigResolver {

    private AuthConfigResolver() {
    }

    public static Map<String, Object> resolve(ConnectorSpec spec, EndpointSpec endpoint) {
        if (endpoint != null && endpoint.authOverride() != null) {
            return endpoint.authOverride();
        }
        return spec.auth();
    }
}
```

**Test pattern for override precedence:**

```16:36:api-connector-engine/src/test/java/com/suntek/apiconnector/engine/AuthConfigResolverTest.java
    @Test
    void endpointAuthOverrideReplacesConnectorAuth() {
        Map<String, Object> connectorAuth = Map.of("type", "none");
        Map<String, Object> override = Map.of("type", "groovy_auth_script", "script", "return AuthOutcome.empty()");
        ConnectorSpec spec = new ConnectorSpec(/* ... */, connectorAuth,
                List.of(new EndpointSpec("ep1", "GET", "/ep1", null, true, null, override)), /* ... */);
        EndpointSpec endpoint = spec.endpoints().getFirst();

        Map<String, Object> resolved = AuthConfigResolver.resolve(spec, endpoint);

        assertSame(override, resolved);
    }
```

**Target shape (new file):**

```java
public final class MappingConfigResolver {
    public static ResolvedMapping resolve(ConnectorSpec spec, EndpointSpec endpoint) {
        // Per direction: endpoint.mappingOverride().request() ?? spec.mapping().request()
        // Mutual exclusion already validated at parse/publish time
    }
    public static boolean hasAnyMapping(ResolvedMapping m) {
        return m.hasRequest() || m.hasResponse() || m.hasError();
    }
}
```

**Difference from auth:** Auth override is Groovy-only single block; mapping override is **full per-direction block** (rules OR script per `request`/`response`/`error`).

---

## Pattern 2: ConnectorSpec / EndpointSpec — mapping + mappingOverride

**Role:** Declarative mapping at connector level; per-endpoint override wins (D-01).

**Closest analog:** `auth` + `authOverride` on existing spec models.

**ConnectorSpec — existing `transform[]` placeholder (extend, do not replace):**

```18:85:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/ConnectorSpec.java
public final class ConnectorSpec {
    private final Map<String, Object> auth;
    private final List<EndpointSpec> endpoints;
    private final List<Map<String, Object>> transform;
    // ...
    public List<Map<String, Object>> transform() {
        return transform;
    }
}
```

**EndpointSpec — `authOverride` pattern to mirror for `mappingOverride`:**

```17:85:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/EndpointSpec.java
public final class EndpointSpec {
    private final Map<String, Object> authOverride;
    // ...
    public Map<String, Object> authOverride() {
        return authOverride;
    }
}
```

**Parser — authOverride validation + round-trip (mirror for mapping):**

```67:89:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/ConnectorSpecParser.java
    private static EndpointSpec toEndpoint(Map<String, Object> m) {
        Map<String, Object> authOverride = map(m.get("authOverride"));
        if (!authOverride.isEmpty()) {
            validateAuthOverride(authOverride);
        }
        EndpointSpec endpoint = new EndpointSpec(
                /* ... */,
                authOverride.isEmpty() ? null : authOverride);
        return EndpointDocumentation.enrich(endpoint);
    }

    private static void validateAuthOverride(Map<String, Object> authOverride) {
        Object type = authOverride.get("type");
        if (!"groovy_auth_script".equals(String.valueOf(type))) {
            throw new IllegalArgumentException(
                    "Endpoint authOverride is Groovy-only: type must be groovy_auth_script, got: " + type);
        }
    }
```

**Serialize round-trip (Pitfall 8 — must add `mapping` symmetrically):**

```117:150:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/ConnectorSpecParser.java
    public static Map<String, Object> toConnectorMap(ConnectorSpec spec) {
        Map<String, Object> connector = new LinkedHashMap<>();
        connector.put("code3rd", spec.code3rd());
        // ...
        connector.put("transform", spec.transform());
        return connector;
    }

    private static Map<String, Object> endpointToMap(EndpointSpec endpoint) {
        Map<String, Object> map = new LinkedHashMap<>();
        // ...
        if (endpoint.authOverride() != null && !endpoint.authOverride().isEmpty()) {
            map.put("authOverride", endpoint.authOverride());
        }
        return map;
    }
```

**Target YAML shape:**

```yaml
mapping:
  request:
    rules:
      - op: rename
        source: $.clientId
        target: $.app_id
  response:
    script: |
      def m = ctx.bodyAsMap()
      [code: '200', success: true, data: m.result]
  error:
    rules:
      - op: set
        target: $.success
        value: false
endpoints:
  - id: echo
    mappingOverride:
      request:
        script: |
          ctx.bodyAsMap()
transform:
  - type: sm4_encrypt
    direction: request
    keyRef: appSecret
```

---

## Pattern 3: DeclarativeRuleExecutor — Jayway JsonPath + custom ops

**Role:** Sequential rule execution (D-07); lenient missing sources (D-06); strict coerce failures → `MappingException`.

**Closest analog:** `ResponseEvaluator` — Jayway read + `normalizePath()`.

```21:85:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ResponseEvaluator.java
    public ResponseEvaluation evaluate(ResponseSpec spec, String rawBody) {
        // ...
        Object document = JsonPath.parse(rawBody).json();
        boolean success = isSuccessOnDocument(spec, document);
        // ...
    }

    private static String normalizePath(String path) {
        if (path.startsWith("$")) {
            return path;
        }
        return "$." + path;
    }
```

**Publish-time path validation — use `JsonPath.compile(path)` (same library):**

```java
// MappingSpecValidator (new) — analog: validateAuthOverride throws on bad config
try {
    JsonPath.compile(normalizePath(rule.source()));
} catch (InvalidPathException ex) {
    throw MappingExceptions.specInvalid("mapping.request.rules[" + i + "].source", path, ex);
}
```

**Write path — Jayway `DocumentContext.set()` (not in ResponseEvaluator; RESEARCH Pattern 1):**

```java
DocumentContext ctx = JsonPath.parse(inputJson);
Object value = JsonPath.read(document, normalizePath(rule.source())); // read
ctx.set(normalizePath(rule.target()), coercedValue);                  // write
return ctx.jsonString();
```

**Op dispatch (hand-roll, ~200–400 LOC):**

| Op | Behavior |
|----|----------|
| `rename` | read source → set target → optional delete source |
| `set` | write constant to target (D-08, no source) |
| `coerce` | `CoerceHelper` strict; failure → `MAPPING_COERCE_FAILED` |
| `nest` | create parent objects before leaf `set` |
| `array_map` | iterate array at source; apply nested rules per element (D-05) |

---

## Pattern 4: MappingScript SPI + GroovyMappingScriptProvider

**Role:** Per-direction Groovy scripts (`requestScript` / `responseScript` / `errorScript`); compile-once; rich `MappingContext` bindings (D-09–D-13).

**Closest analog:** `AuthScript` + `GroovyAuthScriptProvider`.

**SPI contract:**

```12:16:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/spi/AuthScript.java
@FunctionalInterface
public interface AuthScript {

    AuthOutcome apply(AuthContext ctx);
}
```

**Target MappingScript:**

```java
@FunctionalInterface
public interface MappingScript {
    Object apply(MappingContext ctx);  // Map or List — Jackson-serializable
}
```

**Provider — mirror bindings and return-type check:**

```21:61:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/GroovyAuthScriptProvider.java
public class GroovyAuthScriptProvider implements AuthProvider {

  private static final String TYPE = "groovy_auth_script";
  private final ScriptCompileService scriptCompileService;

  @Override
  public AuthOutcome apply(AuthContext context) {
      String source = stringConfig(context.authConfig(), "script", null);
      // ...
      CompiledScript compiled = scriptCompileService.compile(source, context.code3rd());
      SimpleBindings bindings = new SimpleBindings();
      bindings.put("ctx", context);
      Object result = compiled.eval(bindings);
      if (!(result instanceof AuthOutcome outcome)) {
          throw new ClassCastException(/* ... */);
      }
      return outcome;
  }
}
```

**Mapping equivalent:**

```java
public class GroovyMappingScriptProvider {
    public Object apply(MappingContext context, String scriptSource, String compileLabel) {
        CompiledScript compiled = scriptCompileService.compile(scriptSource, compileLabel);
        SimpleBindings bindings = new SimpleBindings();
        bindings.put("ctx", context);
        Object result = compiled.eval(bindings);
        if (result instanceof MappingScript ms) {
            return ms.apply(context);
        }
        if (result instanceof Map || result instanceof List) {
            return result;
        }
        throw MappingExceptions.scriptRuntimeError(/* ... */);
    }
}
```

**MappingContext bindings (D-11) — analog `AuthContext` fields + `AuthContextSnapshot`:**

```22:66:api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/AuthContextSnapshot.java
public record AuthContextSnapshot(
        String code3rd,
        List<String> profileTypes,
        Map<String, String> credentialRefsUsed,
        Map<String, Object> ext) {
    // ...
}
```

**Target MappingContext record:**

```java
public record MappingContext(
        String code3rd,
        MappingDirection direction,
        String rawBody,
        AuthContextSnapshot authSnapshot,
        EndpointMeta endpoint) {

    public Map<String, Object> bodyAsMap() { /* Jackson parse */ }
}
```

**Compile labels:** `code3rd:mapping:request`, `code3rd:mapping:response`, `code3rd:mapping:error`, `code3rd:endpoint:{id}:mapping:request`.

**Mutual exclusion (D-10):** Parser/validator rejects direction block containing both `rules` and `script`.

---

## Pattern 5: MappingException (mirror AuthException)

**Role:** Structured platform errors with code + ops-friendly details (D-14).

**Closest analog:** `AuthException` + `AuthExceptions` + `AuthErrorCode`.

```11:33:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/exception/AuthException.java
public final class AuthException extends RuntimeException {

    private final AuthErrorCode code;
    private final Map<String, Object> details;

    public AuthException(String message, AuthErrorCode code, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }
    // code(), details()
}
```

```9:14:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/exception/AuthErrorCode.java
public enum AuthErrorCode {
    AUTH_PROFILE_MISSING,
    UPSTREAM_AUTH_FAILED,
    AUTH_SCRIPT_COMPILE_ERROR,
    AUTH_SCRIPT_RUNTIME_ERROR
}
```

**Target MappingErrorCode:**

```java
public enum MappingErrorCode {
    MAPPING_SPEC_INVALID,      // publish: bad JSONPath, unknown op, rules+script
    MAPPING_SCRIPT_COMPILE_ERROR,
    MAPPING_SCRIPT_RUNTIME_ERROR,
    MAPPING_COERCE_FAILED,
    TRANSFORM_UNSUPPORTED
}
```

**Factory pattern:**

```37:44:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/exception/AuthExceptions.java
    public static AuthException scriptCompileError(ScriptCompileException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("label", ex.label());
        if (ex.line() != null) {
            details.put("line", ex.line());
        }
        return new AuthException(ex.getMessage(), AuthErrorCode.AUTH_SCRIPT_COMPILE_ERROR, details);
    }
```

**API handler extension:**

```47:58:api-connector-api/src/main/java/com/suntek/apiconnector/api/RuntimeApiExceptionHandler.java
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(AuthException ex) {
        HttpStatus status = switch (ex.code()) {
            case AUTH_PROFILE_MISSING, AUTH_SCRIPT_COMPILE_ERROR -> HttpStatus.BAD_REQUEST;
            case UPSTREAM_AUTH_FAILED, AUTH_SCRIPT_RUNTIME_ERROR -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(ApiErrorResponse.builder()
                .code(ex.code().name())
                .message(ex.getMessage())
                .details(ex.details())
                .build());
    }
```

**Add parallel handler:**

```java
@ExceptionHandler(MappingException.class)
public ResponseEntity<ApiErrorResponse> handleMapping(MappingException ex) {
    HttpStatus status = switch (ex.code()) {
        case MAPPING_SPEC_INVALID, MAPPING_SCRIPT_COMPILE_ERROR -> HttpStatus.BAD_REQUEST;
        case MAPPING_SCRIPT_RUNTIME_ERROR, MAPPING_COERCE_FAILED -> HttpStatus.BAD_GATEWAY;
        case TRANSFORM_UNSUPPORTED -> HttpStatus.BAD_REQUEST;
    };
    // same builder pattern
}
```

---

## Pattern 6: ConnectorPublishListener — mapping extension

**Role:** On `ConnectorRegistry.save()`, compile Groovy mapping scripts, validate declarative rules, reject invalid spec (D-29, D-30).

**Closest analog:** Existing auth script scan + contract validation.

```28:117:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
public final class ConnectorPublishListener {

    private static final String GROOVY_AUTH_SCRIPT = "groovy_auth_script";
    private final ScriptCompileService scriptCompileService;

    public void onPublish(ConnectorSpec spec) {
        try {
            onPublishInternal(spec);
        } catch (ScriptCompileException ex) {
            throw AuthExceptions.scriptCompileError(ex);
        }
    }

    private void onPublishInternal(ConnectorSpec spec) {
        tokenCache.evictForConnector(spec.code3rd());
        Set<String> compiledSources = new HashSet<>();
        scanAuthConfig(spec.auth(), spec.code3rd(), "auth", compiledSources);
        // endpoint authOverride scan ...
    }

    private void compileGroovyIfPresent(/* ... */) {
        if (!GROOVY_AUTH_SCRIPT.equals(String.valueOf(config.get("type")))) {
            return;
        }
        CompiledScript compiled = scriptCompileService.compile(source, compileLabel);
        validateAuthScriptContract(compiled, compileLabel);
    }
}
```

**Contract validation stub eval:**

```119:147:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorPublishListener.java
    private void validateAuthScriptContract(CompiledScript compiled, String label) {
        SimpleBindings bindings = new SimpleBindings();
        AuthContext stub = stubContext();
        bindings.put("ctx", stub);
        Object result = compiled.eval(bindings);
        if (result instanceof AuthOutcome) {
            return;
        }
        if (result instanceof AuthScript authScript) {
            AuthOutcome outcome = authScript.apply(stub);
            // ...
        }
        throw contractError(label, /* ... */);
    }
```

**Mapping extension points (add to `onPublishInternal`):**

1. `MappingSpecValidator.validate(spec)` — JSONPath compile, known ops, rules XOR script, transform type registry
2. `scanMappingScripts(spec.mapping(), ...)` — connector-level per-direction scripts
3. `scanEndpointMappingOverride(endpoint.mappingOverride(), ...)` — endpoint scripts
4. `validateMappingScriptContract(compiled, label)` — stub `MappingContext` with sample JSON body

**Registry hook (unchanged — publish already fires):**

```57:63:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorRegistry.java
    public void save(ConnectorSpec spec, Map<String, String> credentialPatch, ConnectorSpecStatus status) {
        String code3rd = spec.code3rd();
        specs.put(code3rd, spec);
        mergeCredentials(code3rd, credentialPatch);
        statuses.put(code3rd, status);
        notifyPublish(spec);
    }
```

**Test pattern for publish compile:**

```26:51:api-connector-engine/src/test/java/com/suntek/apiconnector/engine/ConnectorPublishListenerTest.java
    @Test
    void onPublishCompilesGroovyScriptsAndEvictsTokens() {
        ScriptCompileService compileService = new ScriptCompileService(scriptCache);
        ConnectorPublishListener listener = new ConnectorPublishListener(compileService, tokenCache);
        String script = """
                import com.suntek.apiconnector.domain.model.AuthOutcome
                new AuthOutcome([Authorization: 'Bearer publish-token'], [:], null)
                """;
        listener.onPublish(groovyConnectorSpec("GROOVY_DEMO", script, null));
        assertEquals(1, scriptCache.size());
    }
```

---

## Pattern 7: ScriptCompileService — shared compile cache

**Role:** Compile Groovy once on publish; SHA-256 content-hash cache; shared by auth and mapping (D-29, Pitfall 3).

**No changes expected** — mapping uses same service with distinct compile labels.

```18:54:api-connector-scripting/src/main/java/com/suntek/apiconnector/scripting/ScriptCompileService.java
public final class ScriptCompileService {

    private final CompiledScriptCache cache;

    public CompiledScript compile(String source, String label) {
        String hash = sha256Hex(source);
        CompiledScript cached = cache.get(hash);
        if (cached != null) {
            return cached;
        }
        CompiledScript compiled = ((Compilable) scriptEngine).compile(source);
        cache.put(hash, compiled);
        return compiled;
    }
}
```

**Integration test anchor (AUTH-03 pattern):** `InvokeIntegrationTest.groovyAuthScriptCompileOnceAcrossTwoInvokes` — replicate for mapping scripts.

---

## Pattern 8: TransformStep SPI (ConnectorSpec.transform[])

**Role:** Crypto/envelope steps separate from JSON mapping (D-20, D-22, ADR-002). `transform[]` already on `ConnectorSpec`; Phase 2 implements execution.

**Closest analog:** `AuthProvider` — type id + `apply(context)`.

```java
public interface TransformStep {
    String type();  // e.g. "sm4_encrypt"
    String apply(TransformContext ctx);  // body in → body out
}
```

**Existing spec field (placeholder):**

```28:38:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/ConnectorSpec.java
    private final List<Map<String, Object>> transform;

    public ConnectorSpec(/* ... */, List<Map<String, Object>> transform) {
        // ...
        this.transform = transform;
    }
```

**Step YAML shape:**

```yaml
transform:
  - type: sm4_encrypt
    direction: request
    keyRef: appSecret
    mode: ECB
    outputEncoding: base64
  - type: business_envelope
    enabled: false
```

**Registry pattern — mirror `AuthEngine` provider map:**

```java
public class TransformStepRegistry {
    private final Map<String, TransformStep> stepsByType;
    public String applyRequest(String body, List<Map<String, Object>> steps, Map<String, String> credentials) {
        return steps.stream()
            .filter(s -> "request".equals(s.get("direction")))
            .reduce(body, (b, step) -> resolve(step).apply(new TransformContext(b, step, credentials)), ...);
    }
}
```

**SM4:** BouncyCastle `SM4/ECB/PKCS5Padding`; key via `keyRef` → `credentials.get(keyRef)` — never inline secrets (Pitfall 6).

**Stub:** `business_envelope` with `enabled: true` → `TRANSFORM_UNSUPPORTED` at publish.

---

## Pattern 9: Error mapping + ResponseEvaluator gate

**Role:** Trigger `mapping.error` on HTTP non-2xx OR business failure (`successWhen` not met) (D-15, D-19). Passthrough vendor body when no error mapping (D-18).

**Closest analog:** `ResponseEvaluator.isSuccess()` — existing JsonPath success gate.

```48:66:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ResponseEvaluator.java
    public boolean isSuccess(ResponseSpec spec, String rawBody) {
        return evaluate(spec, rawBody).success();
    }

    private static boolean isSuccessOnDocument(ResponseSpec spec, Object document) {
        if (spec.successWhen() == null || spec.successWhen().isBlank()) {
            return true;
        }
        String rule = spec.successWhen().trim();
        if (rule.contains("==")) {
            String[] parts = rule.split("==", 2);
            String path = normalizePath(parts[0].trim());
            Object value = JsonPath.read(document, path);
            return expected.equals(String.valueOf(value));
        }
        // ...
    }
```

**Legacy target contract:**

```16:22:api-connector-api/src/main/java/com/suntek/apiconnector/api/legacy/LegacySuntekResult.java
public class LegacySuntekResult {
    private String code;
    private String message;
    private Object data;
    private Boolean success;
}
```

**Trigger matrix:**

| Condition | Error mapping? |
|-----------|----------------|
| HTTP 4xx/5xx + `mapping.error` configured | Yes |
| HTTP 200 + `!responseEvaluator.isSuccess()` | Yes |
| HTTP 200 + success | Use `mapping.response` |
| Failure + no `mapping.error` | Passthrough `rawBody` (D-18) |

**Orchestrator pseudocode (Phase 3):**

```java
ResponseEvaluation evaluation = responseEvaluator.evaluate(spec.response(), httpResp.body());
boolean shouldMapError = httpResp.statusCode() >= 400 || !evaluation.success();
if (shouldMapError && resolvedMapping.hasError()) {
    body = mappingEngine.mapError(ctx, resolvedMapping, trigger);
} else if (!shouldMapError && MappingConfigResolver.hasAnyMapping(resolvedMapping)) {
    body = mappingEngine.mapResponse(ctx, resolvedMapping);
}
```

---

## Pattern 10: Passthrough fast path (MAP-07)

**Role:** True zero overhead when no mapping blocks — orchestrator must not call `MappingEngine` (D-23, D-26).

**Closest analog:** No existing analog — auth always runs (even `none` profile). Mapping is opt-in.

**NOT acceptable:**

```java
// Anti-pattern — still parses JSON
mappingEngine.mapRequest(body, emptyConfig);
```

**Required guard (Phase 3 orchestrator):**

```java
ResolvedMapping mapping = MappingConfigResolver.resolve(spec, endpointSpec);
String body = request.body();
if (MappingConfigResolver.hasAnyMapping(mapping)) {
    body = mappingEngine.mapRequest(buildContext(REQUEST, body, snapshot, endpoint), mapping);
}
// transform[] still runs if configured (D-25)
body = transformPipeline.applyRequest(body, spec.transform(), credentials);
```

**Phase 2 deliverable:** `hasAnyMapping()` API + unit test; orchestrator wiring in Phase 3.

---

## Pattern 11: Spring wiring (IntegrationEngineConfiguration)

**Role:** Register mapping beans alongside auth — hexagonal: engine wires, mapping module has no Spring Web dep.

**Closest analog:** Auth + scripting bean registration.

```47:154:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    @Bean
    public ConnectorPublishListener connectorPublishListener(
            ScriptCompileService scriptCompileService, TokenCache tokenCache) {
        return new ConnectorPublishListener(scriptCompileService, tokenCache);
    }

    @Bean
    public ScriptCompileService scriptCompileService() {
        return new ScriptCompileService();
    }

    @Bean
    public GroovyAuthScriptProvider groovyAuthScriptProvider(ScriptCompileService scriptCompileService) {
        return new GroovyAuthScriptProvider(scriptCompileService);
    }
```

**Add:**

```java
@Bean
public MappingEngine mappingEngine(GroovyMappingScriptProvider groovyProvider, DeclarativeRuleExecutor executor) {
    return new MappingEngineImpl(executor, groovyProvider);
}

@Bean
public TransformPipeline transformPipeline(List<TransformStep> steps) {
    return new TransformPipeline(new TransformStepRegistry(steps));
}

@Bean
public Sm4EncryptTransformStep sm4EncryptTransformStep() { return new Sm4EncryptTransformStep(); }
```

**Extend `ConnectorPublishListener` constructor** if `MappingSpecValidator` needs `TransformStepRegistry` for publish-time type lookup.

---

## Auth → Mapping analog table

| Auth (Phase 1) | Mapping (Phase 2) | File analog |
|----------------|-------------------|-------------|
| `AuthConfigResolver` | `MappingConfigResolver` | `api-connector-engine` |
| `GroovyAuthScriptProvider` | `GroovyMappingScriptProvider` | `api-connector-mapping` |
| `AuthScript` | `MappingScript` | SPI interface |
| `AuthContext` | `MappingContext` | `api-connector-domain` |
| `AuthException` | `MappingException` | exception package |
| `AuthErrorCode` | `MappingErrorCode` | enum |
| `AuthExceptions` | `MappingExceptions` | factory |
| `groovy_auth_script` | per-direction `script` in `mapping.*` | spec YAML |
| `authOverride` (Groovy-only) | `mappingOverride` (full block per direction) | `EndpointSpec` |
| `ConnectorPublishListener` scan | + mapping scan/validate | extend same class |
| `ScriptCompileService` | reuse unchanged | `api-connector-scripting` |
| `AuthContextSnapshot` | `ctx.authSnapshot()` binding | reuse unchanged |
| `RuntimeApiExceptionHandler` | + `MappingException` handler | `api-connector-api` |
| N/A | `DeclarativeRuleExecutor` | new |
| N/A | `TransformStep` SPI | new |
| `ConnectorSpec.auth` | `ConnectorSpec.mapping` | spec model |
| `ConnectorSpec.transform[]` | execute via `TransformPipeline` | existing field |

---

## Module dependency graph

```
api-connector-domain       ← MappingContext, MappingDirection, EndpointMeta
api-connector-spec         ← MappingSpec, parser extensions, MappingSpecValidator
api-connector-scripting    ← ScriptCompileService (unchanged)
api-connector-mapping      ← NEW: engine, rules, transform steps, Groovy adapter
api-connector-engine       ← MappingConfigResolver, ConnectorPublishListener extend
api-connector-persistence  ← no schema change (mapping in SPEC_JSON)
api-connector-api            ← MappingException handler
```

**Root pom** — add after `api-connector-auth`:

```xml
<module>api-connector-mapping</module>
```

**api-connector-mapping/pom.xml** — mirror auth deps:

```xml
<dependencies>
    <dependency><artifactId>api-connector-domain</artifactId></dependency>
    <dependency><artifactId>api-connector-spec</artifactId></dependency>
    <dependency><artifactId>api-connector-scripting</artifactId></dependency>
    <dependency><groupId>com.jayway.jsonpath</groupId><artifactId>json-path</artifactId></dependency>
    <dependency><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk18on</artifactId></dependency>
</dependencies>
```

---

## Anti-patterns to avoid

| Anti-pattern | Why | Use instead |
|--------------|-----|-------------|
| `MappingEngine.mapRequest()` when no mapping config | Allocates JsonNode every invoke (Pitfall 2) | `hasAnyMapping()` guard before bean call |
| `rules` + `script` in same direction | Ambiguous runtime (Pitfall 4) | Parse/publish reject |
| Per-request Groovy compile | P99 crush (Pitfall 3) | `ScriptCompileService` on publish |
| SM4 key inline in YAML | Secret leak (Pitfall 6) | `keyRef` → credentials |
| Field renames in `transform[]` | ADR-002 violation (Pitfall 9) | `mapping.*` for shape; `transform[]` for crypto |
| `toConnectorMap()` omits `mapping` | JDBC round-trip data loss (Pitfall 8) | Symmetric parse + serialize |
| Error mapping on HTTP 200 success | Wrong path (Pitfall 5) | Gate on `ResponseEvaluator.isSuccess()` |
| Auth before mapping (Phase 3) | HMAC signs wrong body (Pitfall 1) | `mapRequest → transform → auth` |

---

## Verification anchors

| Requirement | Pattern | Test location |
|-------------|---------|---------------|
| MAP-01 | Pattern 3 | `DeclarativeRuleExecutorTest` |
| MAP-02 | Pattern 3 `array_map` | `ArrayMapRuleTest` |
| MAP-03 | Pattern 4 | `GroovyMappingScriptProviderTest` |
| MAP-04 | Pattern 9 | `ErrorMappingEngineTest` vs `LegacySuntekResult` |
| MAP-05 | Pattern 6, 2 | `MappingPublishIntegrationTest`, parser round-trip |
| MAP-07 | Pattern 10 | `MappingConfigResolverTest.hasAnyMapping` |
| SC#6 | Pattern 6 | `ConnectorPublishListenerTest` + `MappingSpecValidatorTest` |
| D-20 | Pattern 8 | `Sm4EncryptTransformStep` unit test |

**CI commands:**

| Scope | Command |
|-------|---------|
| Mapping module | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test` |
| Spec parser | `.\mvnw-jdk21.ps1 -pl api-connector-spec -am test` |
| Publish listener | `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=ConnectorPublishListenerTest` |
| Full gate | `.\mvnw-jdk21.ps1 clean verify` |

---

## Walking skeleton (recommended first slice)

Per RESEARCH Wave 1 — unlock MAP-01 + MAP-05 + MAP-07 structure before Groovy/SM4:

1. Add `api-connector-mapping` module + root/BOM entries
2. `MappingSpec` model + `ConnectorSpecParser` parse/serialize `mapping`
3. `MappingSpecValidator` rejects invalid JSONPath on `ConnectorRegistry.save()`
4. `DeclarativeRuleExecutor` — one rename rule: `{"a":1}` → `{"b":1}`
5. `MappingConfigResolver.hasAnyMapping()` — false when spec has no `mapping` block
6. Fixture: `src/test/resources/mapping/demo-rename.yaml`

Defer to Phase 3: orchestrator wiring, HMAC-after-mapping (MAP-06).  
Defer to Phase 4: admin API dry-run validation.

---

*Phase: 02-data-mapping-engine*  
*Pattern mapping: 2026-06-17*

## PATTERN MAPPING COMPLETE
