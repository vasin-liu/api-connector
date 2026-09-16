## Context

See `proposal.md` for why this change exists. The current tree remains a proxy hub (`DefaultIntegrationOrchestrator` → `AuthEngine` → `JdkHttpTransport`) and is not the implementation substrate. Runtime Contract: `docs/design/api-connector-design-v2_7.md`. Testable tables and YAML: `docs/design/v2.7-greenfield/`.

Constraints: Java 21; Phase 0 in-process host; memory session store; no Spring in core; existing 11 Maven modules are frozen relative to this change.

## Goals / Non-Goals

**Goals:**

- Implement Phase 0 as a new engine with modules `core`, `runtime`, `transport`, `config`.
- Execute only compiled plans; ship FakeTransport tests for Mock A–I.
- Freeze host API as `ApiClient` + snapshot/result types in `core`.
- Make JSONPath and GraalVM runtime choices via spikes before those slices land.

**Non-Goals:**

- Migrating or wrapping current Catalog, MappingEngine, Groovy, admin UI, or legacy URLs.
- REST proxy, gateway auth, tenant isolation, JDBC definition store, Visual Editor.
- Full §33 definite-assignment compiler; Phase 0 uses the shallow checks in `docs/design/v2.7-greenfield/07-plan-compiler.md`.
- Production Vault/KMS and multi-node session coordination.
- Trusted Java Extension SPI implementation (package may exist empty; no Phase 0 requirement).
- Script coverage inside Mock A–I; script is proven by 0d unit tests only.

## Decisions

### D1. Greenfield modules, not in-place rewrite

**Choice:** New Maven modules `core` / `runtime` / `transport` / `config` (V2.7 §54). Packages: `flow`, `state`, `session`, `pipeline`, `crypto`, `script`, `extension`, `policy`, `observability`.

**Why:** The existing graph (domain → spec → auth → engine → api → app) encodes a different product. Mixing both semantics in one orchestrator fails Mock C/B.

**Alternatives:** (1) Reinterpret `DefaultIntegrationOrchestrator` as FlowRuntime — rejected, dual semantics. (2) Split today’s 11 modules further — rejected, wrong seams.

### D2. Host is in-process ApiClient

**Choice:** Phase 0 public API is `ApiClient.execute` / `cancel` returning `ExecutionHandle`. No `/integrations/{code3rd}` in this change.

**Why:** Specs require snapshot, outcome, and trace, not HTTP envelopes. Tests inject FakeTransport without Spring.

**Alternatives:** Keep Spring Boot proxy as host — rejected for Phase 0 (pulls mapping, security filters, UI).

### D3. Increment 0a → 0d with capability flags

**Choice:** Compiler always emits `PlanCapability` set. 0a runtime refuses AUTH/REPLAY/SESSION/SCRIPT with `PLAN_CAPABILITY_UNSUPPORTED` while still compiling Mock C fixtures.

**Why:** Validator/compiler tests can land before session/replay exist. Matches `docs/design/v2.7-greenfield/02-phase0-increments.md`.

**Alternatives:** Delay compiler until 0c — rejected; Mock A validation would wait months.

### D4. Condition JSONPath is a spike, not Jayway default

**Choice:** Phase 0 week-0 spike: restricted library mode or a minimal parser (root/property/index/exists/eq). Jayway default filter/script MUST NOT be the Condition engine.

**Why:** Spec `flow-runtime` forbids Filter/`..`/`()`. Jayway cannot be “configured safe” without proof.

**Alternatives:** Use Jayway and document “don’t write filters” — rejected (unenforceable).

### D5. Script runtime is GraalVM Polyglot

**Choice:** Built-in scripts use GraalVM Polyglot, `HostAccess.EXPLICIT`, statement `resourceLimits`. Groovy/Nashorn/Rhino are not built-in.

**Why:** Spec `secret-script`. If the spike shows resourceLimits need GraalVM JDK, that becomes a platform constraint before 0d.

**Alternatives:** Groovy sandbox — rejected by V2.7 §39.

### D6. Replay holds templates, not bytes

**Choice:** `OriginalRequestTemplate` stores compiled templates (URL/header/body bindings). Each send renders with current VariableRuntime + Session + Pipeline.

**Why:** Spec `transport-replay` forbids clone. Timestamp/nonce HMAC depends on re-render.

