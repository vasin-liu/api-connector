# Phase 2: Data Mapping Engine - Research

**Researched:** 2026-06-17
**Domain:** Declarative JSON field mapping + Groovy scripts + Transform SPI (SM4) + JDBC publish validation
**Confidence:** HIGH

---

## User Constraints

### Locked Decisions (from `02-CONTEXT.md` — NON-NEGOTIABLE)

#### Declarative Mapping DSL (MAP-01, MAP-02)
- **D-01:** Mapping rules follow **connector default + endpoint override** (endpoint wins), symmetric with Phase 1 auth (D-05).
- **D-02:** Syntax is **JSONPath source/target + enumerated transform ops** (`rename`, `coerce`, `nest`, `array_map`, `set`).
- **D-03:** Organize as three blocks: **`mapping.request`**, **`mapping.response`**, **`mapping.error`**.
- **D-04:** **Validate on publish/load** — reject invalid JSONPath or unknown transforms.
- **D-05:** **array_map** operator for per-element array transforms.
- **D-06:** **Lenient missing fields** (omit/null); **strict type coercion** failures raise structured mapping errors.
- **D-07:** Rules execute **sequentially** in YAML list order (later rules override same target).
- **D-08:** **set** operator writes constants/defaults to target paths without a source.

#### Groovy Mapping Boundary (MAP-03)
- **D-09:** Profile type **`groovy_mapping_script`**, mirror auth: connector default + **endpoint Groovy-only override** of declarative rules.
- **D-10:** **Per-direction mutual exclusion** — each of request/response/error is either declarative rules OR a script, not both.
- **D-11:** Script bindings: **rich context** — body, `AuthContextSnapshot`, direction, endpoint metadata (AUTH-05).
- **D-12:** **`MappingScript` functional interface** — `apply(MappingContext) → Object` (Map/List), mirroring `AuthScript`.
- **D-13:** **Per-direction scripts** — `requestScript` / `responseScript` / `errorScript` fields; compile separately on publish.
- **D-14:** Runtime failures throw structured **`MappingException`** with platform error code + details (mirror `AuthException`).

#### Error Response Mapping (MAP-04)
- **D-15:** Trigger error mapping on **HTTP non-2xx and business failure** (successWhen not met).
- **D-16:** Map to **legacy compat business error JSON** (`LegacySuntekResult` / `com.suntek.common.core.base.Result` shape).
- **D-17:** Error mapping uses **connector default + endpoint override**.
- **D-18:** When **no mapping.error** configured, **passthrough vendor error body** unchanged.
- **D-19:** **HTTP 200 + business failure** gated by **successWhen** before applying error mapping.

#### Transform Pipeline Scope
- **D-20:** Phase 2 delivers **TransformStep SPI + SM4 implementation**; other transform types stubbed; **business envelope deferred**.
- **D-21:** Request order: **JSON mapping → transform (SM4) → auth signing**; response reverses (MAP-06 verified in Phase 3).
- **D-22:** Reuse existing **`ConnectorSpec.transform[]`** with typed steps (e.g. `sm4_encrypt`); JSON mapping uses new **`mapping`** blocks (separate from transform).

#### Passthrough Mode (MAP-07)
- **D-23:** **Default passthrough** — no `mapping.*` blocks means skip MappingEngine (zero overhead).
- **D-24:** Passthrough is **implicit** (no `mapping.mode` field required).
- **D-25:** Passthrough skips **JSON mapping only**; **`transform[]` still runs** if configured.
- **D-26:** **Orchestrator short-circuit** — do not invoke MappingEngine bean when no mapping config (true zero overhead; Phase 3 wiring).

#### JDBC Persistence (MAP-05)
- **D-27:** Store mapping inside **existing ConnectorSpec JSON blob** in JDBC (no new mapping_rules table).
- **D-28:** **Catalog/YAML and JDBC coexist** — JDBC publish overrides classpath for runtime; Catalog remains for tests and builtins.
- **D-29:** Extend **`ConnectorPublishListener`** — compile Groovy mapping scripts, validate declarative rules, update in-memory registry (no restart).
- **D-30:** **Reject invalid mapping on publish/sync** path (admin save validation deferred to Phase 4 API, but publish path enforces now).

### Deferred Ideas (OUT OF SCOPE)

- **Business envelope transform** — Wave 2+ (D-20)
- **MAP-06 pipeline ordering integration tests** — Phase 3
- **Admin UI mapping editor** — Phase 4
- **invokeStream mapping snapshot exposure** — Phase 2/3 when streaming mapping ships
- **JOLT chain import (MAP-V2-01)** — v2 backlog

---

## Standard Stack

### Core (existing — verified in repo)

