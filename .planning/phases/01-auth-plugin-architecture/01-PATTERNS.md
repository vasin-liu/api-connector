# Phase 1: Auth Plugin Architecture — Pattern Mapping

**Mapped:** 2026-06-17  
**Sources:** `01-CONTEXT.md`, `01-RESEARCH.md`, codebase exploration  
**Purpose:** Guide planners/implementers with file roles, closest analogs, and concrete excerpts.

---

## Summary

Phase 1 **extends** the existing `AuthEngine` + `AuthProvider` SPI — it does not replace it. The brownfield stack already wires seven built-in profiles through `IntegrationEngineConfiguration` and applies auth in `DefaultIntegrationOrchestrator`. Gaps to close:

| Gap | Current state | Phase 1 target |
|-----|---------------|----------------|
| Groovy auth | None | New `api-connector-scripting` + `GroovyAuthScriptProvider` |
| Endpoint auth override | `EndpointSpec` has no `authOverride` | Groovy-only per-endpoint override |
| Auth carry-forward | `ext` passed as `Map.of()`; context discarded | `AuthContextSnapshot` on `InvocationResult` |
| OAuth token cache | Per-provider `ConcurrentHashMap` keyed by `code3rd` | Central `TokenCache` keyed by `code3rd\|profile\|scope` |
| Auth errors | `IllegalStateException` → generic `UPSTREAM_AUTH_FAILED` | `AuthException` + `details` on `ApiErrorResponse` |
| Publish hooks | `ConnectorRegistry.save()` stores spec only | Compile scripts + evict tokens on register/save |

---

## File Inventory

### NEW files

| File | Role | Closest analog |
|------|------|----------------|
| `api-connector-scripting/pom.xml` | Maven module definition | `api-connector-auth/pom.xml` |
| `api-connector-scripting/src/main/java/.../ScriptCompileService.java` | JSR-223 compile + content-hash cache | *No analog — greenfield* |
| `api-connector-scripting/src/main/java/.../CompiledScriptCache.java` | SHA-256 keyed `CompiledScript` store | OAuth `ConcurrentHashMap` in providers (cache shape only) |
| `api-connector-scripting/src/test/java/.../ScriptCompileServiceTest.java` | Compile-once assertion (AUTH-03) | `GaodeTrafficHmacAuthProviderTest.java` |
| `api-connector-auth/src/main/java/.../profile/GroovyAuthScriptProvider.java` | `AuthProvider` adapter for `groovy_auth_script` | `BearerStaticAuthProvider.java` |
| `api-connector-auth/src/main/java/.../spi/AuthScript.java` | Groovy script entry contract | `AuthProvider.java` (method signature) |
| `api-connector-auth/src/main/java/.../cache/TokenCache.java` | Central OAuth token store | `OAuth2ClientCredentialsAuthProvider` inner cache |
| `api-connector-auth/src/main/java/.../cache/TokenCacheKey.java` | Cache key record | *No analog — greenfield record* |
| `api-connector-auth/src/main/java/.../exception/AuthException.java` | Typed auth failure | `InvokeRateLimitException.java` |
| `api-connector-auth/src/main/java/.../exception/AuthErrorCode.java` | Error code enum | `RuntimeApiExceptionHandler` string codes |
| `api-connector-auth/src/main/java/.../context/AuthContextSnapshot.java` | Immutable post-auth view | `AuthContext.java` (read-only derivative) |
| `api-connector-auth/src/main/java/.../profile/OAuth2PasswordAuthProvider.java` | Wave 1 L2 (conditional) | `OAuth2ClientCredentialsAuthProvider.java` |
| `api-connector-auth/src/main/java/.../profile/BearerFromLoginAuthProvider.java` | Wave 1 L2 (conditional) | `BearerStaticAuthProvider.java` + OAuth fetch |
| `api-connector-engine/src/main/java/.../AuthConfigResolver.java` | Resolve connector vs endpoint auth | `EndpointResolver.java` |
| `api-connector-engine/src/main/java/.../ConnectorPublishListener.java` | Publish-time compile + token evict | `ConnectorBootstrapConfiguration.register()` |
| `docs/legacy-auth-inventory.md` | Wave 1 audit deliverable (D-03) | `docs/profile-registry.md` (reference only) |
| `api-connector-auth/src/test/resources/scripts/*.groovy` | Test Groovy auth scripts | *No analog* |
| `api-connector-auth/src/test/resources/connectors/groovy-demo.yaml` | Classpath spec with inline script | Catalog YAML pattern (none in repo yet) |

