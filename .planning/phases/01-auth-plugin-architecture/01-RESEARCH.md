# Phase 1: Auth Plugin Architecture - Research

**Researched:** 2026-06-17
**Domain:** Outbound auth plugin architecture (Java AuthProvider SPI + Groovy JSR-223 + invoke pipeline carry-forward)
**Confidence:** HIGH

---

## User Constraints

### Locked Decisions (from `01-CONTEXT.md` — NON-NEGOTIABLE)

#### v1 Built-in Profile Scope
- **D-01:** Phase 1 Java built-ins cover auth types required by **Wave 1 migration vendors** only (IDPS, Gaode, Baidu, Hikvision, TrafficBrain, etc.). Remaining legacy types use Groovy until their migration wave.
- **D-02:** **L2 profiles** used by Wave 1 vendors ship as Java `AuthProvider` classes (e.g., `oauth2_password`, `bearer_from_login`). Exotic L2 combos not in Wave 1 may still defer.
- **D-03:** Deliver **`docs/legacy-auth-inventory.md`**: vendor → profile type → L1/L2/L3 → migration wave → implementation path (Java / Groovy / SPI / deferred). Success criterion #6 artifact.
- **D-04:** **国密** (SM3 header sign, SM4 body encrypt) Java profiles included **only if** the legacy audit shows Wave 1 vendors require them.

#### Groovy Auth Hook Design
- **D-05:** Groovy auth scripts bind at **connector level by default**, with **per-endpoint override** when needed. Endpoint override is **Groovy-only** — endpoints inherit Java built-in profiles from connector; only `groovy_auth_script` may differ per endpoint.
- **D-06:** New **`api-connector-scripting`** module owns Groovy JSR-223 compilation and publish-time cache; shared by auth (Phase 1) and mapping (Phase 2).
- **D-07:** Groovy integrates as profile type **`groovy_auth_script`** — an `AuthProvider` adapter usable as standalone auth or as a `auth.pipeline[]` step.
- **D-08:** Scripts **compile on publish/load** (Catalog YAML, classpath spec, or future JDBC sync). Cache keyed by content hash. **Compile + contract validation** on publish; fail fast with structured error (line/symbol when possible). No per-request recompilation (AUTH-03).
- **D-09:** Groovy script **source of truth** is the `ConnectorSpec` auth block (YAML/JSON in spec). Compiled artifact cached in memory.

#### AuthContext Downstream Shape
- **D-10:** Downstream pipeline receives **immutable `AuthContextSnapshot`** (read-only post-auth copy) **plus `AuthOutcome`** (headers/query/body mutations). AUTH-04 input `AuthContext` remains immutable for auth execution.
- **D-11:** OAuth tokens and signing intermediates exposed via structured **`ext` map** on the snapshot with documented standard keys (`accessToken`, `tokenExpiresAt`, `signatureBase`, `oauthRawResponse`, etc.).
- **D-12:** Snapshot + outcome **carried through the invoke pipeline** on `InvocationRequest`/result for mapping (Phase 2) and audit enrichment (Phase 3).

#### L3 Java SPI vs Groovy Boundary
- **D-13:** **L3 vendors default to Groovy** in Phase 1 (大华 multi-step login, 海康 Artemis SDK, Cookie session, 讯飞 WS). No new `custom_spi` Java classes unless ADR-002 criteria force it.
- **D-14:** **ADR-002 strict SPI admission**: multi-step login ≥3 round-trips, vendor SDK only, non-templateable crypto, Cookie/WebSocket dedicated.
- **D-15:** **PF4J hot-deploy deferred to v2** (PLAT-V2-02). Interim L3 Java (if ever needed before v2) registers as **Spring `@Bean` `AuthProvider`** in `api-connector-auth`.
- **D-16:** **Transform Pipeline** (SM4 encrypt, business envelope per ADR-002) **deferred to Phase 2** mapping engine — not in Phase 1 auth scope.

#### Auth Failure & Errors
- **D-17:** Phase 1 uses **unified platform error codes** (`UPSTREAM_AUTH_FAILED`, `AUTH_PROFILE_MISSING`, script compile errors, etc.). Legacy-shaped per-vendor error JSON deferred to compat layer (Phase 6) / mapping.
- **D-18:** Structured JSON payload: `{ code, message, details? }` — `details` includes missing profile type, script compile failure, `code3rd`, profile type for ops.

#### OAuth Token Cache
- **D-19:** **Central `TokenCache` service** keyed by `code3rd + profile + scope` (ADR-002). All OAuth providers use it; replace per-provider in-memory maps.
- **D-20:** **Proactive refresh** with expiry skew (e.g., 60s before `expiresAt`); single-flight per cache key.
- **D-21:** **Evict all tokens for `code3rd`** on connector republish or credential change.