| Technology | Version | Purpose | Confidence |
|------------|---------|---------|------------|
| Java | 21 | Runtime, records, virtual threads | [VERIFIED: root `pom.xml`] |
| Spring Boot | 4.0.6 | Bean wiring for MappingEngine, publish hooks | [VERIFIED: BOM] |
| Jackson `databind` | via Spring BOM | JSON IR (`JsonNode`) for mapping read/write | [VERIFIED: engine, persistence] |
| Jayway JsonPath | **2.10.0** | Path read + `DocumentContext.set()` for writes | [VERIFIED: `api-connector-dependencies/pom.xml`, `ResponseEvaluator.java`] |
| Apache Groovy `groovy-jsr223` | **4.0.32** | Mapping scripts; shared `ScriptCompileService` | [VERIFIED: Phase 1 `api-connector-scripting`] |
| BouncyCastle `bcprov-jdk18on` | **1.80** | SM4 encrypt/decrypt (CETC legacy parity) | [VERIFIED: `api-connector-auth/pom.xml`] |
| JUnit 5 + AssertJ | via Surefire | Rule unit tests, publish validation tests | [VERIFIED] |

### New for Phase 2

| Technology | Version | Purpose | Confidence |
|------------|---------|---------|------------|
| `api-connector-mapping` module | NEW | MappingEngine, rule executor, TransformStep SPI, Groovy adapter | [ASSUMED: per `.planning/research/ARCHITECTURE.md`] |
| Jayway JsonPath (moved/shared) | 2.10.0 | Declarative path ops — **add dependency to mapping module** | [VERIFIED: already in BOM; currently only on engine] |

**Do NOT add for v1:**

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| JOLT (`com.bazaarvoice.jolt`) | Deferred to MAP-V2-01; different mental model (chain transforms vs sequential rules) | Declarative DSL + Groovy |
| JSONata | No team mandate; another expression language to validate/sandbox | JsonPath + Groovy |
| MapStruct | Compile-time DTO mapping; not runtime-configurable | Jackson `JsonNode` pipeline |
| Custom JSONPath parser | Jayway already in BOM and used by `ResponseEvaluator` | Jayway + thin rule layer |

### Module dependency graph (Phase 2 additions)

```
api-connector-domain      ← MappingContext, MappingDirection, MappingException (new)
api-connector-spec        ← MappingSpec model, parser extensions, publish-time schema validation
api-connector-scripting   ← reuse ScriptCompileService (no changes expected)
api-connector-mapping     ← NEW: MappingEngine, DeclarativeRuleExecutor, TransformPipeline, GroovyMappingProvider
api-connector-engine      ← MappingConfigResolver, extend ConnectorPublishListener, future orchestrator hooks (Phase 3)
api-connector-persistence ← no schema change; mapping lives in SPEC_JSON blob (D-27)
api-connector-api         ← MappingException → RuntimeApiExceptionHandler (mirror auth)
```

**Hexagonal rule:** `api-connector-mapping` depends on `domain`, `spec`, `scripting` only — **not** on `engine` or Spring Web. Engine wires beans.

---

## Architecture Patterns

### System flow (Phase 2 build target; orchestrator wiring Phase 3)

```
Publish / JDBC sync
  → ConnectorSpecParser.parse() + MappingSpecValidator.validate()
  → ConnectorRegistry.save()
  → ConnectorPublishListener.onPublish()
       → compile groovy_mapping_script (connector + endpoint overrides)
       → validate declarative rules (JSONPath syntax, known ops, mutual exclusion)
       → reject publish on failure (D-30)

Runtime (Phase 3 will wire; Phase 2 delivers engine API)
  → resolve MappingConfig (connector default + endpoint override)
  → if no mapping blocks → SKIP (D-23, D-26) — no MappingEngine bean call
  → mapRequest (declarative rules OR requestScript)
  → transform[] pipeline (SM4 encrypt on request)
  → auth (signs final body — MAP-06)
  → HTTP
  → evaluate successWhen (ResponseEvaluator — existing)
  → if failure → mapError OR passthrough (D-15, D-18, D-19)
  → else → transform[] reverse (SM4 decrypt)
  → mapResponse
```

**Current gap:** `DefaultIntegrationOrchestrator` runs Auth → HTTP only; `ConnectorSpec.transform()` parsed but never executed. [VERIFIED: `DefaultIntegrationOrchestrator.java`, `.planning/codebase/CONCERNS.md`]

### Pattern 1: Jayway JsonPath + Jackson JsonNode IR (declarative mapping)

**Recommendation:** Use **Jayway for path operations**, not a full third-party mapping framework.

| Concern | Jayway JsonPath | Custom rule executor | JOLT |
|---------|-----------------|----------------------|------|
| Read nested fields | `JsonPath.read(document, path)` | — | shift |
| Write/rename/nest | `JsonPath.parse(json).set(path, value)` | Rule ops orchestrate multiple sets | shift/default |
| Type coercion | Not built-in | **Custom** `coerce` op (string↔number↔date) | Limited |
| array_map | Filter/read arrays; per-element needs loop | **Custom** `array_map` with nested rules | chain |
| Publish validation | `JsonPath.compile(path)` throws on invalid | Validate op names + required fields | Separate JOLT spec validator |
| Consistency with `successWhen` | Same library as `ResponseEvaluator` | — | Different syntax |