### MODIFY files

| File | Role | Change |
|------|------|--------|
| `pom.xml` | Root module list | Add `api-connector-scripting` module |
| `api-connector-dependencies/pom.xml` | BOM | Pin `groovy-jsr223` 4.0.32 (`org.apache.groovy`) |
| `api-connector-auth/pom.xml` | Auth module deps | Add `api-connector-scripting` |
| `api-connector-engine/pom.xml` | Engine deps | Add `api-connector-scripting` (publish listener) |
| `api-connector-spec/.../EndpointSpec.java` | Spec model | Add optional `authOverride` field |
| `api-connector-spec/.../ConnectorSpecParser.java` | YAML/JSON parser | Parse `authOverride`, `auth.script`; validate Groovy-only override |
| `api-connector-spec/.../CatalogConnectorScanner.java` | Catalog → spec | Optional `authOverride` on `@CatalogEndpoint` (if needed) |
| `api-connector-spec/.../CatalogAuth.java` | Catalog annotation | Optional `script` attribute for inline Groovy |
| `api-connector-auth/.../AuthEngine.java` | Auth dispatch | Throw `AuthException` instead of `IllegalStateException` |
| `api-connector-auth/.../OAuth2ClientCredentialsAuthProvider.java` | OAuth profile | Delegate to `TokenCache`; populate `ext` |
| `api-connector-auth/.../OAuth2TokenInQueryAuthProvider.java` | OAuth profile | Same refactor as client credentials |
| `api-connector-engine/.../DefaultIntegrationOrchestrator.java` | Invoke pipeline | `AuthConfigResolver`, snapshot carry-forward |
| `api-connector-engine/.../IntegrationEngineConfiguration.java` | Spring wiring | Register new `AuthProvider` beans, `TokenCache`, publish listener |
| `api-connector-engine/.../ConnectorRegistry.java` | Runtime registry | Hook publish listener on `save()`/`register()` |
| `api-connector-domain/.../InvocationResult.java` | Invoke result | Optional `authSnapshot()`, `authOutcome()` |
| `api-connector-api/.../ApiErrorResponse.java` | Error DTO | Add `details` map (D-18) |
| `api-connector-api/.../RuntimeApiExceptionHandler.java` | Exception mapping | Map `AuthException` → structured codes |
| `api-connector-app/.../ConnectorBootstrapConfiguration.java` | Startup load | Trigger script compile on catalog register |
| `api-connector-persistence/.../ConnectorConfigSyncService.java` | JDBC sync | Trigger compile + evict on `reloadFromStore()` |
| `api-connector-connectors/.../BuiltinConnectorCatalogs.java` | Catalog index | Register Wave 1 / demo Groovy connectors |
| `api-connector-engine/.../DefaultIntegrationOrchestratorTest.java` | Unit test | Assert snapshot on result |
| `api-connector-app/.../InvokeIntegrationTest.java` | Integration test | Structured auth error assertions |

---

## Pattern 1: AuthProvider SPI (built-in profiles)

**Role:** Java L2 profile implementations registered as Spring `@Bean`, indexed by `profileType()` in `AuthEngine`.

**Closest analog:** `AkskHmacSha256V1AuthProvider` — credential refs, config helpers, `AuthOutcome` return.

