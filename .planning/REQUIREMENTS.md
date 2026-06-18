# Requirements: API Connector

**Defined:** 2026-06-17
**Core Value:** Zero-disruption replacement of system-thirdpart via configurable integration (auth plugins + data mapping), not per-vendor Java controllers.

## v1 Requirements

### Authentication (AUTH)

- [x] **AUTH-01**: Operator can register a Java built-in auth profile on a connector endpoint (covering all auth types used by legacy system-thirdpart vendors)
- [ ] **AUTH-02**: Operator can attach a Groovy auth script to a connector endpoint for non-standard outbound authentication flows
- [ ] **AUTH-03**: System compiles and caches Groovy auth scripts on publish (no per-request recompilation)
- [x] **AUTH-04**: AuthEngine produces an immutable AuthContext (tokens, signatures, headers) consumable by mapping and HTTP transport steps
- [x] **AUTH-05**: AuthContext is available to Groovy mapping scripts for the same invoke pipeline
- [x] **AUTH-06**: System fails invoke with structured error when auth profile/script is missing or misconfigured

### Data Mapping (MAP)

- [x] **MAP-01**: Operator can define declarative request field mappings (paths, transforms, type coercion) per endpoint
- [x] **MAP-02**: Operator can define declarative response field mappings per endpoint including nested objects and arrays
- [x] **MAP-03**: Operator can attach a Groovy mapping script for complex transformations not expressible declaratively
- [x] **MAP-04**: MappingEngine supports error-response mapping to legacy error JSON shapes
- [x] **MAP-05**: Mapping rules persist in JDBC store and reload on connector publish without restart
- [x] **MAP-06**: Invoke pipeline applies mappings in correct order relative to auth signing (body finalized before HMAC)
- [x] **MAP-07**: Operator can configure passthrough mode (no mapping) as default for simple endpoints

### Invoke Pipeline (PIPE)

- [x] **PIPE-01**: Unified invoke API orchestrates resolve → mapRequest → auth → HTTP → evaluate → mapResponse
- [ ] **PIPE-02**: Legacy URL paths route through the same orchestration pipeline as unified API
- [ ] **PIPE-03**: Invoke audit log records code3rd, endpointId, latency, outcome, and correlation id
- [ ] **PIPE-04**: Streaming invoke path remains supported for endpoints that require it

### Admin & Configuration (ADMIN)

- [ ] **ADMIN-01**: Admin BFF exposes CRUD for connectors, endpoints, credentials, auth profiles, and mapping rules
- [ ] **ADMIN-02**: Operator can publish connector config to runtime registry via admin API
- [ ] **ADMIN-03**: Operator can dry-run test invoke from admin API with sample payload
- [ ] **ADMIN-04**: Admin API validates mapping and auth script syntax before publish
- [ ] **ADMIN-05**: Published config changes are auditable (who/when/version)

### Gateway Integration (GW)

- [ ] **GW-01**: Each endpoint spec includes `gatewayAuthRequired` flag (caller auth handled by upstream gateway)
- [ ] **GW-02**: System exports gateway route metadata (path, method, auth flag) for all published endpoints
- [ ] **GW-03**: Legacy URL routes are included in gateway metadata export

### Admin UI (UI)

- [ ] **UI-01**: Management console uses Vite + React + Ant Design (Vue fully removed)
- [ ] **UI-02**: Console is served from same HTTP port as backend at `/console/`
- [ ] **UI-03**: Operator can manage connectors, credentials, auth, and mapping rules via console
- [ ] **UI-04**: Console includes invoke log search/filter viewer
- [ ] **UI-05**: Console displays per-connector health status
- [ ] **UI-06**: Console embeds or links Prometheus metrics views for integration QPS/latency/errors

### Monitoring (MON)

- [ ] **MON-01**: Prometheus endpoint exposes per-code3rd invoke counters and latency histograms
- [ ] **MON-02**: Actuator health includes per-connector health contributor
- [ ] **MON-03**: Structured invoke audit logs are queryable (API + UI)
- [ ] **MON-04**: Connector health check probes vendor reachability on configurable interval

### Compatibility Testing (TEST)