**Implementation approach:**

1. Parse input body once → `JsonNode` root (Jackson) or Jayway `DocumentContext`.
2. For each rule in order (D-07): read `source` path → apply op → write `target` path.
3. Missing source path → skip rule or write null (D-06 lenient).
4. Coerce failure → `MappingException` with path + expected type (D-06 strict).

**Path normalization:** Reuse `ResponseEvaluator.normalizePath()` pattern — bare `field` → `$.field`. [VERIFIED: `ResponseEvaluator.java` lines 80–85]

**Why not pure JsonPath for everything:** `coerce`, `nest`, `array_map`, and `set` are **domain-specific ops** — a thin `DeclarativeRuleExecutor` over Jayway is the right split (80% library, 20% custom). [CITED: Jayway README — `parse().set()` for mutations]

### Pattern 2: Groovy mapping scripts (mirror Phase 1 auth)

**What:** `groovy_mapping_script` profile type implemented as `GroovyMappingScriptProvider` (not AuthProvider).

**Config shape (connector-level declarative + per-direction scripts):**

```yaml
mapping:
  request:
    rules:
      - op: rename
        source: $.oldField
        target: $.newField
      - op: coerce
        source: $.count
        target: $.count
        type: number
  response:
    script: |
      import com.suntek.apiconnector.mapping.spi.MappingScript
      // or return Map directly
      def body = ctx.bodyAsMap()
      [code: '200', success: true, data: body.result]
  error:
    rules:
      - op: set
        target: $.code
        value: "500"
      - op: rename
        source: $.vendorMsg
        target: $.message
```

**Endpoint override (Groovy-only for scripts, full block override for rules — mirror auth D-05):**

```yaml
endpoints:
  - id: specialTransform
    mappingOverride:
      request:
        script: |
          // endpoint-specific request transform
          ctx.bodyAsMap()
```

**Bindings (`MappingContext` — D-11):**

| Binding | Type | Source |
|---------|------|--------|
| `ctx` | `MappingContext` | Primary entry |
| `ctx.body()` | `String` | Raw JSON body |
| `ctx.bodyAsMap()` | `Map` | Parsed body helper |
| `ctx.authSnapshot()` | `AuthContextSnapshot` | From Phase 1 pipeline |
| `ctx.direction()` | `MappingDirection` | REQUEST / RESPONSE / ERROR |
| `ctx.endpoint()` | `EndpointMeta` | id, method, path |
| `ctx.code3rd()` | `String` | Connector id |

**Compile-on-publish:** Reuse `ScriptCompileService.compile(source, label)` with label `code3rd:mapping:request` etc. Contract validation stub-eval with `MappingContext` stub (mirror `ConnectorPublishListener.validateAuthScriptContract`). [VERIFIED: `ConnectorPublishListener.java`]

**Mutual exclusion (D-10):** Parser rejects `mapping.request` containing both `rules` and `script`.

### Pattern 3: Transform pipeline SPI (D-20, D-22)

**Separate from JSON mapping** — `ConnectorSpec.transform[]` already exists as `List<Map<String, Object>>`. [VERIFIED: `ConnectorSpec.java`]

**Step shape:**

```yaml
transform:
  - type: sm4_encrypt
    direction: request    # request | response
    keyRef: appSecret     # credential ref, not inline secret
    mode: ECB             # match legacy CETC: SM4/ECB/PKCS5Padding
    outputEncoding: base64
  - type: business_envelope
    enabled: false        # stub — throws UnsupportedTransformException or no-op registry
```

**SPI:**

```java
public interface TransformStep {
    String type();
    String apply(TransformContext ctx);  // body in → body out
}
```

**SM4 implementation notes (legacy parity):**

- Legacy `CetcUtils.sm4Encrypt` uses `SM4/ECB/PKCS5Padding`, key from UTF-8 bytes, output Base64. [VERIFIED: `system-thirdpart/.../CetcUtils.java`]
- Register `BouncyCastleProvider` once (static init in transform module or reuse auth pattern).
- Key resolution via credential ref (`appSecret`) — **never** embed secrets in mapping/transform config.
- Wave 1 does not require SM4 in production (`docs/legacy-auth-inventory.md` — CETC is Wave 2+), but Phase 2 delivers SPI + CETC-compatible step for future catalog.

**Stub types:** `business_envelope`, `sm4_decrypt` (response) — register bean that fails at publish if `enabled: true`, or no-op with WARN log for unknown types during Phase 2.