```23:60:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/AkskHmacSha256V1AuthProvider.java
public class AkskHmacSha256V1AuthProvider implements AuthProvider {

    private static final String TYPE = "aksk_hmac_sha256_v1";
    // ...

    @Override
    public String profileType() {
        return TYPE;
    }

    @Override
    public AuthOutcome apply(AuthContext context) {
        String accessKeyRef = stringConfig(context.authConfig(), "accessKeyRef", DEFAULT_ACCESS_KEY_REF);
        String secretKeyRef = stringConfig(context.authConfig(), "secretKeyRef", DEFAULT_SECRET_KEY_REF);
        // ...
        return new AuthOutcome(new HashMap<>(signedHeaders), Map.of(), null);
    }
}
```

**SPI contract:**

```18:34:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/spi/AuthProvider.java
public interface AuthProvider {

    String profileType();

    AuthOutcome apply(AuthContext context);
}
```

**Spring registration pattern** — add new profiles alongside existing beans:

```54:129:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/config/IntegrationEngineConfiguration.java
    @Bean
    public AuthEngine authEngine(List<AuthProvider> providers) {
        return new AuthEngine(providers);
    }
    // ...
    @Bean
    public AkskHmacSha256V1AuthProvider akskHmacSha256V1AuthProvider() {
        return new AkskHmacSha256V1AuthProvider();
    }
    // ... NoneAuthProvider, BearerStatic, ApiKeyQuery, OAuth2*, GaodeTraffic
```

**New Wave 1 profiles to add (after inventory confirms):**

| Profile ID | Analog | Notes |
|------------|--------|-------|
| `oauth2_password` | `OAuth2ClientCredentialsAuthProvider` | Password grant + token cache |
| `bearer_from_login` | `BearerStaticAuthProvider` + OAuth fetch | Login POST → Bearer |
| `sm3_header_sign_v1` | `AkskHmacSha256V1AuthProvider` + BouncyCastle | Conditional per D-04 |
| `groovy_auth_script` | `BearerStaticAuthProvider` (thin adapter) | Delegates to `ScriptCompileService` |

**Already implemented for Wave 1 catalogs:**

| code3rd | Profile | Catalog |
|---------|---------|---------|
| IDPS | `aksk_hmac_sha256_v1` | `IdpsConnectorCatalog` |
| GAODE_OPEN_PLATFORM | `api_key_query` | `GaodeOpenPlatformConnectorCatalog` |
| GAODE_TRAFFIC | `gaode_traffic_hmac_v1` | `GaodeTrafficConnectorCatalog` |
| BAIDU_MAP | `api_key_query` | `BaiduMapConnectorCatalog` |
| BAIDU_WENXIN | `oauth2_token_in_query` | `BaiduWenxinConnectorCatalog` |

---

## Pattern 2: AuthEngine pipeline merge

**Role:** Dispatch single profile or `auth.pipeline[]` multi-step; merge `AuthOutcome` across steps; share `ext` between steps.

**Closest analog:** Existing `AuthEngine` — extend error handling only.

```45:91:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/AuthEngine.java
    public AuthOutcome authenticate(AuthContext context) {
        Map<String, Object> auth = context.authConfig();
        if (auth == null || auth.isEmpty()) {
            return AuthOutcome.empty();
        }
        Object pipeline = auth.get("pipeline");
        if (pipeline instanceof List) {
            List<?> steps = (List<?>) pipeline;
            AuthOutcome merged = AuthOutcome.empty();
            for (Object step : steps) {
                if (step instanceof Map) {
                    merged = merge(merged, runStep(context, (Map<String, Object>) step));
                }
            }
            return merged;
        }
        return runStep(context, auth);
    }

    private AuthOutcome runStep(AuthContext context, Map<String, Object> stepConfig) {
        String type = String.valueOf(stepConfig.get("type"));
        AuthProvider provider = providersByType.get(type);
        if (provider == null) {
            throw new IllegalStateException("No AuthProvider for type: " + type);
        }
        AuthContext stepCtx = new AuthContext(
                context.code3rd(), context.baseUrl(), context.method(), context.path(),
                context.query(), context.requestBody(), stepConfig,
                context.credentials(), context.ext());
        return provider.apply(stepCtx);
    }
```