#### Phase 1 Config & Validation
- **D-22:** Auth profiles and Groovy scripts configured via **Java Catalog + classpath YAML + env credentials only** in Phase 1. JDBC admin persistence UI deferred to Phase 4.
- **D-23:** Phase 1 validation workflow: **unit/integration tests** via Java Catalog connectors and Groovy scripts in **test resources**; no admin dry-run API until Phase 4.

### Claude's Discretion

None — user made explicit choices for all presented options.

### Deferred Ideas (OUT OF SCOPE)

- **PF4J hot-deploy auth/mapping JARs** — v2 (PLAT-V2-02); user confirmed defer after initial PF4J selection
- **Transform Pipeline** (SM4 body encrypt, business envelope) — Phase 2 mapping per ADR-002
- **Per-vendor legacy error code shaping** — Phase 6 compat harness / mapping
- **JDBC persistence + admin CRUD for auth scripts** — Phase 4 Admin BFF
- **L3 Java SPI vendor plugins** (大华, 海康, etc.) — Groovy in Phase 1; Java SPI only if ADR-002 criteria met, via Spring bean until PF4J v2
- **Distributed OAuth token cache (Redis)** — v2 (PLAT-V2-01)
- **Groovy sandbox for untrusted authors** — v2 (AUTH-V2-02)

---

## Standard Stack

### Core (existing — verified in repo)

| Technology | Version | Purpose | Confidence |
|------------|---------|---------|------------|
| Java | 21 | Runtime, records, virtual threads | [VERIFIED: `.planning/codebase/STACK.md`] |
| Spring Boot | 4.0.6 | Bean wiring for `AuthProvider`, registry hooks | [VERIFIED: `api-connector-dependencies/pom.xml`] |
| Maven | 3.9.x | Multi-module build | [VERIFIED: root `pom.xml`] |
| BouncyCastle `bcprov-jdk18on` | 1.80 | HMAC + planned 国密 SM3/SM4 | [VERIFIED: `api-connector-auth/pom.xml`] |
| Jackson databind | via Spring BOM | OAuth token JSON, error payloads | [VERIFIED: OAuth providers] |
| JDK `HttpClient` | JDK 21 | OAuth token fetch inside providers | [VERIFIED: `OAuth2ClientCredentialsAuthProvider.java`] |
| JUnit 5 + WireMock | 3.5.4 / 3.13.1 | Profile unit tests + invoke integration | [VERIFIED: `api-connector-app/pom.xml`] |

### New for Phase 1

