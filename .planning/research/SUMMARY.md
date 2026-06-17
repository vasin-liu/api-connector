# Project Research Summary

**Project:** API Connector
**Domain:** Enterprise third-party API integration hub (system-thirdpart replacement)
**Researched:** 2026-06-17
**Confidence:** HIGH

## Executive Summary

API Connector is an iPaaS-lite integration hub: a single spec-driven runtime replaces 60+ per-vendor Spring Controllers with a unified orchestration pipeline (resolve → map → auth → HTTP → map). Research confirms the existing hexagonal Java 21 / Spring Boot 4 foundation is the right core; the major gaps are a **mapping engine**, **auth plugin + Groovy scripting**, **React admin console**, and **contract-level legacy parity**.

The highest risk is not greenfield architecture but **drop-in migration**: subtle JSON/error/URL differences will break production callers. Mitigation is non-negotiable: golden contract tests per legacy controller domain, Groovy compile caching, and AuthContext threaded through the full pipeline. Roadmap should front-load platform capabilities (auth, mapping, admin API, compat harness) before bulk vendor migration, then finish with 100% coverage before cutover.

## Key Findings

### Recommended Stack

Retain Java 21 + Spring Boot 4 multi-module Maven layout. Add `api-connector-mapping` (and optionally `api-connector-scripting`) modules. Use **Groovy 4 JSR-223** for non-standard auth and mapping scripts with compile-on-publish caching. Replace Vue with **Vite + React 18 + Ant Design 5.26** embedded in the same Fat JAR. Use Micrometer/Prometheus (already via Actuator) for metrics; Jackson + JsonPath for mapping IR.

### Expected Features

**Must have (table stakes):**
- Unified + legacy URL invoke paths with identical contracts
- Outbound auth (Java plugins + Groovy) with context for downstream steps
- Configurable request/response/error mapping
- Admin CRUD, invoke logs, health, Prometheus metrics
- Gateway auth metadata export

**Should have (competitive):**
- Visual mapping editor and dry-run test invoke in console
- Per-code3rd metrics and health dashboards

**Defer (v2+):**
- Distributed OAuth cache, PF4J hot plugins, workflow orchestration

### Architecture Approach

Extend `DefaultIntegrationOrchestrator` pipeline with MappingEngine between resolve and auth/transport. LegacyCompatFilter driven from published specs, not hardcoded. React console calls Admin BFF on same port. Gateway owns caller auth; service exports `gatewayAuthRequired` per endpoint.

**Major components:**
1. **AuthEngine (extended)** — Java AuthProvider plugins + Groovy scripts → AuthContext
2. **MappingEngine (new)** — Declarative JSON rules + Groovy → contract-compatible bodies
3. **Compat + migration layer** — Golden tests + vendor catalogs replacing old controllers

### Critical Pitfalls

1. **Silent contract drift** — golden compat tests per legacy URL/response
2. **Uncached Groovy** — compile once on publish, cache by script hash
3. **Lost AuthContext** — pass through orchestrator to mapping and signing
4. **60+ vendor underestimation** — coverage matrix; no cutover until 100%
5. **UI rewrite blocking ops** — API-first admin BFF before polished React screens

## Implications for Roadmap

### Phase 1: Auth Plugin Architecture
**Rationale:** Outbound auth blocks every vendor migration; Groovy needed for legacy quirks.
**Delivers:** Plugin registry, AuthContext, Groovy auth adapter, built-in profiles for legacy-used types.
**Addresses:** AUTH-01..06
**Avoids:** Auth context not threaded (Pitfall 3)

### Phase 2: Data Mapping Engine
**Rationale:** Drop-in compat requires response/request shaping, not just HTTP passthrough.
**Delivers:** MappingSpec model, declarative transforms, Groovy mapping scripts, persistence.
**Addresses:** MAP-01..07
**Avoids:** Mapping in controllers anti-pattern

### Phase 3: Orchestrator Pipeline Integration
**Rationale:** Wire auth + mapping into single invoke path for proxy and legacy routes.
**Delivers:** Extended orchestrator, ordering guarantees (sign after map), audit log enrichment.
**Addresses:** PIPE-01..04

### Phase 4: Admin BFF & Gateway Metadata
**Rationale:** Config must be publishable before UI and migration at scale.
**Delivers:** REST CRUD for auth/mapping/gateway flags, publish sync, gateway export API.
**Addresses:** ADMIN-01..05, GW-01..03

### Phase 5: React Console & Observability UI
**Rationale:** Ops needs config + monitoring for production cutover.
**Delivers:** Vite+React+Ant Design replacing Vue; connector CRUD, log viewer, metrics/health screens.
**Addresses:** UI-01..06, MON-01..04

### Phase 6: Legacy Compat Test Harness
**Rationale:** De-risk migration before vendor waves; enables CI gate.
**Delivers:** Golden response framework, legacy URL test suite scaffolding, CI integration.
**Addresses:** TEST-01..04
**Avoids:** Silent contract drift (Pitfall 1)

### Phase 7: Vendor Migration — Wave 1 (Core/high-risk)
**Rationale:** Validate patterns on hardest/most-used vendors early.
**Delivers:** Catalog specs + mappings for first vendor batch; compat tests green.
**Addresses:** MIG-01..02

### Phase 8: Vendor Migration — Wave 2 & Cutover
**Rationale:** 100% coverage required before all-at-once switch.
**Delivers:** Remaining vendors, full legacy route table, cutover checklist, gateway sync verified.
**Addresses:** MIG-03..05
**Avoids:** Incomplete migration surface (Pitfall 4)

### Phase Ordering Rationale

- Platform capabilities (Phases 1–6) precede bulk migration because vendors depend on auth, mapping, admin, and tests.
- Compat harness before migration waves prevents rework.
- UI can parallelize after Admin BFF (Phase 4) starts, but MVP screens needed before cutover.
- Two migration waves keep phase count at standard 8 while allowing internal vendor batching.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 7–8:** Per-vendor legacy quirks (multipart, charset, 国密, bespoke errors)
- **Phase 2:** Complex nested mapping and date/number coercion edge cases

Phases with standard patterns (lighter research):
- **Phase 5:** React + Ant Design CRUD patterns well documented
- **Phase 6:** WireMock + golden file testing established in project

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Brownfield versions verified in codebase |
| Features | HIGH | User requirements + old module inventory |
| Architecture | HIGH | Extends proven hexagonal layout |
| Pitfalls | HIGH | Migration replacement projects have known failure modes |

**Overall confidence:** HIGH

### Gaps to Address

- **Exact legacy auth profile inventory:** Audit `system-thirdpart` clients during Phase 7 planning to finalize built-in plugin list.
- **Gateway export format:** Confirm with gateway team (OpenAPI extension vs custom JSON) in Phase 4 planning.
- **Visual mapping editor depth:** v1 may ship JSON/path form editor; drag-drop deferred to v1.x.

## Sources

### Primary (HIGH confidence)
- `.planning/codebase/*` — existing architecture and stack
- `.planning/PROJECT.md` — user decisions
- `/apache/groovy`, `/ant-design/ant-design` (Context7)

### Secondary (MEDIUM confidence)
- Kong/Camel pattern comparison (conceptual)

---
*Research completed: 2026-06-17*
*Ready for roadmap: yes*