**Phase 1 change:** Replace `IllegalStateException` with `AuthException(AUTH_PROFILE_MISSING, Map.of("profileType", type))`.

**Pipeline + Groovy target shape:**

```yaml
auth:
  pipeline:
    - type: oauth2_client_credentials
      tokenUrl: /oauth/token
      scope: email
    - type: groovy_auth_script
      script: |
        // use ctx.ext.accessToken from prior step
```

---

## Pattern 3: AuthContext / AuthOutcome contracts

**Role:** Immutable input context; mutable `ext` for pipeline handoff; outcome = transport mutations.

**Input context (unchanged):**

```17:84:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/context/AuthContext.java
public final class AuthContext {
    private final String code3rd;
    // ... baseUrl, method, path, query, requestBody
    private final Map<String, Object> authConfig;
    private final Map<String, String> credentials;
    private final Map<String, Object> ext;
    // accessors ...
}
```

**Outcome (unchanged):**

```17:48:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/context/AuthOutcome.java
public final class AuthOutcome {
    private final Map<String, String> headers;
    private final Map<String, String> query;
    private final String mutatedBody;

    public static AuthOutcome empty() {
        return new AuthOutcome(Map.of(), Map.of(), null);
    }
}
```

**NEW: AuthContextSnapshot** — analog = `AuthContext` fields + frozen `ext` + profile metadata:

```java
// Target shape (new file)
public record AuthContextSnapshot(
        String code3rd,
        List<String> profileTypes,
        Map<String, String> credentialRefsUsed,  // ref names only, not secrets
        Map<String, Object> ext) {               // accessToken, tokenExpiresAt, signatureBase, ...

    static AuthContextSnapshot from(AuthContext ctx, AuthOutcome outcome, List<String> profiles) {
        // copy ext; add appliedHeaders from outcome if needed
    }
}
```

**Standard `ext` keys (D-11):** `accessToken`, `tokenExpiresAt`, `oauthRawResponse`, `signatureBase`, `appliedHeaders`.

---

## Pattern 4: DefaultIntegrationOrchestrator integration

**Role:** Build `AuthContext` → authenticate → merge outcome → HTTP → return result with snapshot.

**Closest analog:** Current orchestrator — extension point is immediately after `authenticate()`.

```61:110:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
    public InvocationResult invoke(InvocationRequest request) {
        ConnectorSpec spec = registry.require(request.connectorCode().value());
        Map<String, String> credentials = registry.credentials(request.connectorCode().value());

        AuthContext authContext = new AuthContext(
                request.connectorCode().value(),
                spec.baseUrl(),
                request.method().name(),
                request.path(),
                request.query() != null ? request.query() : Map.of(),
                request.body(),
                spec.auth(),          // ← replace with AuthConfigResolver.resolve(spec, endpoint)
                credentials,
                Map.of());            // ← mutable ext for pipeline; snapshot after auth
        AuthOutcome authOutcome = authEngine.authenticate(authContext);
        // ... merge headers/query/body → httpTransport.exchange
        return new InvocationResult(/* no auth snapshot today */);
    }
```

**Gaps to fix:**

1. Resolve endpoint via `EndpointResolver` + `request.endpointId()` to get `EndpointSpec`
2. `AuthConfigResolver.resolve(spec, endpoint)` — connector `auth` unless `endpoint.authOverride()` (Groovy-only)
3. After `authenticate()`: `AuthContextSnapshot.from(authContext, authOutcome, ...)`
4. Extend `InvocationResult` constructor with optional snapshot + outcome
5. Mirror in `invokeStream()`

**Endpoint resolution analog:**

