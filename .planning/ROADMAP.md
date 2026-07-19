# Roadmap: API Connector

**Project:** API Connector — system-thirdpart replacement
**Phases:** 8 | **Requirements:** 44 v1 | **Coverage:** 100%
**Structure:** Vertical MVP (end-to-end capability slices per phase)

## Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | Auth Plugin Architecture | 6/6 | Complete   | 2026-06-17 |
| 2 | Data Mapping Engine | 6/6 | Complete | 2026-06-18 |
| 3 | Orchestrator Pipeline | 4/4 | Complete   | 2026-07-19 |
| 4 | Admin BFF & Gateway Metadata | Config APIs, publish flow, gateway route export | ADMIN-01..05, GW-01..03 | 8 |
| 5 | React Console & Observability | Replace Vue UI; logs, metrics, health screens | UI-01..06, MON-01..04 | 10 |
| 6 | Legacy Compat Test Harness | Golden-file contract tests as CI gate | TEST-01..04 | 4 |
| 7 | Vendor Migration Wave 1 | Core/high-risk legacy domains with compat tests | MIG-01, MIG-02 | 5 |
| 8 | Vendor Migration Wave 2 & Cutover | 100% legacy coverage + production switch | MIG-03..05 | 5 |

---

## Phase 1: Auth Plugin Architecture

**Goal:** Establish independent auth plugin module with Java built-ins and Groovy script support; AuthContext flows to downstream pipeline stages.

**Mode:** mvp

**Requirements:** AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06

**Success Criteria:**

1. Operator can assign built-in auth profile (api_key, hmac, oauth2, 国密 as needed by legacy audit) to an endpoint via config
2. Operator can publish Groovy auth script; second invoke uses cached compiled script (measurable compile-once behavior)
3. AuthContext carries tokens/headers/signatures accessible from unit test without HTTP call
4. Missing auth configuration returns structured `UPSTREAM_AUTH_FAILED` (or legacy-equivalent) error
5. Integration test: OAuth/HMAC vendor issues correctly signed outbound request
6. Legacy-used auth types inventory documented from system-thirdpart audit

**Plans:** 6/6 plans complete

| Wave | Plans | What it builds |
|------|-------|----------------|
| 1 | 01 | `api-connector-scripting` module + compile-once Groovy cache |
| 2 | 02 | Groovy auth provider + spec extensions (`groovy_auth_script`) |
| 3 | 03 | Central TokenCache + publish-time compile/contract validation |
| 4 | 04, 05 | AuthContextSnapshot carry-forward; structured auth errors |
| 5 | 06 | Wave 1 L2 profiles + legacy auth inventory + WireMock proof |

---

## Phase 2: Data Mapping Engine

**Goal:** New mapping module supports declarative field transforms and Groovy scripts for request, response, and error shapes.

**Mode:** mvp

**Requirements:** MAP-01, MAP-02, MAP-03, MAP-04, MAP-05, MAP-07

**Success Criteria:**

1. Declarative mapping renames/nests fields with type coercion (string↔number↔date) in unit tests
2. Groovy mapping script transforms sample vendor JSON to target legacy shape
3. Error JSON from vendor maps to legacy business error structure
4. Mapping rules persist in DB and apply after publish without application restart
5. Passthrough endpoints skip mapping with zero overhead path
6. Mapping spec validates on admin save (invalid path/transform rejected)

**Plans:** 6/6 plans complete (2026-06-18)

| Wave | Plans | What it builds |
|------|-------|----------------|
| 1 | 02-01 | `api-connector-mapping` module + MappingSpec model + parser + publish validator |
| 2 | 02-02 | DeclarativeRuleExecutor: rename, set, coerce, nest |
| 3 | 02-03 | array_map + MappingEngine facade + MappingConfigResolver |
| 4 | 02-04 | Groovy mapping scripts + publish compile + MappingException handler |
| 5 | 02-05 | Error mapping → legacy shape + passthrough hasAnyMapping API |
| 6 | 02-06 | TransformStep SPI + SM4 + JDBC publish integration test |

**Cross-cutting constraints:**

- Connector default + endpoint override resolution (D-01) across all mapping directions
- Publish-time validation rejects invalid JSONPath/transforms (D-04, D-30)
- Orchestrator invoke wiring deferred to Phase 3 (MAP-06, D-26)

---

## Phase 3: Orchestrator Pipeline Integration

**Goal:** Single invoke pipeline applies mapping and auth in correct order for proxy API and legacy URL routes.

**Mode:** mvp

**Requirements:** MAP-06, PIPE-01, PIPE-02, PIPE-03, PIPE-04

**Success Criteria:**

1. `POST /api/v1/integrations/{code3rd}/endpoints/{id}/invoke` runs full pipeline end-to-end in integration test
2. Legacy compat filter URL hits same pipeline with identical outcome as unified API for equivalent endpoint
3. HMAC endpoint signs request body after request mapping applied (ordering verified by test)
4. Audit log line emitted per invoke with code3rd, endpointId, duration, outcome, requestId
5. Streaming endpoint still delivers chunks through pipeline without mapping buffer regression

**Plans:** 4/4 plans complete