### Pattern 4: Error response mapping (MAP-04)

**Legacy target shape** (already modeled in api-connector):

```json
{
  "code": "500",
  "message": "vendor error text",
  "data": null,
  "success": false
}
```

[VERIFIED: `LegacySuntekResult.java` — mirrors `com.suntek.common.core.base.Result`]

**Trigger matrix (D-15, D-19):**

| Condition | Error mapping? | Notes |
|-----------|----------------|-------|
| HTTP 4xx/5xx | Yes, if `mapping.error` configured | Platform HTTP status remains orchestrator concern (D-16) |
| HTTP 200 + `successWhen` false | Yes | Use existing `ResponseEvaluator` first |
| HTTP 200 + success | No — use `mapping.response` | Normal path |
| Failure + no `mapping.error` | Passthrough `rawBody` (D-18) | Legacy compat filter may still wrap via `LegacyCompatResponseFormatter` |

**Legacy thirdpart pattern:** Controllers call `Result.fail(code, message)` mapping vendor codes to platform `Result`. Phase 2 replaces per-controller logic with declarative `mapping.error` rules producing the same JSON envelope before API layer formatting.

### Pattern 5: MappingConfigResolver (mirror AuthConfigResolver)

```java
public final class MappingConfigResolver {
    public static ResolvedMapping resolve(ConnectorSpec spec, EndpointSpec endpoint) {
        // endpoint.mappingOverride wins over connector.mapping
        // per-direction: rules OR script, never both
    }
    public static boolean hasAnyMapping(ResolvedMapping m) {
        // used for orchestrator short-circuit (D-26)
    }
}
```

[VERIFIED pattern: `AuthConfigResolver.java` — endpoint override → connector default]

### Pattern 6: Passthrough fast path (MAP-07)

**Not a no-op method** — orchestrator checks `MappingConfigResolver.hasAnyMapping()` **before** injecting `MappingEngine`. When false:

- No `JsonNode` parse
- No script eval
- Body passes unchanged to transform/auth

Transform pipeline still runs if `transform[]` non-empty (D-25). [VERIFIED decision in `02-CONTEXT.md`]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Reason |
|---------|-------------|-------------|--------|
| JSONPath read/write | Regex on JSON strings | Jayway JsonPath 2.10.0 | Already in BOM; `ResponseEvaluator` precedent; `set()` API for mutations [CITED: json-path README] |
| Groovy compile cache | New cache per module | `ScriptCompileService` + SHA-256 hash | Phase 1 proven; AUTH-03 tests |
| SM4 block cipher | Custom crypto | BouncyCastle `SM4/ECB/PKCS5Padding` | ADR-002; legacy CETC parity |
| Publish-time script validation | Runtime-only checks | Extend `ConnectorPublishListener` | Same hook as auth; fails before registry update |
| Legacy error JSON envelope | Per-vendor Java | `mapping.error` rules → `LegacySuntekResult` shape | Drop-in compat requirement |
| JDBC mapping storage | New `IT_MAPPING_RULES` table | `SPEC_JSON` blob via `JdbcConnectorConfigStore` | D-27; parser already round-trips `ConnectorSpec` |
| OAuth/token in mapping scripts | Script fetches tokens | `AuthContextSnapshot.ext` | Pitfall 3 — auth context threading |
| Full JSON query language | JSONata/JQ port | Enumerated ops + Groovy escape hatch | Bounded validation surface (D-04) |

**Safe to hand-roll (narrow scope):**

- `DeclarativeRuleExecutor` — sequential op dispatch (~200–400 LOC)
- `MappingSpecValidator` — JSONPath compile + schema checks on publish
- `CoerceHelper` — string/number/ISO-8601 date coercion with strict errors
- `TransformStepRegistry` — Spring `Map<String, TransformStep>` bean wiring

---

## Common Pitfalls

### Pitfall 1: Signing body before mapping completes (MAP-06 — Phase 3)

**Risk:** HMAC signs pre-mapping body; vendor rejects request.

**Prevention:** Document and test order D-21: `mapRequest → transform → auth`. Phase 2 builds engines separately; Phase 3 integration test is the gate.

**Phase 2 action:** Export `MappingEngine` and `TransformPipeline` as distinct beans with explicit method signatures so orchestrator cannot accidentally reorder silently.

### Pitfall 2: Passthrough that still parses JSON

**Risk:** "Passthrough" calls `MappingEngine.mapRequest(body)` identity function — allocates `JsonNode` on every invoke.

**Prevention:** `hasAnyMapping()` guard in orchestrator (D-26); unit test asserts `MappingEngine` mock never called when spec has no mapping block.

### Pitfall 3: Groovy mapping without compile cache

**Risk:** Same as auth — per-request compile crushes P99.