```38:59:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/EndpointResolver.java
    public static ResolvedInvocation resolve(
            ConnectorSpec spec, String endpointId, String method, String path) {
        if (endpointId != null && !endpointId.isBlank()) {
            EndpointSpec endpoint = requireEndpoint(spec, endpointId.trim());
            // ...
            return new ResolvedInvocation(endpoint.id(), resolvedMethod, resolvedPath);
        }
        // ...
    }
```

**NEW AuthConfigResolver** — same resolution style as `EndpointResolver`:

```java
public final class AuthConfigResolver {
    public static Map<String, Object> resolve(ConnectorSpec spec, EndpointSpec endpoint) {
        if (endpoint != null && endpoint.authOverride() != null) {
            validateGroovyOnly(endpoint.authOverride());
            return endpoint.authOverride();
        }
        return spec.auth();
    }
}
```

---

## Pattern 5: ConnectorSpec / EndpointSpec models

**Role:** Declarative auth config at connector level; Groovy-only override at endpoint level.

**ConnectorSpec (auth at connector level — existing):**

```18:69:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/ConnectorSpec.java
public final class ConnectorSpec {
    private final Map<String, Object> auth;
    private final List<EndpointSpec> endpoints;
    // ...
    public Map<String, Object> auth() {
        return auth;
    }
}
```

**EndpointSpec (no authOverride yet):**

```15:66:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/model/EndpointSpec.java
public final class EndpointSpec {
    private final String id;
    private final String method;
    private final String path;
    // ... bodyTemplate, enabled, doc — no authOverride
}
```

**Parser extension point:**

```67:76:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/ConnectorSpecParser.java
    private static EndpointSpec toEndpoint(Map<String, Object> m) {
        EndpointSpec endpoint = new EndpointSpec(
                str(m.get("id")),
                str(m.get("method")),
                str(m.get("path")),
                str(m.get("bodyTemplate")),
                m.get("enabled") == null || Boolean.TRUE.equals(m.get("enabled")),
                null);
        return EndpointDocumentation.enrich(endpoint);
    }
```

**Target YAML shape:**

```yaml
auth:
  type: groovy_auth_script
  script: |
    // inline Groovy source (D-09)

endpoints:
  - id: specialSign
    method: POST
    path: /vendor/special
    authOverride:          # Groovy-only (D-05)
      type: groovy_auth_script
      script: |
        // endpoint-specific signing
```

---

## Pattern 6: Java Catalog connector auth

**Role:** Wave 1 vendors declare auth via `@CatalogAuth` on catalog interface; scanner builds `auth` map.

**Closest analog:** `IdpsConnectorCatalog` + `CatalogConnectorScanner.authMap()`.

```16:18:api-connector-connectors/src/main/java/com/suntek/apiconnector/connectors/idps/IdpsConnectorCatalog.java
@CatalogConnector(code3rd = "IDPS", baseUrl = "https://idps.example.com")
@CatalogAuth(type = "aksk_hmac_sha256_v1", accessKeyRef = "publicKey", secretKeyRef = "appSecret")
@CatalogResponse(successWhen = "$.success", dataPath = "$.obj", messagePath = "$.msg")
```

```113:128:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/catalog/CatalogConnectorScanner.java
    private static Map<String, Object> authMap(CatalogAuth auth) {
        if (auth == null) {
            return Map.of("type", "none");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", auth.type());
        putIfPresent(map, "accessKeyRef", auth.accessKeyRef());
        putIfPresent(map, "secretKeyRef", auth.secretKeyRef());
        // ... tokenUrl, clientIdRef, etc.
        return map;
    }
```

**Catalog registration at startup:**

```28:33:api-connector-app/src/main/java/com/suntek/apiconnector/app/config/ConnectorBootstrapConfiguration.java
        for (ConnectorSpec spec : BuiltinConnectorCatalogs.loadAll()) {
            register(registry, spec);
            registered.add(spec.code3rd());
        }
```