| Technology | Version | Purpose | Confidence |
|------------|---------|---------|------------|
| Apache Groovy `groovy-jsr223` | **4.0.32** (pin in BOM) | Script compile + `CompiledScript` cache | [VERIFIED: https://groovy.apache.org/download.html] [CITED: Groovy 4.0.32 on Maven Central] |
| Apache Groovy `groovy` (transitive) | 4.0.32 | Core compiler invoked by JSR-223 engine | [VERIFIED: Groovy JSR-223 docs] |

**Maven coordinates (Groovy 4+):** use `org.apache.groovy`, **not** legacy `org.codehaus.groovy`. [CITED: https://groovy-lang.org/releasenotes/groovy-4.0.html]

**BOM addition (recommended):**

```xml
<groovy.version>4.0.32</groovy.version>
<!-- in api-connector-dependencies -->
<dependency>
  <groupId>org.apache.groovy</groupId>
  <artifactId>groovy-jsr223</artifactId>
  <version>${groovy.version}</version>
</dependency>
```

### Module dependency graph (Phase 1 additions)

```
api-connector-domain      ← AuthContextSnapshot (new record)
api-connector-scripting   ← NEW: ScriptCompileService, ScriptCache
api-connector-auth        ← depends on domain, spec, scripting; TokenCache, GroovyAuthScriptProvider
api-connector-spec        ← EndpointSpec authOverride; auth block schema for script source
api-connector-engine      ← orchestrator carry-forward; publish hook → compile + token evict
api-connector-connectors  ← Wave 1 catalogs reference built-in profile IDs
```

---

## Architecture Patterns

### System flow (Phase 1 target)

```
Invoke API
  → ConnectorRegistry.require(code3rd)
  → resolveAuthConfig(connector.auth, endpoint.authOverride?)   // Groovy-only endpoint override
  → build AuthContext (immutable input)
  → AuthEngine.authenticate(context)
       → AuthProvider.apply (Java built-in OR groovy_auth_script adapter)
       → merge pipeline steps → AuthOutcome
  → build AuthContextSnapshot from context.ext + standard keys   // post-auth read-only
  → attach snapshot + outcome to pipeline carrier
  → merge AuthOutcome → HTTP request
  → HttpTransport.exchange
  → InvocationResult (+ auth snapshot for Phase 2/3)
```

**Current gap:** `DefaultIntegrationOrchestrator` uses `spec.auth()` only, passes empty `ext`, and does not attach snapshot to result. [VERIFIED: `DefaultIntegrationOrchestrator.java` lines 66–75, 101–110]

### Module responsibilities

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `AuthEngine` | `api-connector-auth` | Dispatch single profile or `auth.pipeline[]`; merge outcomes | [VERIFIED: existing] |
| `AuthProvider` SPI | `api-connector-auth/spi` | Java profiles + `groovy_auth_script` adapter | [VERIFIED: existing SPI] |
| `ScriptCompileService` | `api-connector-scripting` | JSR-223 compile, contract check, content-hash cache | [ASSUMED: new] |
| `GroovyAuthScriptProvider` | `api-connector-auth/profile` | `profileType()` → `groovy_auth_script`; delegates to compiled script | [ASSUMED: new] |
| `TokenCache` | `api-connector-auth/cache` | OAuth token store; key = `code3rd\|profile\|scope` | [ASSUMED: new] |
| `AuthContextSnapshot` | `api-connector-domain` or `auth/context` | Immutable post-auth view for mapping/audit | [ASSUMED: new] |
| `ConnectorPublishListener` | `api-connector-engine` | On `ConnectorRegistry.save/register`: compile scripts, evict tokens | [ASSUMED: new] |
| `AuthException` + codes | `api-connector-auth` or `domain` | Typed failures → structured API errors | [ASSUMED: new] |

### Pattern 1: Groovy compile-on-publish cache (D-06, D-08, AUTH-03)

**What:** Compile script source once when connector spec enters registry; cache `CompiledScript` by SHA-256 of source text.

**Mechanism:** Groovy's JSR-223 `Compilable.compile(String)` returns `GroovyCompiledScript` backed by a cached `Class<?>` in `GroovyScriptEngineImpl.classMap`. [CITED: Apache Groovy JSR-223 source — `getScriptClass` + `compile`]

**Cache key:** `sha256(scriptSource)` — independent of connector id so identical scripts share bytecode.

**Publish hook points:**
1. `ConnectorBootstrapConfiguration.register()` — startup Catalog/YAML load [VERIFIED: `ConnectorBootstrapConfiguration.java`]
2. `ConnectorRegistry.save()` / `register()` — runtime republish [VERIFIED: `ConnectorRegistry.java`]
3. `ConnectorConfigSyncService.reloadFromStore()` — JDBC sync (Phase 4 path; hook now for future) [VERIFIED: `ConnectorConfigSyncService.java`]

**Contract validation on publish:** Script must expose a callable entry (recommended: class implementing `AuthScript` functional interface with `AuthOutcome apply(AuthContext ctx)`). Fail publish with `AUTH_SCRIPT_COMPILE_ERROR` + line number from `CompilationFailedException`.

**Anti-pattern:** Creating `new GroovyShell()` or `ScriptEngine.eval(source)` per invoke — causes 10–50× latency (Pitfall 2). [VERIFIED: `.planning/research/PITFALLS.md`]

### Pattern 2: `groovy_auth_script` as AuthProvider adapter (D-07)

**What:** Treat Groovy like any other profile type in `AuthEngine.providersByType`.

**Config shape (connector-level):**

```yaml
auth:
  type: groovy_auth_script
  script: |
    // Groovy source inline in spec
    headers -> [Authorization: "Bearer ${ctx.credentials.apiKey}"]
```

**Pipeline step:**

```yaml
auth:
  pipeline:
    - type: oauth2_client_credentials
      tokenUrl: /oauth/token
      scope: email
    - type: groovy_auth_script
      script: |
        // add vendor-specific headers using ctx.ext.accessToken
```

**Endpoint override (Groovy-only, D-05):**

```yaml
endpoints:
  - id: specialSign
    method: POST
    path: /vendor/special
    authOverride:
      type: groovy_auth_script
      script: |
        // endpoint-specific signing only
```

Resolution rule: if `endpoint.authOverride != null` → use override config **instead of** connector `auth` for that endpoint; override must be `groovy_auth_script` type only.

### Pattern 3: AuthContextSnapshot + ext carry-forward (D-10, D-11, D-12)

**Input (unchanged):** `AuthContext` — immutable per auth step; `ext` map mutable **within** provider execution for pipeline handoff between steps.

**Output (new):**
- `AuthOutcome` — transport mutations (headers/query/body) [VERIFIED: existing]
- `AuthContextSnapshot` — read-only record with:
  - `code3rd`, `profileType(s)`, `credentials` refs used (not secret values)
  - `ext` map with standard keys:

| Key | Source | Purpose |
|-----|--------|---------|
| `accessToken` | OAuth providers | Mapping scripts read token without re-fetch |
| `tokenExpiresAt` | OAuth providers | Audit/debug |
| `oauthRawResponse` | OAuth providers | Vendor-specific fields |
| `signatureBase` | HMAC providers | Verify signing input |
| `appliedHeaders` | merged outcome | Downstream reference |

**Pipeline carrier:** Extend `InvocationResult` (or intermediate `InvocationContext`) with optional `AuthContextSnapshot` + `AuthOutcome`. Phase 1 must wire through orchestrator even if mapping (Phase 2) is not yet consuming it — satisfies AUTH-05 structurally.

### Pattern 4: Central TokenCache (D-19–D-21)

**Replace:** Per-provider `ConcurrentHashMap<String, CachedToken>` keyed only by `code3rd`. [VERIFIED: `OAuth2ClientCredentialsAuthProvider.java` lines 37, 55; same in `OAuth2TokenInQueryAuthProvider.java`]

**New key:** `TokenCacheKey(code3rd, profileType, scope)` — ADR-002 + D-19.

**Refresh:** Return cached token if `now < expiresAt - skew` (skew default 60s per D-20; existing providers use 30s — align to 60s).

**Single-flight:** `ConcurrentHashMap.compute` or per-key `ReentrantLock` to prevent thundering herd on expiry.

**Eviction:** `TokenCache.evictForConnector(code3rd)` called from publish listener when spec or credentials change (D-21).

### Pattern 5: Structured auth errors (D-17, D-18, AUTH-06)

**Replace:** Generic `IllegalStateException("No AuthProvider for type: ...")` → 502 `UPSTREAM_AUTH_FAILED`. [VERIFIED: `AuthEngine.java` line 69; `RuntimeApiExceptionHandler.java` lines 46–51]

**New exception type:** `AuthException` with `AuthErrorCode` enum:

| Code | When |
|------|------|
| `AUTH_PROFILE_MISSING` | No `AuthProvider` for profile type |
| `UPSTREAM_AUTH_FAILED` | OAuth/token HTTP failure, missing credential ref |
| `AUTH_SCRIPT_COMPILE_ERROR` | Groovy compile/contract failure at publish |
| `AUTH_SCRIPT_RUNTIME_ERROR` | Groovy execution failure at invoke |

**Extend `ApiErrorResponse`:** add optional `Map<String, Object> details` per D-18. [VERIFIED: current DTO lacks `details` — `ApiErrorResponse.java`]

### Pattern 6: legacy-auth-inventory.md audit (D-03)

**Source:** Read-only scan of `D:\Work\99_Code\ITS\suntek-system\system-thirdpart` (325 client files, 50+ controllers). [VERIFIED: filesystem scan 2026-06-17]

**Audit procedure:**
1. List `controller/*Controller.java` → vendor domain name
2. Map controller → `client/<vendor>/*Client.java` (InvokeService if present)
3. Classify auth from Client code: OAuth, HMAC, Bearer login, Cookie, SDK, 国密, none
4. Map to `docs/profile-registry.md` profile ID + L1/L2/L3
5. Assign migration wave (Wave 1 = IDPS, Gaode, Baidu, Hikvision, TrafficBrain per CONTEXT)
6. Assign implementation path: Java / Groovy / SPI / deferred

**Output columns:** `vendor | code3rd | legacy_client | profile_id | level | wave | path | notes`

**Known Wave 1 catalog mappings (already implemented):**

| code3rd | Profile | Status |
|---------|---------|--------|
| IDPS | `aksk_hmac_sha256_v1` | Java ✅ |
| GAODE_OPEN_PLATFORM | `api_key_query` | Java ✅ |
| GAODE_TRAFFIC | `gaode_traffic_hmac_v1` | Java ✅ |
| BAIDU_MAP | `api_key_query` | Java ✅ |
| BAIDU_WENXIN | `oauth2_token_in_query` | Java ✅ |

**Wave 1 gaps requiring new Java L2 (pending audit confirmation):** `oauth2_password`, `bearer_from_login` — referenced in CONTEXT D-02; not yet in codebase. [VERIFIED: grep shows no Java implementation]

**Wave 1 L3 → Groovy per D-13:** Hikvision (`hikvision_artemis_sdk_v1`), 大华 (`multi_step_login_dahua_v1`), 讯飞 WS — Groovy scripts in Phase 1, not Java SPI.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Groovy parsing/compilation | Custom expression evaluator | Groovy 4 JSR-223 `Compilable.compile()` | AST, Java interop, error line numbers built-in [CITED: Groovy JSR-223] |
| OAuth token caching | Per-provider maps | Central `TokenCache` + single-flight | ADR-002 keying; avoids duplicate token fetches across profile types [VERIFIED: duplicate pattern in 2 OAuth providers] |
| HMAC-SHA256 signing | New MAC implementations | `AkskCanonicalSigner`, `javax.crypto.Mac` | IDPS canonical string already extracted [VERIFIED: `AkskCanonicalSigner.java`] |
| 国密 SM3/SM4 | Custom crypto | BouncyCastle `bcprov-jdk18on` | Already on classpath; ADR-002 decision [VERIFIED: ADR-002] |
| Script sandbox (v1) | JVM SecurityManager isolation | Trusted-admin scripts only; defer sandbox to AUTH-V2-02 | CONTEXT deferred |
| SPI/plugin classloading | PF4J OSGi | Spring `@Bean` AuthProvider registration | D-15 PF4J deferred |
| Auth error HTTP mapping | Per-controller try/catch | `AuthException` + `@RestControllerAdvice` | Existing `RuntimeApiExceptionHandler` pattern [VERIFIED] |
| Endpoint auth resolution | Ad-hoc in API layer | `AuthConfigResolver` in engine/spec | Keeps orchestrator single entry point |

**Key insight:** The brownfield `AuthEngine` + `AuthProvider` SPI already matches ADR-002. Phase 1 extends — it does not replace — this architecture.

---

## Common Pitfalls

### Pitfall 1: Groovy on hot path without caching (AUTH-03)

**What goes wrong:** Every invoke recompiles script; P99 latency spikes.
**Why:** Naive `ScriptEngine.eval(source)` per request.
**How to avoid:** Compile on publish; invoke path calls `CompiledScript.eval(bindings)` only. [VERIFIED: `.planning/research/PITFALLS.md` #2]
**Warning signs:** CPU high at moderate QPS; latency correlates with script-enabled connectors.

### Pitfall 2: Auth context lost before mapping (AUTH-04, AUTH-05)

**What goes wrong:** Phase 2 mapping re-fetches OAuth tokens or uses stale signature inputs.
**Why:** Orchestrator discards `AuthContext` after merging headers.
**How to avoid:** Build `AuthContextSnapshot` immediately after `authEngine.authenticate()`; attach to pipeline carrier. [VERIFIED: `.planning/research/PITFALLS.md` #3; orchestrator currently drops context]

### Pitfall 3: OAuth cache keyed only by code3rd

**What goes wrong:** Two OAuth profiles on same connector (pipeline) share/wrong token.
**Why:** Current providers use `cacheKey = context.code3rd()` only. [VERIFIED: `OAuth2ClientCredentialsAuthProvider.java:55`]
**How to avoid:** Central `TokenCache` with `code3rd + profile + scope` key per D-19.

### Pitfall 4: Endpoint override scope creep

**What goes wrong:** Endpoints override full Java pipeline; spec complexity explodes.
**Why:** Temptation to mirror legacy per-endpoint auth in spec.
**How to avoid:** Enforce Groovy-only endpoint override (D-05); validate at spec parse time.

### Pitfall 5: Publishing without compile validation

**What goes wrong:** Bad script deployed; first production invoke fails.
**Why:** Compile deferred to first invoke.
**How to avoid:** Fail fast at `ConnectorRegistry.save()` with structured compile error (D-08).

### Pitfall 6: Stale OAuth tokens after credential rotation

**What goes wrong:** 401 from vendor until process restart.
**Why:** No eviction on republish.
**How to avoid:** `TokenCache.evictForConnector(code3rd)` on register/save (D-21).

### Pitfall 7: Wave 1 scope explosion

**What goes wrong:** Implementing all 28 profile-registry entries in Phase 1.
**Why:** profile-registry lists entire legacy inventory.
**How to avoid:** `legacy-auth-inventory.md` gates Java work to Wave 1 types only (D-01, D-02).

---

## Code Examples

### Existing: AuthEngine pipeline merge

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
    // ...
    private AuthOutcome runStep(AuthContext context, Map<String, Object> stepConfig) {
        String type = String.valueOf(stepConfig.get("type"));
        AuthProvider provider = providersByType.get(type);
        if (provider == null) {
            throw new IllegalStateException("No AuthProvider for type: " + type);
        }
        // ...
    }
```

### Existing: Orchestrator auth wiring (carry-forward extension point)

```66:97:api-connector-engine/src/main/java/com/suntek/apiconnector/engine/DefaultIntegrationOrchestrator.java
        AuthContext authContext = new AuthContext(
                request.connectorCode().value(),
                spec.baseUrl(),
                request.method().name(),
                request.path(),
                request.query() != null ? request.query() : Map.of(),
                request.body(),
                spec.auth(),
                credentials,
                Map.of());
        AuthOutcome authOutcome = authEngine.authenticate(authContext);

        Map<String, String> headers = new HashMap<>();
        // ... merge authOutcome into HTTP request
```

**Planner action:** After `authenticate()`, add `AuthContextSnapshot.from(authContext, authOutcome)` and pass through result.

### Existing: OAuth per-provider cache (to be replaced)

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

### Existing: Catalog auth annotation → spec map

```113:127:api-connector-spec/src/main/java/com/suntek/apiconnector/spec/catalog/CatalogConnectorScanner.java
    private static Map<String, Object> authMap(CatalogAuth auth) {
        if (auth == null) {
            return Map.of("type", "none");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", auth.type());
        putIfPresent(map, "accessKeyRef", auth.accessKeyRef());
        // ...
        return map;
    }
```

### Recommended: Groovy compile-on-publish (from Groovy JSR-223)

```java
// Pattern from Apache Groovy JSR-223 — compile once, eval many
// [CITED: https://github.com/apache/groovy — GroovyScriptEngineImpl.compile]
ScriptEngineManager manager = new ScriptEngineManager();
Compilable compilable = (Compilable) manager.getEngineByName("groovy");
CompiledScript compiled = compilable.compile(scriptSource);
// On invoke:
Bindings bindings = new SimpleBindings();
bindings.put("ctx", authContext);
AuthOutcome outcome = (AuthOutcome) compiled.eval(bindings);
```

### Recommended: AuthScript contract (new interface)

```java
// api-connector-auth — script entry contract validated at compile/publish time
@FunctionalInterface
public interface AuthScript {
    AuthOutcome apply(AuthContext ctx);
}
```

Groovy scripts should implement `AuthScript` (or define `AuthOutcome apply(AuthContext ctx)` method on a script class).

### Recommended: Java Catalog Wave 1 auth

```16:18:api-connector-connectors/src/main/java/com/suntek/apiconnector/connectors/idps/IdpsConnectorCatalog.java
@CatalogConnector(code3rd = "IDPS", baseUrl = "https://idps.example.com")
@CatalogAuth(type = "aksk_hmac_sha256_v1", accessKeyRef = "publicKey", secretKeyRef = "appSecret")
@CatalogResponse(successWhen = "$.success", dataPath = "$.obj", messagePath = "$.msg")
```

### Test resource Groovy script (Phase 1 validation, D-23)

Place under `api-connector-auth/src/test/resources/scripts/`:

```groovy
// demo_auth.groovy — implements AuthScript contract
import com.suntek.apiconnector.auth.context.*

AuthOutcome apply(AuthContext ctx) {
    new AuthOutcome(
        [Authorization: "Bearer test-token"],
        [:],
        null
    )
}
```

Unit test: compile at `@BeforeAll`, assert second `eval` does not call `Compilable.compile` again (mock/spy compile service).

---

## Package Legitimacy Audit

| Package | GroupId | Version | Repository | Maintenance | CVE posture | Verdict |
|---------|---------|---------|------------|-------------|-------------|---------|
| `groovy-jsr223` | `org.apache.groovy` | 4.0.32 | Maven Central | Apache Groovy PMC; active 4.0.x releases (4.0.32 May 2026) | Check NVD at pin time; no known blockers for embedding | **APPROVED** [CITED: groovy.apache.org] |
| `bcprov-jdk18on` | `org.bouncycastle` | 1.80 | Maven Central | BouncyCastle maintained | Already in production BOM | **APPROVED** [VERIFIED: BOM] |
| Spring Boot BOM | `org.springframework.boot` | 4.0.6 | Maven Central | VMware/Broadcom + community | Standard enterprise stack | **APPROVED** [VERIFIED] |

**Groovy 4 notes for JDK 21:**
- Groovy 4.0 supports JDK 8–17+ for runtime; project uses JDK 21 — compatible per Groovy release notes. [CITED: Groovy 4.0 release notes]
- Use `org.apache.groovy` coordinates exclusively (groupId changed from `org.codehaus.groovy` in 4.0). [CITED: Groovy 4.0 release notes]

**Do NOT add:** `org.codehaus.groovy:*` (legacy), Nashorn/Rhino (removed/deprecated on modern JDK). [VERIFIED: `.planning/research/STACK.md`]

---

## Validation Architecture

Nyquist validation enabled in `.planning/config.json` (`workflow.nyquist_validation: true`). Each AUTH requirement maps to automated verification runnable in CI via `.\mvnw-jdk21.ps1 clean verify`.

### Verification commands

| Scope | Command | When |
|-------|---------|------|
| Auth module unit tests | `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test` | Every auth task |
| Scripting module unit tests | `.\mvnw-jdk21.ps1 -pl api-connector-scripting -am test` | Script compile/cache tasks |
| Engine orchestrator tests | `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test` | Snapshot carry-forward |
| App integration (WireMock) | `.\mvnw-jdk21.ps1 -pl api-connector-app -am test` | End-to-end invoke |
| Full CI gate | `.\mvnw-jdk21.ps1 clean verify` | Phase completion |

### Per-requirement verification map

| Req | Acceptance focus | Test type | Test location (planned) | Pass signal |
|-----|------------------|-----------|-------------------------|-------------|
| **AUTH-01** | Built-in profile on connector endpoint | Unit + Catalog | `*AuthProviderTest.java`, `ProductionConnectorCatalogsTest` | Profile produces expected headers/query; Catalog `@CatalogAuth` resolves |
| **AUTH-02** | Groovy auth script attach | Unit | `GroovyAuthScriptProviderTest.java` | Script returns `AuthOutcome` with expected headers |
| **AUTH-03** | Compile-on-publish, no recompile per invoke | Unit | `ScriptCompileServiceTest.java` | `compile()` called once per source hash; second invoke uses cache (mock counter) |
| **AUTH-04** | Immutable auth context with tokens/signatures | Unit | `AuthContextSnapshotTest.java` | Snapshot contains `ext.accessToken` / `signatureBase` after OAuth/HMAC provider |
| **AUTH-05** | Snapshot available to mapping hook | Unit | `DefaultIntegrationOrchestratorTest.java` (extend) | `InvocationResult` (or carrier) exposes non-null snapshot; mapping stub can read `accessToken` |
| **AUTH-06** | Structured error on missing/misconfigured auth | Unit + Integration | `AuthEngineTest.java`, `InvokeIntegrationTest.java` | Missing provider → `{code: AUTH_PROFILE_MISSING, details: {profileType}}`; bad script → `AUTH_SCRIPT_COMPILE_ERROR` |

### Success criteria crosswalk (ROADMAP Phase 1)

| # | Success criterion | Verification |
|---|-------------------|--------------|
| 1 | Built-in profile assignable via config | Catalog connector + `@CatalogAuth` test |
| 2 | Second invoke uses cached compiled script | `ScriptCompileServiceTest` compile-count assertion |
| 3 | AuthContext readable without HTTP | `AuthContextSnapshotTest` pure unit |
| 4 | Missing auth → structured error | `InvokeIntegrationTest` expects JSON error code |
| 5 | OAuth/HMAC signed outbound request | WireMock captures `Authorization` / query digest headers |
| 6 | Legacy auth inventory doc | File exists: `docs/legacy-auth-inventory.md` with ≥ Wave 1 rows |

### Walking skeleton (MVP first slice)

Recommended first vertical slice for Phase 1 plans:

1. Add `api-connector-scripting` with compile cache + one test script
2. Add `GroovyAuthScriptProvider` + `DEMO_GROOVY` catalog connector (or test-only spec)
3. Refactor one OAuth provider to `TokenCache`
4. Add `AuthContextSnapshot` + orchestrator carry-forward
5. Extend `ApiErrorResponse.details` + `AUTH_PROFILE_MISSING`
6. Draft `docs/legacy-auth-inventory.md` with Wave 1 rows

This delivers AUTH-02..06 structure before all Wave 1 Java L2 profiles land.

---

## Implementation Recommendations

Prescriptive guidance for the planner — ordered by dependency.

### 1. Create `api-connector-scripting` module (D-06)

- Add module to root `pom.xml` and BOM
- Dependencies: `groovy-jsr223` only (minimal surface)
- Classes: `ScriptCompileService`, `CompiledScriptCache` (ConcurrentHashMap keyed by SHA-256)
- API: `CompiledScript compile(String source, String label)`, `void evict(String hash)`
- Unit tests: compile error includes line; cache hit skips recompile

### 2. Extend spec models (D-05, D-09)

- `EndpointSpec`: add optional `Map<String, Object> authOverride`
- `ConnectorSpecParser` + `CatalogConnectorScanner`: parse `auth.script` field
- Validation: `authOverride.type` must equal `groovy_auth_script` if present
- Document YAML shape in `docs/profile-registry.md` (reference only — inventory is separate doc)

### 3. Implement `GroovyAuthScriptProvider` (D-07)

- `profileType()` → `groovy_auth_script`
- Read script source from `stepConfig.script` or `stepConfig.scriptRef`
- Invoke: `compiledScript.eval(bindings)` with `ctx` → `AuthContext`
- Register as Spring `@Bean` in `IntegrationEngineConfiguration`

### 4. Implement `TokenCache` and refactor OAuth providers (D-19–D-21)

- New `TokenCache` bean in auth module
- Inject into `OAuth2ClientCredentialsAuthProvider`, `OAuth2TokenInQueryAuthProvider`
- Key: `record TokenCacheKey(String code3rd, String profileType, String scope)`
- Populate `AuthContext.ext` with `accessToken`, `tokenExpiresAt`, `oauthRawResponse`
- Publish listener calls `tokenCache.evict(code3rd)`

### 5. Add `AuthContextSnapshot` + pipeline carry-forward (D-10–D-12)

- New immutable record in domain or auth context package
- Factory: `AuthContextSnapshot.from(AuthContext input, AuthOutcome outcome, List<String> profileTypes)`
- Extend `InvocationResult` with optional `authSnapshot()` and `authOutcome()` OR introduce `InvocationContext` wrapper
- Update `DefaultIntegrationOrchestrator.invoke()` and `invokeStream()`

### 6. Resolve auth config with endpoint override (D-05)

- New `AuthConfigResolver.resolve(ConnectorSpec spec, EndpointSpec endpoint)` in engine
- Logic: endpoint `authOverride` replaces connector auth when non-null; else `spec.auth()`
- Pass resolved config into `AuthContext.authConfig()`
- Wire `endpointId` from `InvocationRequest` through orchestrator (already available on request)

### 7. Structured auth errors (D-17, D-18, AUTH-06)

- `AuthException extends RuntimeException` with `AuthErrorCode code` and `Map<String, Object> details`
- Replace `IllegalStateException` throws in `AuthEngine`, providers, compile service
- Extend `ApiErrorResponse` with `details` field
- Update `RuntimeApiExceptionHandler` to map `AuthException` → appropriate HTTP status (502 for upstream, 400 for config/compile)

### 8. Publish-time compile hook (D-08)

- `ConnectorRegistry` publish listener or decorate `save()`/`register()`:
  - Scan spec for `groovy_auth_script` at connector and endpoint level
  - Compile each unique script source
  - On failure: throw `AuthException(AUTH_SCRIPT_COMPILE_ERROR)` — bootstrap should log and skip or fail startup (planner choice: **fail fast for catalog connectors**)

### 9. Wave 1 Java built-ins (D-01, D-02, D-04)

**Implement after inventory confirms need:**

| Profile | Priority | Notes |
|---------|----------|-------|
| `oauth2_password` | High if Wave 1 Keda/科达 | Login + password grant pattern |
| `bearer_from_login` | High if Wave 1 parking vendors | Login POST → Bearer |
| `sm3_header_sign_v1` | Conditional | Only if inventory shows Wave 1 国密 vendor |
| `sm4_body_encrypt_v1` | **Defer** | Transform pipeline per D-16 — auth signs headers only in Phase 1 |

**Already done for Wave 1 catalogs:** `aksk_hmac_sha256_v1`, `api_key_query`, `gaode_traffic_hmac_v1`, `oauth2_token_in_query`

### 10. Deliver `docs/legacy-auth-inventory.md` (D-03)

- Run audit script against system-thirdpart (read-only)
- Minimum rows: all Wave 1 vendors + all currently implemented catalog connectors
- Mark Hikvision, 大华, 讯飞 as Groovy/L3 path
- Link each row to profile-registry ID

### 11. Test assets (D-22, D-23)

- `api-connector-auth/src/test/resources/scripts/*.groovy` — sample auth scripts
- `api-connector-auth/src/test/resources/connectors/groovy-demo.yaml` — classpath spec with inline script
- Optional: `DemoGroovyAuthConnectorCatalog.java` for integration visibility
- WireMock test: verify outbound request headers after Groovy auth

### Module touch list (expected plan tasks)

| Module | Changes |
|--------|---------|
| `api-connector-dependencies` | Groovy BOM entry |
| `api-connector-scripting` | **NEW** |
| `api-connector-domain` | `AuthContextSnapshot`, extend `InvocationResult` |
| `api-connector-auth` | TokenCache, GroovyAuthScriptProvider, new L2 profiles, AuthException |
| `api-connector-spec` | EndpointSpec.authOverride, parser validation |
| `api-connector-engine` | AuthConfigResolver, orchestrator carry-forward, publish hooks |
| `api-connector-api` | ApiErrorResponse.details, exception handler |
| `api-connector-connectors` | Wave 1 catalog updates as inventory confirms |
| `docs/legacy-auth-inventory.md` | **NEW** deliverable |

### Open questions for planner (not blockers)

1. **国密 Wave 1 presence:** Audit may show 数运/CETC not in Wave 1 — skip SM profiles. [ASSUMED: conditional per D-04]
2. **InvocationResult vs separate carrier:** Prefer extending `InvocationResult` for minimal API churn vs new wrapper type. [ASSUMED: extend result]
3. **Bootstrap compile failure policy:** Fail startup vs register without script. [RECOMMEND: fail fast for managed catalog connectors]

---

## Sources

### Primary (HIGH confidence)
- `.planning/phases/01-auth-plugin-architecture/01-CONTEXT.md` — locked decisions
- `.planning/REQUIREMENTS.md` — AUTH-01..06
- `docs/adr/002-auth-and-transform.md`, `docs/profile-registry.md`
- `.planning/codebase/ARCHITECTURE.md`, `INTEGRATIONS.md`, `STACK.md`
- `.planning/research/SUMMARY.md`, `PITFALLS.md`, `ARCHITECTURE.md`, `STACK.md`
- `api-connector-auth/` — AuthEngine, providers, context types
- `api-connector-engine/DefaultIntegrationOrchestrator.java`
- `/apache/groovy` (Context7) — JSR-223 compile cache

### Secondary (MEDIUM confidence)
- `system-thirdpart` filesystem scan — 50+ controllers, 325 client files for inventory scope
- Groovy 4.0.32 download page — version pin

---

*Phase: 01-auth-plugin-architecture*
*Research completed: 2026-06-17*
*Ready for planning: yes*
