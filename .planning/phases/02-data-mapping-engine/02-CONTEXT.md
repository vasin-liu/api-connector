# Phase 2: Data Mapping Engine - Context

**Gathered:** 2026-06-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the data mapping engine: declarative JSON field mapping (request/response/error), Groovy script extension, optional Transform steps (SM4 first), JDBC-backed spec persistence with publish-time validation/reload, and passthrough fast path. Satisfies MAP-01 through MAP-05 and MAP-07. Does not wire mapping into the invoke pipeline execution order (MAP-06 — Phase 3), admin BFF CRUD UI (Phase 4), or full business-envelope transforms (deferred within Phase 2).

</domain>

<decisions>
## Implementation Decisions

### Declarative Mapping DSL (MAP-01, MAP-02)
- **D-01:** Mapping rules follow **connector default + endpoint override** (endpoint wins), symmetric with Phase 1 auth (D-05).
- **D-02:** Syntax is **JSONPath source/target + enumerated transform ops** (`rename`, `coerce`, `nest`, `array_map`, `set` for literals).
- **D-03:** Organize as three blocks: **`mapping.request`**, **`mapping.response`**, **`mapping.error`**.
- **D-04:** **Validate on publish/load** — reject invalid JSONPath or unknown transforms (ROADMAP SC#6 spirit; not runtime-lenient).
- **D-05:** **array_map** operator for per-element array transforms (MAP-02 nested arrays).
- **D-06:** **Lenient missing fields** (omit/null); **strict type coercion** failures raise structured mapping errors.
- **D-07:** Rules execute **sequentially** in YAML list order (later rules override same target).
- **D-08:** **set** operator writes constants/defaults to target paths without a source.

### Groovy Mapping Boundary (MAP-03)
- **D-09:** Profile type **`groovy_mapping_script`**, mirror auth: connector default + **endpoint Groovy-only override** of declarative rules.
- **D-10:** **Per-direction mutual exclusion** — each of request/response/error is either declarative rules OR a script, not both.
- **D-11:** Script bindings: **rich context** — body, `AuthContextSnapshot`, direction, endpoint metadata (AUTH-05).
- **D-12:** **`MappingScript` functional interface** — `apply(MappingContext) → Object` (Map/List), mirroring `AuthScript`.
- **D-13:** **Per-direction scripts** — `requestScript` / `responseScript` / `errorScript` fields; compile separately on publish.
- **D-14:** Runtime failures throw structured **`MappingException`** with platform error code + details (mirror `AuthException`).

### Error Response Mapping (MAP-04)
- **D-15:** Trigger error mapping on **HTTP non-2xx and business failure** (successWhen not met).
- **D-16:** Map to **legacy compat business error JSON** (system-thirdpart shape); platform HTTP status remains orchestrator concern.
- **D-17:** Error mapping uses **connector default + endpoint override** (Claude discretion on per-vendor template — user deferred).
- **D-18:** When **no mapping.error** configured, **passthrough vendor error body** unchanged.
- **D-19:** **HTTP 200 + business failure** gated by **successWhen** before applying error mapping.

### Transform Pipeline Scope (D-16 from Phase 1)
- **D-20:** Phase 2 delivers **TransformStep SPI + SM4 implementation**; other transform types stubbed; **business envelope deferred** to Wave 2+.
- **D-21:** Request order: **JSON mapping → transform (SM4) → auth signing**; response reverses (MAP-06 verified in Phase 3).
- **D-22:** Reuse existing **`ConnectorSpec.transform[]`** with typed steps (e.g. `sm4_encrypt`); JSON mapping uses new **`mapping`** blocks (separate from transform).

### Passthrough Mode (MAP-07)
- **D-23:** **Default passthrough** — no `mapping.*` blocks means skip MappingEngine (zero overhead).
- **D-24:** Passthrough is **implicit** (no `mapping.mode` field required).
- **D-25:** Passthrough skips **JSON mapping only**; **`transform[]` still runs** if configured.
- **D-26:** **Orchestrator short-circuit** — do not invoke MappingEngine bean when no mapping config (true zero overhead; Phase 3 wiring).

### JDBC Persistence (MAP-05)
- **D-27:** Store mapping inside **existing ConnectorSpec JSON blob** in JDBC (no new mapping_rules table).
- **D-28:** **Catalog/YAML and JDBC coexist** — JDBC publish overrides classpath for runtime; Catalog remains for tests and builtins.
- **D-29:** Extend **`ConnectorPublishListener`** — compile Groovy mapping scripts, validate declarative rules, update in-memory registry (no restart).
- **D-30:** **Reject invalid mapping on publish/sync** path (admin save validation deferred to Phase 4 API, but publish path enforces now).

### Claude's Discretion
- **D-01** connector/endpoint layering (user: "你来定" — locked to auth-symmetric pattern).
- **D-17** per-vendor error template organization (user: "你来定" — locked to connector default + endpoint override).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Mapping & transform architecture
- `docs/adr/002-auth-and-transform.md` — Auth vs Transform separation; transform[] purpose; SPI admission
- `docs/ARCHITECTURE.md` — ConnectorSpec fields including `transform[]`
- `docs/UI-CONFIG-SPEC.md` — transform[] UI notes (P2); field naming conventions
- `docs/DEVELOPER.md` — Module layout; how to extend engine

### Phase 1 outputs (mapping dependencies)
- `.planning/phases/01-auth-plugin-architecture/01-CONTEXT.md` — D-06 scripting module, D-16 transform deferral, auth snapshot shape
- `api-connector-scripting/` — Groovy compile-once service (reuse for mapping scripts)
- `api-connector-domain/.../AuthContextSnapshot.java` — Available to mapping scripts (AUTH-05)
- `api-connector-engine/.../ConnectorPublishListener.java` — Extend for mapping publish hooks
- `docs/legacy-auth-inventory.md` — Wave 1 vendors; informs which error/SM4 transforms matter

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` — MAP-01..05, MAP-07 acceptance criteria
- `.planning/ROADMAP.md` — Phase 2 goal and six success criteria
- `.planning/PROJECT.md` — Drop-in compat; Groovy for non-standard mapping

### Codebase maps
- `.planning/codebase/ARCHITECTURE.md` — Orchestrator flow; where mapping engine will plug in
- `api-connector-spec/.../ConnectorSpec.java` — Existing `transform()` list; extend with `mapping` model
- `api-connector-persistence/` — JDBC connector config sync pattern for MAP-05

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `api-connector-scripting` — `ScriptCompileService` for Groovy mapping scripts (compile-on-publish, content-hash cache)
- `ConnectorSpec.transform()` — List placeholder for transform pipeline steps; extend, do not replace
- `ConnectorPublishListener` — Publish-time compile/validate pattern established for auth
- `ResponseEvaluator` + `successWhen` JSONPath — Business success/failure gate for error mapping trigger
- `JdbcConnectorConfigStore` / `ConnectorConfigSyncService` — JDBC reload without restart
- `AuthContextSnapshot` on `InvocationResult` — Binding for Groovy mapping scripts

### Established Patterns
- Connector default + endpoint Groovy-only override (auth) — mirror for mapping
- Publish-time validation and fail-fast (auth scripts) — apply to mapping rules
- Hexagonal module split — new `api-connector-mapping` module expected per research (depends on domain, spec, scripting)
- JSONPath already used in `response.successWhen` — consistent with mapping path syntax

### Integration Points
- New `MappingEngine` SPI in mapping module — invoked from orchestrator in Phase 3 only; Phase 2 builds engine + spec model
- `EndpointSpec` / `ConnectorSpecParser` — Add `mapping` blocks and validation
- `ConnectorRegistry.save()` / publish listener — Reload mapping artifacts on publish
- `RuntimeApiExceptionHandler` — Map `MappingException` to structured API errors (mirror auth)

</code_context>

<specifics>
## Specific Ideas

- User requested all discussion areas in **Chinese**; decisions captured in English for agent consumption with Chinese summaries in discussion log.
- Prefer **symmetry with Phase 1 auth** for Groovy hooks, publish validation, and connector/endpoint layering.
- **SM4 in Phase 2** as first real TransformStep; business envelope explicitly deferred.
- Passthrough must be a **real skip**, not a no-op method call.

</specifics>

<deferred>
## Deferred Ideas

- **Business envelope transform** — wrap/unwrap generic JSON envelope; deferred until Wave 2 vendor requires it (D-20).
- **MAP-06 pipeline ordering integration tests** — Phase 3 (HMAC after mapped body).
- **Admin UI mapping editor** — Phase 4; publish-path validation satisfies SC#6 interim.
- **invokeStream mapping snapshot exposure** — noted in Phase 1 verification; address when streaming mapping ships (Phase 2/3).
- **JOLT chain import (MAP-V2-01)** — v2 backlog per REQUIREMENTS.md.

</deferred>

---

*Phase: 02-data-mapping-engine*
*Context gathered: 2026-06-17*