**Prevention:** Reuse `ScriptCompileService`; extend `ConnectorPublishListener` to pre-compile all mapping scripts on publish. Mirror `InvokeIntegrationTest.groovyAuthScriptCompileOnceAcrossTwoInvokes`.

### Pitfall 4: Mutual exclusion not enforced at parse time

**Risk:** Spec has both `rules` and `script` on `mapping.request`; runtime behavior ambiguous.

**Prevention:** `ConnectorSpecParser` or `MappingSpecValidator` throws on load (D-10, D-04) — fail publish, not runtime lottery.

### Pitfall 5: Error mapping on successful responses

**Risk:** `mapping.error` applied to HTTP 200 business-success responses.

**Prevention:** Gate on `ResponseEvaluator.isSuccess()` AND HTTP status (D-19). Error mapping runs only when `!success || httpStatus >= 400`.

### Pitfall 6: SM4 key handling in spec

**Risk:** Operator puts raw key in `transform[].key` field; leaks in logs/DB.

**Prevention:** `keyRef` only → resolve from `ConnectorRegistry.credentials()` at runtime; redact in audit logs.

### Pitfall 7: JsonPath `set` on missing parent path

**Risk:** Jayway `set` may fail or create unexpected structure for `nest` op.

**Prevention:** `nest` op explicitly creates parent objects/arrays before leaf `set`; unit test nested path creation.

### Pitfall 8: JDBC spec round-trip drops mapping

**Risk:** `ConnectorSpecParser.toConnectorMap()` omits new `mapping` field — DB save loses rules.

**Prevention:** Extend parser `parse()` + `toConnectorMap()` + `endpointToMap()` symmetrically; persistence round-trip test via `JdbcConnectorConfigStore` test slice.

### Pitfall 9: Confusing `transform[]` with `mapping.*`

**Risk:** Operators put field renames in `transform[]` or SM4 in `mapping.request`.

**Prevention:** Document in spec validator error messages; ADR-002 separation. Transform = crypto/envelope; mapping = JSON shape.

---

## Phase Structure Recommendation

Recommended plan waves (vertical slices, auth-phase symmetry):

| Wave | Plans | Delivers | Requirements |
|------|-------|----------|--------------|
| **1** | 02-01 | `api-connector-mapping` module skeleton; `MappingSpec` model; `ConnectorSpecParser` extensions; `MappingSpecValidator` (publish reject) | MAP-01 (schema), MAP-05 (parse round-trip) |
| **2** | 02-02 | `DeclarativeRuleExecutor` — rename, set, coerce, nest; unit tests for type coercion | MAP-01 |
| **3** | 02-03 | `array_map` nested rules; `MappingEngine` facade; `MappingConfigResolver` | MAP-02 |
| **4** | 02-04 | `MappingScript` SPI + `GroovyMappingScriptProvider`; extend `ConnectorPublishListener`; `MappingException` + handler | MAP-03, MAP-05 |
| **5** | 02-05 | `mapping.error` + legacy shape tests; passthrough `hasAnyMapping()` API | MAP-04, MAP-07 |
| **6** | 02-06 | `TransformStep` SPI + `Sm4EncryptTransformStep`; stub registry; JDBC publish integration test | D-20, MAP-05 |

**Walking skeleton (first slice):**

1. Add `mapping.request.rules` to test connector YAML in `api-connector-app/src/test/resources`
2. `MappingSpecValidator` rejects bad JSONPath on `ConnectorRegistry.save()`
3. One rename rule unit test: `{"a":1}` → `{"b":1}`
4. Prove passthrough: spec without `mapping` → `hasAnyMapping() == false`

**Defer to Phase 3:** Orchestrator wiring, HMAC-after-mapping test (MAP-06), `invokeStream` body mapping.

**Defer to Phase 4:** Admin API dry-run validation (ADMIN-04) — Phase 2 satisfies ROADMAP SC#6 via publish path only.

---

## Validation Architecture (for Nyquist)

Nyquist validation enabled: `.planning/config.json` → `workflow.nyquist_validation: true`. Each MAP requirement maps to automated verification via `.\mvnw-jdk21.ps1 clean verify`.

### Test infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Maven Surefire |
| **Quick run** | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test` |
| **Publish hook tests** | `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=ConnectorPublishListenerTest` |
| **Full suite** | `.\mvnw-jdk21.ps1 clean verify` |
| **Estimated runtime** | ~90s (mapping module only); ~150s full |

### Verification commands

| Scope | Command | When |
|-------|---------|------|
| Mapping module unit | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test` | Every mapping task |
| Spec parser round-trip | `.\mvnw-jdk21.ps1 -pl api-connector-spec -am test` | Parser/validator tasks |
| Publish listener | `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=ConnectorPublishListenerTest` | Groovy compile + validation |
| Persistence round-trip | `.\mvnw-jdk21.ps1 -pl api-connector-persistence -am test` | JDBC spec with mapping blob |
| Full CI gate | `.\mvnw-jdk21.ps1 clean verify` | Phase completion |