```85:100:api-connector-connectors/src/main/java/com/suntek/apiconnector/connectors/BuiltinConnectorCatalogs.java
    private static java.util.List<ConnectorSpec> scanAll() {
        return java.util.List.of(
                CatalogConnectorScanner.scan(DemoNoneConnectorCatalog.class),
                CatalogConnectorScanner.scan(DemoAkskConnectorCatalog.class),
                CatalogConnectorScanner.scan(IdpsConnectorCatalog.class),
                // ... Gaode, Baidu catalogs
        );
    }
```

**Phase 1:** Extend `@CatalogAuth` with optional `script()` for Groovy; add demo/Hikvision Groovy catalog when inventory confirms.

---

## Pattern 7: OAuth token caching (centralize)

**Role:** Replace per-provider in-memory maps with shared `TokenCache`; key = `code3rd + profile + scope`.

**Anti-pattern (current) — replace this:**

```54:68:api-connector-auth/src/main/java/com/suntek/apiconnector/auth/profile/OAuth2ClientCredentialsAuthProvider.java
    private String resolveAccessToken(AuthContext context) {
        String cacheKey = context.code3rd();
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(30))) {
            return cached.accessToken;
        }
        synchronized (this) {
            // ... fetch and cache
        }
    }
```

**Target pattern:**

```java
// TokenCache — analog: inner CachedToken + synchronized block above
public class TokenCache {
    private final ConcurrentHashMap<TokenCacheKey, CachedToken> store = new ConcurrentHashMap<>();

    public String getOrRefresh(TokenCacheKey key, Supplier<CachedToken> fetcher) {
        // proactive refresh: now < expiresAt - 60s (D-20)
        // single-flight per key via compute / ReentrantLock
    }

    public void evictForConnector(String code3rd) { /* D-21 */ }
}

record TokenCacheKey(String code3rd, String profileType, String scope) {}
```

**Provider change:** Inject `TokenCache`; after fetch, write `context.ext()`:

```java
ext.put("accessToken", token);
ext.put("tokenExpiresAt", expiresAt.toString());
ext.put("oauthRawResponse", rawJson);
```

Same refactor applies to `OAuth2TokenInQueryAuthProvider` (identical cache pattern).

---

## Pattern 8: Groovy compile-on-publish (api-connector-scripting)

**Role:** Own JSR-223 compilation; cache by SHA-256 of source; no per-request compile (AUTH-03).

**Closest analog:** None in repo. Structural analog = OAuth cache (ConcurrentHashMap keyed lookup).

**Target API:**

```java
// api-connector-scripting
public class ScriptCompileService {
    private final CompiledScriptCache cache;
    private final Compilable groovyEngine;

    public CompiledScript compile(String source, String label) {
        String hash = sha256(source);
        return cache.getOrCompute(hash, () -> {
            try {
                return groovyEngine.compile(source);
            } catch (CompilationFailedException e) {
                throw new AuthException(AUTH_SCRIPT_COMPILE_ERROR, Map.of(
                    "label", label, "line", e.getLine(), "message", e.getMessage()));
            }
        });
    }
}
```

**GroovyAuthScriptProvider** — analog = thin `AuthProvider` like `BearerStaticAuthProvider`:

```java
public class GroovyAuthScriptProvider implements AuthProvider {
    private final ScriptCompileService compileService;

    @Override
    public String profileType() { return "groovy_auth_script"; }

    @Override
    public AuthOutcome apply(AuthContext context) {
        String source = stringConfig(context.authConfig(), "script", null);
        CompiledScript compiled = compileService.compile(source, context.code3rd());
        Bindings bindings = new SimpleBindings();
        bindings.put("ctx", context);
        return (AuthOutcome) compiled.eval(bindings);
    }
}
```

**AuthScript contract (validated at publish):**

```java
@FunctionalInterface
public interface AuthScript {
    AuthOutcome apply(AuthContext ctx);
}
```

**Invoke path:** `compiled.eval(bindings)` only — never `new GroovyShell()` or `eval(source)` per request.

---

## Pattern 9: Publish hooks (compile + token evict)