**Alternatives:** Store last `RawHttpRequest` and patch headers — rejected (misses pipeline/signer).

### D7. Session missing sends business first

**Choice:** If business flow has no explicit pre-auth step, first attempt is the business request; 401/403 then AUTHENTICATE.

**Why:** Frozen in Mock B YAML. Avoid two implicit strategies.

**Alternatives:** Always authenticate when session missing — rejected unless YAML adds an explicit step.

### D7b. Session lookup key is not full SessionKey equality

**Choice:** Persist a SessionKey that includes `definitionRevision` for traces and incompatibility isolation. Lookup/reuse uses `(apiId, authProfile, credentialRef)` plus the V2.7 predicate (reuse iff those two fields are unchanged).

**Why:** Full-key equality would fail Mock H pipeline-only revision reuse; dropping revision from the recorded key would violate §16.

**Alternatives:** Key only by apiId — rejected (cannot isolate credential/profile changes).

### D7c. Cancel is in Phase 0

**Choice:** `ApiClient.cancel` is required; outcome CANCELLED; no retry/replay/auth.

**Why:** Proposal already exposed cancel; leaving it unimplemented would fork the host API.

**Alternatives:** Remove cancel from Phase 0 — rejected after review.

### D7d. UNKNOWN vs TIMEOUT

**Choice:** Written-then-dropped → UNKNOWN_OUTCOME. Deadline before write → TIMEOUT.

**Why:** V2.7 §23; mixing them breaks Mock I replay prohibition.

### D7e. In-memory published registry

**Choice:** Phase 0 definition registry is memory-only; successful load defaults to PUBLISHED; explicit DRAFT is rejected at execute.

**Why:** Spec requires PUBLISHED without JDBC.

**Alternatives:** Treat all loaded YAML as executable with no state — rejected (draft scenario would be untestable).

### D8. Shallow dataflow validation in Phase 0

**Choice:** Validate undefined vars on all paths, GLOBAL writes, secret-in-condition, pipeline types/cycles. Defer full CFG definite assignment.

**Why:** Full §33 is larger than the interpreter. Specs still reject the cases Mock A–C need.

**Alternatives:** Full compiler in 0a — rejected (schedule risk).

### D9. Test oracles live in the greenfield spec pack

**Choice:** Unit/IT expected values come from `docs/design/v2.7-greenfield/` tables (J2/J3, Mock YAML, Validate codes). OpenSpec scenarios map onto those tables; do not invent a third expected-result dialect.

**Why:** Avoid drift between OpenSpec, design markdown, and tests.

## Risks / Trade-offs

- [Phase 0 is a small compiler] → Ship 0a linear runtime first; capability flags block unimplemented actions; Mock A–I remain the gate, not module completeness. Mock YAML for A–I lives under `docs/design/v2.7-greenfield/` (03 and 08).
- [GraalVM changes deploy baseline] → Spike in 0d-1; if Zulu 21 cannot enforce resourceLimits, document JDK requirement before writing script code.
- [Secret leaks via JSON/logs/exceptions] → `SecretValue` has no `reveal()`; redaction tests in 0d; JSON codec treats nested secrets as ALLOW sinks only.
- [Replay bugs only show in packet diffs] → 0c asserts r1 vs r2 headers; DecisionTrace required for 49.1.
- [Product vacuum vs ITS hub] → Explicit non-goal; a later Host change can wrap `ApiClient`. Do not smuggle proxy APIs into Phase 0.
- [JSONPath spike fails] → Implement the five-operator parser; do not widen the spec.

## Migration Plan

1. Remove the previous hub modules (`domain`/`spec`/`auth`/`engine`/`api`/`app`/`ui`/…). Replace them with `core` / `runtime` / `transport` / `config`.
2. There is no dual-run of Flow vs AuthProvider; the old orchestrator is gone.
3. Rollback is a git revert of this branch, not a feature flag.
4. Apply override (2026-09-14): user directed that old code be cleared rather than kept beside the new engine.

## Open Questions

- After spikes: GraalVM JDK vs standard JDK plus language jars (does not change specs; may change `config` module docs).
- Cookie RFC library vs hand-rolled store (Mock E only; API of CookieStore stays).
- Exact `planId` hash canonicalization JSON field order (must be stable; algorithm stays SHA-256 of normalized JSON).