### Per-requirement verification map

| Req | Acceptance focus | Test type | Test location (planned) | Pass signal |
|-----|------------------|-----------|-------------------------|-------------|
| **MAP-01** | Declarative request field mapping with coercion | Unit | `DeclarativeRuleExecutorTest.java` | rename + coerce string→number; coerce failure throws `MappingException` |
| **MAP-02** | Nested objects and `array_map` | Unit | `ArrayMapRuleTest.java` | `[{a:1},{a:2}]` maps per nested rules |
| **MAP-03** | Groovy mapping script | Unit | `GroovyMappingScriptProviderTest.java` | Script returns Map; uses `ctx.authSnapshot()` |
| **MAP-04** | Error JSON → legacy shape | Unit | `ErrorMappingEngineTest.java` | Vendor `{errCode, errMsg}` → `{code, message, success:false}` |
| **MAP-05** | JDBC persist + publish reload | Integration | `MappingPublishIntegrationTest.java` | Save spec with mapping to H2 → `reloadFromStore()` → engine resolves rules without restart |
| **MAP-07** | Passthrough zero overhead | Unit | `MappingConfigResolverTest.java` | No mapping block → `hasAnyMapping()==false`; orchestrator test in Phase 3 |

### Success criteria crosswalk (ROADMAP Phase 2)

| # | Success criterion | Verification |
|---|-------------------|--------------|
| 1 | Declarative rename/nest/coerce in unit tests | `DeclarativeRuleExecutorTest` |
| 2 | Groovy script transforms vendor JSON to legacy shape | `GroovyMappingScriptProviderTest` + sample vendor fixture |
| 3 | Error JSON maps to legacy business error | `ErrorMappingEngineTest` vs `LegacySuntekResult` |
| 4 | Mapping persists in DB, applies after publish | `JdbcConnectorConfigStore` + `ConnectorConfigSyncService` integration |
| 5 | Passthrough skips mapping | `MappingConfigResolverTest.hasAnyMapping` |
| 6 | Invalid path/transform rejected on publish | `ConnectorPublishListenerTest` + `MappingSpecValidatorTest` |

### Wave 0 requirements (test stubs before feature tasks)

- [ ] `api-connector-mapping` module in root `pom.xml` + BOM entry
- [ ] `MappingSpecValidatorTest.java` — rejects invalid JSONPath
- [ ] `DeclarativeRuleExecutorTest.java` — rename skeleton
- [ ] `src/test/resources/mapping/demo-rename.yaml` — fixture spec
- [ ] Extend `ConnectorPublishListenerTest` — mapping script compile case

### Manual-only verifications

| Behavior | Requirement | Why Manual | Instructions |
|----------|-------------|------------|--------------|
| CETC SM4 interop | SM4 transform | No Wave 1 vendor sandbox in CI | Compare output with `CetcUtils.sm4Encrypt` golden vector |
| Legacy error parity per vendor | MAP-04 | Golden files in Phase 6 | Spot-check one vendor from system-thirdpart audit |

---

## Codebase Integration Points

### Files to extend (verified)

| File | Change |
|------|--------|
| `api-connector-spec/.../ConnectorSpec.java` | Add `mapping` field (connector-level `MappingSpec`) |
| `api-connector-spec/.../EndpointSpec.java` | Add `mappingOverride` (mirror `authOverride`) |
| `api-connector-spec/.../ConnectorSpecParser.java` | Parse/serialize `mapping`, `mappingOverride`; validate mutual exclusion |
| `api-connector-engine/.../ConnectorPublishListener.java` | Scan `groovy_mapping_script`; validate declarative rules; compile mapping scripts |
| `api-connector-engine/.../ConnectorRegistry.java` | No change — already calls `publishListener.onPublish()` |
| `api-connector-engine/.../config/IntegrationEngineConfiguration.java` | Register `MappingEngine`, `TransformPipeline`, `TransformStep` beans |
| `api-connector-persistence/.../JdbcConnectorConfigStore.java` | No schema change — mapping in `SPEC_JSON` automatically if parser handles field |
| `api-connector-api/.../RuntimeApiExceptionHandler.java` | Add `@ExceptionHandler(MappingException.class)` |
| `pom.xml` (root) | Add `api-connector-mapping` module |
| `api-connector-dependencies/pom.xml` | BOM entry for mapping module |

### Reusable Phase 1 assets