**Role:** On connector register/save/reload: compile all Groovy scripts; evict OAuth tokens for `code3rd`.

**Hook points (existing):**

| Hook | File | Method |
|------|------|--------|
| Startup catalog | `ConnectorBootstrapConfiguration` | `register(registry, spec)` |
| Runtime save | `ConnectorRegistry` | `save()`, `register()` |
| JDBC sync | `ConnectorConfigSyncService` | `reloadFromStore()` |

```48:53:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/ConnectorRegistry.java
    public void save(ConnectorSpec spec, Map<String, String> credentialPatch, ConnectorSpecStatus status) {
        String code3rd = spec.code3rd();
        specs.put(code3rd, spec);
        mergeCredentials(code3rd, credentialPatch);
        statuses.put(code3rd, status);
        // ← add: publishListener.onPublish(spec)
    }
```

```54:68:api-connector-persistence/src/main/java/com/suntek/apiconnector/persistence/ConnectorConfigSyncService.java
    public int reloadFromStore() {
        for (StoredConnectorConfig item : published) {
            registry.save(
                    CatalogManagedSpecMerger.forRuntime(item.spec()),
                    item.credentials(),
                    ConnectorSpecStatus.PUBLISHED);
            // ← publish listener fires via registry.save()
        }
    }
```

**ConnectorPublishListener responsibilities:**

1. Scan `spec.auth()` and all `endpoint.authOverride` for `type: groovy_auth_script`
2. `ScriptCompileService.compile(script, code3rd + ":" + endpointId)`
3. `TokenCache.evictForConnector(code3rd)`
4. Fail fast on compile error for managed catalog connectors

---

## Pattern 10: Structured auth errors

**Role:** Unified platform codes with `details` map (D-17, D-18, AUTH-06).

**Current handler (too coarse):**

```46:52:api-connector-api/src/main/java/com/suntek/apiconnector/api/RuntimeApiExceptionHandler.java
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> failedDependency(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiErrorResponse.builder()
                .code("UPSTREAM_AUTH_FAILED")
                .message(ex.getMessage())
                .build());
    }
```

**Current DTO (no details):**

```16:23:api-connector-api/src/main/java/com/suntek/apiconnector/api/dto/ApiErrorResponse.java
public class ApiErrorResponse {
    private String code;
    private String message;
}
```

**Target:**

```java
// AuthException — analog: InvokeRateLimitException
public class AuthException extends RuntimeException {
    private final AuthErrorCode code;
    private final Map<String, Object> details;
}

public enum AuthErrorCode {
    AUTH_PROFILE_MISSING,
    UPSTREAM_AUTH_FAILED,
    AUTH_SCRIPT_COMPILE_ERROR,
    AUTH_SCRIPT_RUNTIME_ERROR
}
```

```java
// ApiErrorResponse extension
private Map<String, Object> details;

// RuntimeApiExceptionHandler
@ExceptionHandler(AuthException.class)
public ResponseEntity<ApiErrorResponse> authFailed(AuthException ex) {
    HttpStatus status = ex.code() == AUTH_PROFILE_MISSING
            ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
    return ResponseEntity.status(status).body(ApiErrorResponse.builder()
            .code(ex.code().name())
            .message(ex.getMessage())
            .details(ex.details())
            .build());
}
```

---

## Pattern 11: InvocationResult carry-forward

**Role:** Expose auth snapshot + outcome on invoke result for Phase 2 mapping and Phase 3 audit.

**Current (no auth fields):**

```17:97:api-connector-domain/src/main/java/com/suntek/apiconnector/domain/model/InvocationResult.java
public final class InvocationResult {
    private final int httpStatus;
    private final boolean success;
    // ... vendorCode, vendorMessage, rawBody, parsedData, latencyMillis, responseHeaders
}
```

**Target:** Add optional fields (backward-compatible overload):

