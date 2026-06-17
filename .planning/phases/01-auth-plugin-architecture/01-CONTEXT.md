# Phase 1: Auth Plugin Architecture - Context

**Gathered:** 2026-06-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish the outbound auth plugin architecture: Java built-in `AuthProvider` profiles for Wave 1 legacy vendors, Groovy script extension for non-standard flows, compile-on-publish script caching, and a downstream-readable auth snapshot for the invoke pipeline. Satisfies AUTH-01 through AUTH-06. Does not include data mapping (Phase 2), full admin BFF persistence UI (Phase 4), PF4J hot-deploy (v2), or Transform Pipeline body encryption (deferred per ADR-002).

</domain>

<decisions>
## Implementation Decisions

### v1 Built-in Profile Scope
- **D-01:** Phase 1 Java built-ins cover auth types required by **Wave 1 migration vendors** only (IDPS, Gaode, Baidu, Hikvision, TrafficBrain, etc.). Remaining legacy types use Groovy until their migration wave.
- **D-02:** **L2 profiles** used by Wave 1 vendors ship as Java `AuthProvider` classes (e.g., `oauth2_password`, `bearer_from_login`). Exotic L2 combos not in Wave 1 may still defer.
- **D-03:** Deliver **`docs/legacy-auth-inventory.md`**: vendor → profile type → L1/L2/L3 → migration wave → implementation path (Java / Groovy / SPI / deferred). Success criterion #6 artifact.
- **D-04:** **国密** (SM3 header sign, SM4 body encrypt) Java profiles included **only if** the legacy audit shows Wave 1 vendors require them.

### Groovy Auth Hook Design
- **D-05:** Groovy auth scripts bind at **connector level by default**, with **per-endpoint override** when needed. Endpoint override is **Groovy-only** — endpoints inherit Java built-in profiles from connector; only `groovy_auth_script` may differ per endpoint.
- **D-06:** New **`api-connector-scripting`** module owns Groovy JSR-223 compilation and publish-time cache; shared by auth (Phase 1) and mapping (Phase 2).
- **D-07:** Groovy integrates as profile type **`groovy_auth_script`** — an `AuthProvider` adapter usable as standalone auth or as a `auth.pipeline[]` step.
- **D-08:** Scripts **compile on publish/load** (Catalog YAML, classpath spec, or future JDBC sync). Cache keyed by content hash. **Compile + contract validation** on publish; fail fast with structured error (line/symbol when possible). No per-request recompilation (AUTH-03).
- **D-09:** Groovy script **source of truth** is the `ConnectorSpec` auth block (YAML/JSON in spec). Compiled artifact cached in memory.

### AuthContext Downstream Shape
- **D-10:** Downstream pipeline receives **immutable `AuthContextSnapshot`** (read-only post-auth copy) **plus `AuthOutcome`** (headers/query/body mutations). AUTH-04 input `AuthContext` remains immutable for auth execution.
- **D-11:** OAuth tokens and signing intermediates exposed via structured **`ext` map** on the snapshot with documented standard keys (`accessToken`, `tokenExpiresAt`, `signatureBase`, `oauthRawResponse`, etc.).
- **D-12:** Snapshot + outcome **carried through the invoke pipeline** on `InvocationRequest`/result for mapping (Phase 2) and audit enrichment (Phase 3).

### L3 Java SPI vs Groovy Boundary
- **D-13:** **L3 vendors default to Groovy** in Phase 1 (大华 multi-step login, 海康 Artemis SDK, Cookie session, 讯飞 WS). No new `custom_spi` Java classes unless ADR-002 criteria force it.
- **D-14:** **ADR-002 strict SPI admission**: multi-step login ≥3 round-trips, vendor SDK only, non-templateable crypto, Cookie/WebSocket dedicated.
- **D-15:** **PF4J hot-deploy deferred to v2** (PLAT-V2-02). Interim L3 Java (if ever needed before v2) registers as **Spring `@Bean` `AuthProvider`** in `api-connector-auth`.
- **D-16:** **Transform Pipeline** (SM4 encrypt, business envelope per ADR-002) **deferred to Phase 2** mapping engine — not in Phase 1 auth scope.

### Auth Failure & Errors
- **D-17:** Phase 1 uses **unified platform error codes** (`UPSTREAM_AUTH_FAILED`, `AUTH_PROFILE_MISSING`, script compile errors, etc.). Legacy-shaped per-vendor error JSON deferred to compat layer (Phase 6) / mapping.
- **D-18:** Structured JSON payload: `{ code, message, details? }` — `details` includes missing profile type, script compile failure, `code3rd`, profile type for ops.

### OAuth Token Cache
- **D-19:** **Central `TokenCache` service** keyed by `code3rd + profile + scope` (ADR-002). All OAuth providers use it; replace per-provider in-memory maps.
- **D-20:** **Proactive refresh** with expiry skew (e.g., 60s before `expiresAt`); single-flight per cache key.
- **D-21:** **Evict all tokens for `code3rd`** on connector republish or credential change.

