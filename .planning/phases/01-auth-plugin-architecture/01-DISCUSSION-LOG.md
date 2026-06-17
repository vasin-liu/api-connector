# Phase 1: Auth Plugin Architecture - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-17
**Phase:** 1-Auth Plugin Architecture
**Areas discussed:** Built-in Profile Scope, Groovy Auth Hook Design, AuthContext Downstream Shape, L3 Java SPI vs Groovy Boundary, PF4J Timing, Auth Failure Errors, OAuth Token Cache, Auth Config Persistence, Follow-up overrides

---

## v1 Built-in Profile Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Wave 1 vendors only | Java built-ins for auth types used by Wave 1 migration vendors | ✓ |
| All L1 profiles | Every L1 entry in profile-registry.md | |
| Current 7 + audit P0 | Keep existing 7, add only audit-proven P0 | |
| You decide | Planner/researcher recommends | |

**User's choice:** Wave 1 vendors only
**Notes:** L2 profiles for Wave 1 ship as Java; inventory doc `docs/legacy-auth-inventory.md`; 国密 only if Wave 1 audit requires.

---

## Groovy Auth Hook Design

| Option | Description | Selected |
|--------|-------------|----------|
| Connector default + endpoint override | Connector-level default; endpoint may override | ✓ |
| Shared api-connector-scripting module | JSR-223 compile cache shared auth + mapping | ✓ |
| groovy_auth_script profile type | AuthProvider adapter; pipeline or standalone | ✓ |
| Compile on publish | No per-request compilation | ✓ |

**User's choice:** All recommended options above
**Notes:** Script source in ConnectorSpec; compile + validate on publish; endpoint override is Groovy-only.

---

## AuthContext Downstream Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Snapshot + AuthOutcome | Immutable snapshot plus HTTP mutations | ✓ |
| Structured ext map | Tokens/signing intermediates in ext keys | ✓ |
| Carry through invoke pipeline | Available to mapping and audit | ✓ |
| Immutable input + read-only snapshot | AUTH-04 preserved | ✓ |

**User's choice:** All recommended options above

---

## L3 Java SPI vs Groovy Boundary

| Option | Description | Selected |
|--------|-------------|----------|
| Groovy default for L3 in v1 | No new custom_spi unless forced | ✓ |
| Strict ADR-002 criteria | SPI admission rules | ✓ |
| Defer Transform to Phase 2 | SM4 envelope not in Phase 1 auth | ✓ |

**User's choice:** Groovy default; ADR-002 criteria; defer Transform
**Notes:** User initially selected PF4J for L3 registration — revised in follow-up to defer PF4J to v2.

---

## PF4J Timing (follow-up)

| Option | Description | Selected |
|--------|-------------|----------|
| Defer PF4J to v2 | Groovy + Spring beans interim | ✓ |
| Spring @Bean AuthProvider | Interim L3 Java registration | ✓ |

**User's choice:** Defer PF4J; Spring beans for rare interim L3 Java

---

## Auth Failure Error Semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Unified platform codes in Phase 1 | Legacy shapes in Phase 6 | ✓ |
| Structured platform JSON | code, message, details | ✓ |

**User's choice:** Platform-unified errors now; legacy compat later

---

## OAuth Token Cache

| Option | Description | Selected |
|--------|-------------|----------|
| Central TokenCache service | code3rd+profile+scope keys | ✓ |
| Proactive refresh with skew | Single-flight per key | ✓ |
| Evict on republish/credential change | Per code3rd flush | ✓ |

**User's choice:** All recommended options above

---

## Auth Config Persistence

| Option | Description | Selected |
|--------|-------------|----------|
| Catalog + YAML only in Phase 1 | JDBC admin in Phase 4 | ✓ |
| Script source in ConnectorSpec | Compiled cache in memory | ✓ |
| Tests via Catalog + test resources | No admin dry-run until Phase 4 | ✓ |

**User's choice:** All recommended options above

---

## Claude's Discretion

None — user selected explicit options for every question.

## Deferred Ideas

- PF4J hot-deploy (v2)
- Transform Pipeline (Phase 2)
- Legacy per-vendor auth error shapes (Phase 6)
- JDBC admin persistence for auth (Phase 4)
- Distributed Redis token cache (v2)
- Groovy sandbox (v2)