| Asset | Location | Mapping use |
|-------|----------|-------------|
| `ScriptCompileService` | `api-connector-scripting` | Compile `requestScript`/`responseScript`/`errorScript` |
| `CompiledScriptCache` | `api-connector-scripting` | Content-hash cache shared with auth |
| `AuthContextSnapshot` | `api-connector-domain` | Groovy binding `ctx.authSnapshot()` (AUTH-05) |
| `AuthConfigResolver` pattern | `api-connector-engine` | Template for `MappingConfigResolver` |
| `ConnectorPublishListener` | `api-connector-engine` | Extend, don't duplicate publish hook |
| `ResponseEvaluator` | `api-connector-engine` | `successWhen` gate for error mapping trigger |
| `LegacySuntekResult` | `api-connector-api` | Target error JSON contract for MAP-04 tests |
| `ConnectorConfigSyncService` | `api-connector-persistence` | Reload path already calls `registry.save()` → publish listener |

### New domain types (recommended)

```java
// api-connector-domain
public record MappingContext(
    String code3rd,
    MappingDirection direction,
    String rawBody,
    AuthContextSnapshot authSnapshot,
    EndpointMeta endpoint) { ... }

public enum MappingDirection { REQUEST, RESPONSE, ERROR }

// api-connector-mapping
public interface MappingEngine {
    String mapRequest(MappingContext ctx, ResolvedMapping config);
    String mapResponse(MappingContext ctx, ResolvedMapping config);
    String mapError(MappingContext ctx, ResolvedMapping config, ErrorTrigger trigger);
}

public interface MappingScript {
    Object apply(MappingContext ctx);
}
```

### Spec YAML example (canonical for planners)

```yaml
code3rd: DEMO_MAP
baseUrl: https://vendor.example.com
auth:
  type: aksk_hmac_sha256_v1
mapping:
  request:
    rules:
      - op: rename
        source: $.clientId
        target: $.app_id
      - op: set
        target: $.version
        value: "1.0"
  response:
    rules:
      - op: rename
        source: $.result
        target: $.data
  error:
    rules:
      - op: set
        target: $.success
        value: false
      - op: rename
        source: $.errCode
        target: $.code
      - op: rename
        source: $.errMsg
        target: $.message
transform:
  - type: sm4_encrypt
    direction: request
    keyRef: appSecret
endpoints:
  - id: echo
    method: POST
    path: /api/echo
    mappingOverride:
      request:
        script: |
          import com.suntek.apiconnector.domain.model.*
          def m = ctx.bodyAsMap()
          m.vendorField = 'override'
          m
response:
  successWhen: $.code == 0
  dataPath: $.data
```

### Orchestrator integration (Phase 3 — document now, implement later)

```java
// Pseudocode for DefaultIntegrationOrchestrator.invoke() — Phase 3
ResolvedMapping mapping = MappingConfigResolver.resolve(spec, endpointSpec);
String body = request.body();
if (MappingConfigResolver.hasAnyMapping(mapping)) {
    body = mappingEngine.mapRequest(buildContext(REQUEST, body, snapshot, endpoint), mapping);
}
body = transformPipeline.applyRequest(body, spec.transform(), credentials);
AuthenticatedInvocation auth = authenticate(..., body);
// ... HTTP ...
if (shouldMapError(httpResp, evaluation)) {
    mappedBody = mappingEngine.mapError(...);
} else {
    mappedBody = transformPipeline.applyResponse(httpResp.body(), ...);
    mappedBody = mappingEngine.mapResponse(...);
}
```

---

## JSONPath Mapping Libraries vs Custom — Decision Record

| Option | Verdict | Rationale |
|--------|---------|-----------|
| **Jayway JsonPath + custom rule executor** | **SELECTED** | In BOM; used by `ResponseEvaluator`; supports read + `set`; team already familiar |
| **Jackson JsonNode only (no Jayway)** | Rejected | Manual path traversal reinvents JsonPath; inconsistent with `successWhen` |
| **JOLT** | Deferred (MAP-V2-01) | Good for bulk structural shift; weak on typed coercion; second spec language |
| **JSONata** | Rejected | Extra dependency; harder publish-time validation; Groovy covers escape hatch |
| **Apache Camel JSON Mapper** | Rejected | Heavyweight; wrong abstraction for embedded connector platform |

**Custom code boundary:** Rule op dispatch, coercion, `array_map` iteration, publish validator, `MappingConfigResolver` — approximately 400–600 LOC core + tests.

---

## Groovy Binding Patterns — Decision Record

Mirror Phase 1 `GroovyAuthScriptProvider`:

| Auth pattern | Mapping equivalent |
|--------------|-------------------|
| `bindings.put("ctx", authContext)` | `bindings.put("ctx", mappingContext)` |
| Return `AuthOutcome` or implement `AuthScript` | Return `Map`/`List` or implement `MappingScript` |
| `groovy_auth_script` profile type | `groovy_mapping_script` in mapping block (not auth profile) |
| Compile label `code3rd:auth` | `code3rd:mapping:request` etc. |
| `AuthException` + `AuthErrorCode` | `MappingException` + `MappingErrorCode` |
| Publish contract validation with stub context | Stub `MappingContext` with sample JSON body |