### Phase 1 Config & Validation
- **D-22:** Auth profiles and Groovy scripts configured via **Java Catalog + classpath YAML + env credentials only** in Phase 1. JDBC admin persistence UI deferred to Phase 4.
- **D-23:** Phase 1 validation workflow: **unit/integration tests** via Java Catalog connectors and Groovy scripts in **test resources**; no admin dry-run API until Phase 4.

### Claude's Discretion
None — user made explicit choices for all presented options.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Auth architecture & profile registry
- `docs/adr/002-auth-and-transform.md` — AuthEngine pipeline, AuthOutcome, SPI admission, Transform separation, token cache keying
- `docs/profile-registry.md` — L1/L2/L3 profile inventory and pipeline examples
- `docs/DEVELOPER.md` — Module responsibilities, how to add AuthProvider, SPI guidance
- `docs/schemas/profiles-meta/` — Profile UI metadata (reference for profile IDs; UI is Phase 5)

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` — AUTH-01..06 acceptance criteria
- `.planning/ROADMAP.md` — Phase 1 goal and success criteria
- `.planning/PROJECT.md` — Java plugins + Groovy fallback, v1 scope constraints
- `.planning/research/SUMMARY.md` — Groovy compile cache pitfall, AuthContext threading
- `.planning/research/PITFALLS.md` — Groovy hot-path caching, AuthContext loss risks
- `.planning/research/ARCHITECTURE.md` — api-connector-scripting module recommendation
- `.planning/research/STACK.md` — Groovy 4 JSR-223 dependency guidance

### Codebase maps
- `.planning/codebase/ARCHITECTURE.md` — AuthEngine flow, AuthProvider SPI, orchestrator integration
- `.planning/codebase/INTEGRATIONS.md` — Implemented vs planned auth profiles
- `.planning/codebase/STACK.md` — BouncyCastle for 国密, module layout

### Phase 1 deliverable (to be created during implementation)
- `docs/legacy-auth-inventory.md` — Legacy system-thirdpart auth audit matrix (D-03)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuthEngine` — Already supports single profile and `auth.pipeline[]` multi-step merge (`api-connector-auth/.../AuthEngine.java`)
- `AuthProvider` SPI + 7 built-in profiles — `none`, `aksk_hmac_sha256_v1`, `api_key_query`, `bearer_static`, `oauth2_client_credentials`, `oauth2_token_in_query`, `gaode_traffic_hmac_v1`
- `AuthContext` / `AuthOutcome` — Input/output contracts; `AuthOutcome` merges headers, query, optional `mutatedBody`
- `DefaultIntegrationOrchestrator` — Builds `AuthContext` from spec + credentials, applies `AuthOutcome` to HTTP request
- `IntegrationEngineConfiguration` — Wires `AuthProvider` beans into `AuthEngine`
- BouncyCastle (`bcprov-jdk18on`) — Already in `api-connector-auth` for 国密-capable profiles

### Established Patterns
- Auth configured at **connector spec** level (`ConnectorSpec.auth()`); endpoint-level override not yet modeled — Phase 1 adds Groovy-only endpoint override
- OAuth token cache currently **per-provider in-memory** — Phase 1 centralizes to `TokenCache`
- Missing `AuthProvider` → `IllegalStateException` → 502 `UPSTREAM_AUTH_FAILED` via `RuntimeApiExceptionHandler`
- Hexagonal layout: auth module depends on domain + spec only; engine orchestrates

### Integration Points
- `DefaultIntegrationOrchestrator.invoke()` — Inject snapshot carry-forward after `authEngine.authenticate()`
- New `api-connector-scripting` module — Depended on by `api-connector-auth` (and later `api-connector-mapping`)
- `ConnectorSpec` / `EndpointSpec` models — Extend for `groovy_auth_script` and endpoint Groovy override
- Java Catalog connectors in `api-connector-connectors/` — Wave 1 vendor specs reference new built-in profile types

</code_context>

<specifics>
## Specific Ideas

- User initially selected PF4J for L3 registration but clarified **defer PF4J to v2** with Groovy + Spring beans as interim.
- Endpoint auth override is intentionally narrow: **Groovy-only override**, not full pipeline replacement at endpoint level.
- Legacy audit deliverable is a **new inventory doc**, not just updating `profile-registry.md`.
- Phase 1 operators validate auth via **tests + Catalog YAML**, not admin UI.

</specifics>

<deferred>
## Deferred Ideas

- **PF4J hot-deploy auth/mapping JARs** — v2 (PLAT-V2-02); user confirmed defer after initial PF4J selection
- **Transform Pipeline** (SM4 body encrypt, business envelope) — Phase 2 mapping per ADR-002
- **Per-vendor legacy error code shaping** — Phase 6 compat harness / mapping
- **JDBC persistence + admin CRUD for auth scripts** — Phase 4 Admin BFF
- **L3 Java SPI vendor plugins** (大华, 海康, etc.) — Groovy in Phase 1; Java SPI only if ADR-002 criteria met, via Spring bean until PF4J v2
- **Distributed OAuth token cache (Redis)** — v2 (PLAT-V2-01)
- **Groovy sandbox for untrusted authors** — v2 (AUTH-V2-02)

</deferred>

---

*Phase: 1-Auth Plugin Architecture*
*Context gathered: 2026-06-17*
