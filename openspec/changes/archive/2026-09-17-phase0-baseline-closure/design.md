## Context

See `proposal.md` for why. Current tree is already the V2.7 six-module engine on `feat/v2.7-greenfield-runtime`; OpenSpec change `v2-7-greenfield-runtime` is archived with Mock A–I tasks complete. Baseline specs live under `openspec/specs/`. CI workflow `.github/workflows/v2-7-runtime.yml` tests only the new modules on Temurin 21 (path filters exclude docs-only diffs). GraalVM spike conclusion already exists in `api-connector-config/README.md`.

Stale surfaces verified on disk:

- Root `README.md` still lists deleted hub modules and `java -jar api-connector-app/...`.
- `docs/README.md` and `docs/DEVELOPER.md` (linked from root README) still describe Fat JAR, `/console/`, and `POST /integrations/{code3rd}/...`.
- `.planning/PROJECT.md` / `ROADMAP.md` still point at Phase 4 Admin BFF; GSD injects PROJECT plus `.planning/codebase/STACK.md` / `ARCHITECTURE.md` into `CLAUDE.md`.
- Cookie / `planId` notes missing from `api-connector-config/README.md`.

## Goals / Non-Goals

**Goals:**

- Make `main` the Phase 0 Runtime baseline via one PR + green `v2-7-runtime`.
- Align human-facing and GSD-injected docs so agents are not steered to Vue / Fat JAR / proxy APIs.
- Freeze platform notes so Cookie / `planId` / JDK+polyglot are not rediscussed during protocol validation.

**Non-Goals:**

- Changing Runtime behavior, ApiClient contract, or baseline OpenSpec requirements.
- Implementing Host, admin UI, or wenxin/gaode Definitions (next change).
- Rewriting `docs/THIRDPART-MIGRATION.md`, `API-STYLE.md`, profile registry, or the V2.7 design pack.
- Hand-editing generated `CLAUDE.md`.
- Re-running Mock A–I unless CI fails.

## Decisions

### D1. `skip_specs: true`

**Choice:** No delta specs for this change.

**Why:** Closure is merge + docs + platform notes. Behavior already locked in archived Phase 0 and `openspec/specs/`.

**Alternatives:** Invent a `platform-baseline` capability — rejected (would fake requirements).

### D2. Supersede planning and GSD maps, do not delete history

**Choice:** Banner `.planning/PROJECT.md`, `ROADMAP.md`, `.planning/codebase/STACK.md`, and `ARCHITECTURE.md`. Keep files. Do not hand-edit `CLAUDE.md` (GSD regenerates it from those sources).

**Why:** Phases 1–3 history remains useful. STACK/ARCHITECTURE still claim 11 Spring modules; leaving them unbannered keeps injecting the old product into agent sessions.

**Alternatives:** Full ROADMAP rewrite as Phase 1 Host — deferred until Host decision. Delete planning — rejected (loses audit trail). Rewrite `CLAUDE.md` in place — rejected (next GSD ingest overwrites).

### D3. Platform notes live only in `api-connector-config/README.md`

**Choice:** Extend that README for Cookie and `planId`. GraalVM section stays. No new ADR.

**Why:** Spike notes already live there (JSONPath + GraalVM). Dual write to `docs/adr/` was rejected in review: proposal had an “or short ADR” hedge that would fork truth.

**Alternatives:** New ADR — rejected. Split Cookie notes into `api-connector-runtime` — rejected (one platform-notes file).

### D4. Document Cookie and planId as-implemented (no redesign)

**Choice:** Record current code, not a desired RFC library.

Cookie: `com.suntek.apiconnector.runtime.session.CookieStore` uses `java.net.HttpCookie.parse` / `domainMatches`; defaults blank Domain/Path from request URI; matches Domain + Path prefix + Secure scheme; purges expired. No SameSite. HttpOnly is stored by JDK parse but not used as an outbound filter (server-side client).

`planId`: `PlanCompiler` → `DefinitionNormalizer.normalize` → `CanonicalJson.stringify` (object keys sorted, array order preserved) → `PlanId.sha256Hex` (SHA-256, lowercase hex). No clock/random in the hash.

**Why:** Archived Open Questions were “record the choice.”

**Alternatives:** Switch to a Cookie RFC library — out of scope. Change hash algorithm — breaks Mock stability, rejected.

### D5. Single PR into `main`

**Choice:** Land platform notes + narrative banners on `feat/v2.7-greenfield-runtime`, then one PR into `main`. Require job `v2-7-runtime` green. Engine files are in the PR diff vs `main`, so path filters will run CI even though docs paths are excluded.

**Why:** Two-step “engine then docs” would leave `main` with a green engine and a hub README. Direct push to `main` rejected without team policy.

**Alternatives:** Docs-only follow-up PR — only if the engine PR already merged before this apply; then task 4.3 still requires proving the last engine commit stayed green (docs-only will not retrigger the workflow).

## Risks / Trade-offs

- [Stale deep migration docs] → Banner only README-linked entry points (`docs/README.md`, `docs/DEVELOPER.md`). Leave `THIRDPART-MIGRATION.md` / `API-STYLE.md` untouched unless newly linked.
- [CI path filters miss docs-only commits] → Prefer one PR that includes engine paths. After a docs-only follow-up, do not treat a skipped workflow as failure; record the last green engine SHA.
- [GSD CLAUDE.md lag] → Banner sources; do not hand-patch `CLAUDE.md`. Next GSD ingest picks up PROJECT/STACK.
- [Cookie/planId docs drift] → Notes must name `CookieStore`, `CanonicalJson`, `PlanId`. If names differ, fix docs not algorithms.
- [ITS still expects a hub] → README states Flow Runtime + in-process `ApiClient`; Host is a later change.

## Migration Plan

1. Commit notes + banners on `feat/v2.7-greenfield-runtime`.
2. Local `mvnw` test of the four engine modules (CI-equivalent).
3. Open one PR → review → merge to `main`.
4. Confirm `v2-7-runtime` ran and passed on that PR (or record last green engine SHA if a later docs-only commit skips CI).
5. Rollback = revert the merge commit. That does not restore deleted hub modules from older history unless a separate restore is requested.

## Open Questions

- Host as embedded library vs standalone service (deferred; next protocol-validation change still has no Host).