- [ ] **TEST-01**: Contract test harness captures golden request/response pairs per legacy system-thirdpart endpoint
- [ ] **TEST-02**: CI runs compat tests comparing new implementation output to golden files
- [ ] **TEST-03**: Legacy URL filter paths have dedicated compat test coverage
- [ ] **TEST-04**: Error response shapes (non-2xx, business errors) included in compat tests

### Migration (MIG)

- [ ] **MIG-01**: All system-thirdpart RestController domains have equivalent connector specs (inventory 100% mapped)
- [ ] **MIG-02**: Wave 1 core/high-traffic vendor domains migrated with compat tests passing
- [ ] **MIG-03**: All remaining vendor domains migrated with compat tests passing
- [ ] **MIG-04**: Every legacy URL prefix from system-thirdpart is registered in legacy compat routing
- [ ] **MIG-05**: Production cutover checklist complete (100% compat, gateway metadata synced, monitoring green)

## v2 Requirements

### Authentication

- **AUTH-V2-01**: mTLS client certificate auth profile
- **AUTH-V2-02**: Sandboxed Groovy execution for untrusted script authors

### Mapping

- **MAP-V2-01**: JOLT chain import for bulk JSON restructure
- **MAP-V2-02**: Mapping rule versioning with one-click rollback

### Platform

- **PLAT-V2-01**: Distributed OAuth token cache (Redis) for multi-instance deployment
- **PLAT-V2-02**: PF4J hot-deploy auth/mapping plugin JARs

## Out of Scope

| Feature | Reason |
|---------|--------|
| Reuse system-thirdpart Java code | Explicit rewrite constraint |
| Caller authentication in this service | Upstream API gateway owns consumer auth |
| Vue admin console | Replaced by React per team decision |
| All theoretical auth protocols in v1 | Cover legacy-used types first |
| Multi-step workflow/BPMN orchestration | Not legacy behavior; defer to v2+ |
| Dual-stack permanent operation | All-at-once cutover after 100% migration |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Complete |
| AUTH-02 | Phase 1 | Pending |
| AUTH-03 | Phase 1 | Pending |
| AUTH-04 | Phase 1 | Complete |
| AUTH-05 | Phase 1 | Complete |
| AUTH-06 | Phase 1 | Complete |
| MAP-01 | Phase 2 | Complete |
| MAP-02 | Phase 2 | Complete |
| MAP-03 | Phase 2 | Complete |
| MAP-04 | Phase 2 | Complete |
| MAP-05 | Phase 2 | Complete |
| MAP-06 | Phase 3 | Complete |
| MAP-07 | Phase 2 | Complete |
| PIPE-01 | Phase 3 | Complete |
| PIPE-02 | Phase 3 | Pending |
| PIPE-03 | Phase 3 | Pending |
| PIPE-04 | Phase 3 | Pending |
| ADMIN-01 | Phase 4 | Pending |
| ADMIN-02 | Phase 4 | Pending |
| ADMIN-03 | Phase 4 | Pending |
| ADMIN-04 | Phase 4 | Pending |
| ADMIN-05 | Phase 4 | Pending |
| GW-01 | Phase 4 | Pending |
| GW-02 | Phase 4 | Pending |
| GW-03 | Phase 4 | Pending |
| UI-01 | Phase 5 | Pending |
| UI-02 | Phase 5 | Pending |
| UI-03 | Phase 5 | Pending |
| UI-04 | Phase 5 | Pending |
| UI-05 | Phase 5 | Pending |
| UI-06 | Phase 5 | Pending |
| MON-01 | Phase 5 | Pending |
| MON-02 | Phase 5 | Pending |
| MON-03 | Phase 5 | Pending |
| MON-04 | Phase 5 | Pending |
| TEST-01 | Phase 6 | Pending |
| TEST-02 | Phase 6 | Pending |
| TEST-03 | Phase 6 | Pending |
| TEST-04 | Phase 6 | Pending |
| MIG-01 | Phase 7 | Pending |
| MIG-02 | Phase 7 | Pending |
| MIG-03 | Phase 8 | Pending |
| MIG-04 | Phase 8 | Pending |
| MIG-05 | Phase 8 | Pending |

**Coverage:**

- v1 requirements: 44 total
- Mapped to phases: 44
- Unmapped: 0 ✓

---
*Requirements defined: 2026-06-17*
*Last updated: 2026-06-17 after roadmap creation*