| Wave | Plans | What it builds |
|------|-------|----------------|
| 1 | 03-01 | Core unified pipeline: mapping/transform inserted around auth (MAP-06/PIPE-01), ResolvedMappingCache + publish invalidation, mapping-enabled toggle |
| 1 | 03-02 | Correlation-id capture + MDC, audit `requestId`/`outcome` extension (PIPE-03) |
| 2 | 03-03 | Legacy vs unified core-result parity + no-double-mapping guard (PIPE-02) |
| 2 | 03-04 | Streaming request-side pipeline, raw chunks, warn-once (PIPE-04) |
Plans:
**Wave 1**

- [x] 03-01-PLAN.md — Wire orchestrator pipeline (map→transform→auth, response mirror, cache, toggle) — MAP-06, PIPE-01
- [x] 03-02-PLAN.md — Correlation-id/MDC + audit requestId/outcome — PIPE-03

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 03-03-PLAN.md — Legacy/unified pipeline parity + envelope-only error guard — PIPE-02
- [x] 03-04-PLAN.md — Streaming request-side mapping, raw chunk passthrough — PIPE-04

---

## Phase 4: Admin BFF & Gateway Metadata

**Goal:** REST admin APIs for full config lifecycle; export gateway auth metadata per endpoint.

**Mode:** mvp

**Requirements:** ADMIN-01, ADMIN-02, ADMIN-03, ADMIN-04, ADMIN-05, GW-01, GW-02, GW-03

**Success Criteria:**

1. Admin CRUD covers connector, endpoint, credential, auth profile, mapping rules via `/api/v1/admin`
2. Publish endpoint refreshes runtime registry within same request flow
3. Dry-run test invoke returns mapped response without persisting side effects
4. Invalid Groovy/mapping spec rejected with actionable validation errors
5. Publish audit record stored (timestamp, config version)
6. Endpoint spec includes `gatewayAuthRequired` boolean
7. Export API returns all published routes with path, method, gatewayAuthRequired
8. Legacy routes appear in export with correct auth flags

**Plans:** 0

---

## Phase 5: React Console & Observability

**Goal:** Replace Vue with Vite+React+Ant Design admin; operational visibility for logs, metrics, health.

**Mode:** mvp

**Requirements:** UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, MON-01, MON-02, MON-03, MON-04

**Success Criteria:**

1. Vue dependencies removed; React app builds via frontend-maven-plugin into JAR
2. `/console/` loads admin on same port as API (19090 default)
3. Operator completes connector+auth+mapping config entirely via console
4. Log viewer filters by code3rd, time range, outcome
5. Health dashboard shows per-connector status from Actuator contributor
6. Metrics view shows per-code3rd QPS, error rate, P99 latency (Prometheus-backed)
7. Prometheus scrape returns `integration_invoke` metrics tagged by code3rd
8. Connector health probe marks vendor DOWN when probe fails N times
9. Audit logs queryable via admin API used by console
10. npm build integrated in Maven CI pipeline

**Plans:** 0

---

## Phase 6: Legacy Compat Test Harness

**Goal:** Automated golden-file contract tests gate migration quality.

**Mode:** mvp

**Requirements:** TEST-01, TEST-02, TEST-03, TEST-04

**Success Criteria:**

1. Compat test module exists with golden capture tooling from system-thirdpart responses
2. CI job fails on golden mismatch
3. At least one legacy URL path tested per compat filter prefix pattern
4. Error and empty-body golden cases included for sample vendor

**Plans:** 0

---

## Phase 7: Vendor Migration Wave 1

**Goal:** Migrate core/high-traffic system-thirdpart domains; establish migration playbook.

**Mode:** mvp

**Requirements:** MIG-01, MIG-02

**Success Criteria:**

1. Complete inventory matrix: every legacy RestController domain → connector code3rd (100% rows defined)
2. Wave 1 vendors (TBD from inventory: IDPS, Gaode, Baidu, Hikvision, TrafficBrain, etc.) have Catalog specs
3. All Wave 1 legacy URLs route correctly via LegacyCompatFilter
4. Compat tests pass 100% for Wave 1 golden files
5. Migration playbook documented (spec authoring, mapping, auth, test, publish checklist)

**Plans:** 0

---

## Phase 8: Vendor Migration Wave 2 & Cutover

**Goal:** Complete remaining vendors; execute all-at-once production cutover.

**Mode:** mvp

**Requirements:** MIG-03, MIG-04, MIG-05

**Success Criteria:**

1. All remaining legacy vendor domains migrated; compat tests pass 100% globally
2. Every system-thirdpart URL prefix registered and smoke-tested
3. Gateway metadata export synced and verified against gateway team checklist
4. Cutover runbook executed in staging (monitoring green 48h)
5. Production cutover approved with zero open P1 compat failures

**Plans:** 0

---

## Phase Dependencies

```
Phase 1 (Auth) ──► Phase 3 (Pipeline) ◄── Phase 2 (Mapping)
                         │
                         ▼
                  Phase 4 (Admin BFF)
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
       Phase 5 (React UI)    Phase 6 (Compat Tests)
              │                     │
              └──────────┬──────────┘
                         ▼
                  Phase 7 (Migration W1)
                         │
                         ▼
                  Phase 8 (Migration W2 + Cutover)
```

---

*Roadmap created: 2026-06-17*
*Next: `/gsd-discuss-phase 1` or `/gsd-plan-phase 1`*