```java
private final AuthContextSnapshot authSnapshot;
private final AuthOutcome authOutcome;

public Optional<AuthContextSnapshot> authSnapshot() { return Optional.ofNullable(authSnapshot); }
public Optional<AuthOutcome> authOutcome() { return Optional.ofNullable(authOutcome); }
```

**Test analog:** Extend `DefaultIntegrationOrchestratorTest` — currently asserts vendor parsing only; add snapshot assertion after OAuth/Groovy invoke.

---

## Pattern 12: Module dependency graph

**Current:**

```
api-connector-domain ← api-connector-auth ← api-connector-engine ← api-connector-app
api-connector-spec   ← api-connector-auth
api-connector-connectors (catalog specs)
```

**Phase 1 additions:**

```
api-connector-dependencies     (+ groovy-jsr223 BOM)
api-connector-scripting        (NEW — groovy-jsr223 only)
api-connector-domain           (+ AuthContextSnapshot; extend InvocationResult)
api-connector-auth             (+ scripting dep; TokenCache, GroovyAuthScriptProvider, AuthException)
api-connector-spec             (+ EndpointSpec.authOverride, parser validation)
api-connector-engine           (+ AuthConfigResolver, publish listener, orchestrator changes)
api-connector-api              (+ ApiErrorResponse.details, AuthException handler)
api-connector-connectors       (Wave 1 catalog updates)
docs/legacy-auth-inventory.md  (NEW)
```

**Root pom module list** — add after `api-connector-spec`:

```xml
<module>api-connector-scripting</module>
```

---

## Anti-patterns to avoid

| Anti-pattern | Why | Use instead |
|--------------|-----|-------------|
| `ScriptEngine.eval(source)` per invoke | 10–50× latency (PITFALLS #2) | Compile on publish; `CompiledScript.eval(bindings)` |
| OAuth cache keyed only by `code3rd` | Pipeline profiles collide | `TokenCacheKey(code3rd, profile, scope)` |
| Endpoint overrides full Java pipeline | Spec explosion (D-05) | Groovy-only `authOverride` |
| Discarding `AuthContext` after auth | Mapping can't read tokens (AUTH-05) | `AuthContextSnapshot` on result |
| `IllegalStateException` for missing profile | Loses `profileType` in response | `AuthException(AUTH_PROFILE_MISSING, details)` |
| PF4J for L3 vendors in Phase 1 | Deferred (D-15) | Groovy scripts + Spring `@Bean` if SPI ever needed |

---

## Verification anchors

| Requirement | Pattern | Test location |
|-------------|---------|---------------|
| AUTH-01 | Pattern 1, 6 | `*AuthProviderTest`, `ProductionConnectorCatalogsTest` |
| AUTH-02 | Pattern 8 | `GroovyAuthScriptProviderTest` |
| AUTH-03 | Pattern 8, 9 | `ScriptCompileServiceTest` |
| AUTH-04 | Pattern 3 | `AuthContextSnapshotTest` |
| AUTH-05 | Pattern 4, 11 | `DefaultIntegrationOrchestratorTest` |
| AUTH-06 | Pattern 10 | `AuthEngineTest`, `InvokeIntegrationTest` |
| Success #6 | Pattern 6 inventory | `docs/legacy-auth-inventory.md` exists |

**CI command:** `.\mvnw-jdk21.ps1 clean verify`

---

## Walking skeleton (recommended first slice)

Per RESEARCH — implement in this order to unlock AUTH-02..06 structure before all Wave 1 L2 profiles:

1. `api-connector-scripting` + `ScriptCompileServiceTest`
2. `GroovyAuthScriptProvider` + test script in `src/test/resources/scripts/`
3. Refactor one OAuth provider → `TokenCache`
4. `AuthContextSnapshot` + orchestrator carry-forward
5. `ApiErrorResponse.details` + `AUTH_PROFILE_MISSING`
6. Draft `docs/legacy-auth-inventory.md` with Wave 1 rows

---

*Phase: 01-auth-plugin-architecture*  
*Pattern mapping: 2026-06-17*