**Script return type:** Prefer `Map` (Jackson-serializable) over `JsonNode` in Groovy for simplicity.

---

## SM4 with BouncyCastle — Implementation Notes

```java
// Match legacy CETC (system-thirdpart CetcUtils)
Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding", "BC");
SecretKeySpec keySpec = new SecretKeySpec(keyUtf8Bytes, "SM4");
cipher.init(Cipher.ENCRYPT_MODE, keySpec);
byte[] encrypted = cipher.doFinal(plainUtf8Bytes);
String out = Base64.getEncoder().encodeToString(encrypted);
```

- **Provider:** `BouncyCastleProvider` registered once at class load.
- **Key length:** Legacy uses UTF-8 string key bytes (16 bytes for SM4); validate key length on publish.
- **Direction:** Request encrypt; response decrypt — separate transform steps or `direction` field.
- **Module placement:** `Sm4EncryptTransformStep` in `api-connector-mapping` (depends on BC) — keeps auth module free of body crypto per ADR-002.
- **Test vector:** Copy one encrypt/decrypt round-trip from `CetcUtils` main or unit test in legacy repo (read-only).

---

## Publish Validation Approach

Extend existing publish hook — **do not create parallel validation path**.

```
ConnectorRegistry.save()
  → publishListener.onPublish(spec)
       → [existing] tokenCache.evictForConnector
       → [existing] compile groovy_auth_script
       → [NEW] MappingPublishValidator.validate(spec)
            → foreach direction: rules XOR script
            → JsonPath.compile(each source/target)
            → known op names only
            → coerce types in enum
            → transform[] type registry lookup
       → [NEW] compile groovy_mapping_script(s)
       → [NEW] MappingScript contract check (stub eval)
       → on failure: throw MappingException(MAPPING_SPEC_INVALID) wrapped for registry
```

**Error shape (mirror auth):**

```json
{
  "code": "MAPPING_SPEC_INVALID",
  "message": "Invalid JSONPath at mapping.request.rules[2].source",
  "details": {
    "code3rd": "DEMO",
    "direction": "request",
    "ruleIndex": 2,
    "field": "source",
    "path": "$.invalid[["
  }
}
```

**Startup vs runtime:** Catalog bootstrap connectors with invalid mapping should **fail fast** (same as auth script compile errors). JDBC sync should log and skip invalid row or fail sync batch — planner choice; recommend **fail fast** for single-connector publish, **skip+log** for bulk `reloadFromStore` to avoid one bad row killing all connectors.

---

## Open Questions for Planner

| # | Question | Recommendation |
|---|----------|----------------|
| 1 | `mappingOverride` Groovy-only or full block override? | **Full block override** per direction (symmetric with auth endpoint override semantics) |
| 2 | `nest` op semantics | Create intermediate objects on missing path; document in spec schema |
| 3 | Error mapping output: replace `rawBody` or only `data` field on `ProxyInvokeResponse`? | Replace `rawBody` with mapped JSON string; `LegacyCompatResponseFormatter` consumes it |
| 4 | Move JsonPath dependency from engine to mapping module? | Engine keeps for `ResponseEvaluator`; mapping adds own dependency (both use BOM version) |
| 5 | SM4 in Phase 2 tests without CETC catalog? | Unit test with known vector; optional `CETC` catalog stub in test resources |

---

## Sources

### Primary (verified in repo)

- `.planning/phases/02-data-mapping-engine/02-CONTEXT.md` — locked decisions D-01..D-30
- `.planning/REQUIREMENTS.md` — MAP-01..05, MAP-07
- `.planning/ROADMAP.md` — Phase 2 success criteria
- `.planning/phases/01-auth-plugin-architecture/01-CONTEXT.md` — Phase 1 dependencies
- `.planning/research/ARCHITECTURE.md`, `PITFALLS.md`, `STACK.md`
- `docs/adr/002-auth-and-transform.md`
- `api-connector-spec/.../ConnectorSpec.java` — `transform[]` field
- `api-connector-scripting/.../ScriptCompileService.java`
- `api-connector-engine/.../ConnectorPublishListener.java`
- `api-connector-engine/.../ResponseEvaluator.java`
- `api-connector-api/.../LegacySuntekResult.java`
- `docs/legacy-auth-inventory.md` — SM4/CETC Wave 2+ deferral

### External / legacy (read-only)

- `system-thirdpart/.../CetcUtils.java` — SM4/ECB/PKCS5Padding + Base64
- Jayway JsonPath README — `parse().set()` mutation API [CITED: /json-path/jsonpath Context7]

### Phase 1 patterns (mirror)

- `.planning/phases/01-auth-plugin-architecture/01-RESEARCH.md` — compile-on-publish, Nyquist map, module graph

---

*Phase: 02-data-mapping-engine*
*Research complete: 2026-06-17*
