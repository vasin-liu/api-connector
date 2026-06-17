# Feature Research

**Domain:** Enterprise third-party API integration platform (iPaaS-lite / API gateway backend)
**Researched:** 2026-06-17
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Unified HTTP proxy invoke API | Callers expect one integration entry, not 60 controllers | MEDIUM | Already exists; must preserve + legacy paths |
| Per-connector credential storage | Each vendor has keys/secrets | MEDIUM | JDBC store exists; extend for script refs |
| Outbound authentication | Third parties require signed/tokenized requests | HIGH | AuthProvider SPI exists; must pluginize + Groovy |
| Request/response passthrough | Most calls are forward-with-minimal-change | LOW | Default path when no mapping configured |
| Error normalization | Callers expect consistent error JSON | MEDIUM | `ApiErrorResponse` + legacy error shape compat |
| Connector enable/disable | Ops must cut off bad integrations | LOW | Publish status in DB |
| Call logging | Debug production issues | MEDIUM | Audit logger exists; needs UI query |
| Health indicators | K8s/ops monitoring | LOW | Actuator + per-connector health |
| Admin CRUD for connectors | Cannot require redeploy for config | HIGH | Partial; needs mapping + auth script UI |
| Legacy URL compatibility | Drop-in replacement requirement | HIGH | `LegacyCompatFilter` must cover all old paths |

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Declarative + scriptable data mapping | Eliminate per-vendor Java DTO glue | HIGH | Core new capability |
| Auth plugin + Groovy extension | Onboard odd vendors without release cycle | HIGH | Core new capability |
| Gateway auth metadata export | Central policy at API gateway | MEDIUM | OpenAPI extensions or sidecar config |
| Visual mapping editor | Reduce integration developer skill bar | HIGH | Ant Design Form + JSON tree UI |
| Java Catalog + DB-published specs | Type-safe dev path + runtime config | MEDIUM | Already differentiated vs old monolith |
| Prometheus metrics per code3rd | Capacity planning per vendor | MEDIUM | Micrometer tags on `code3rd` |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Full BPMN/visual workflow engine | "Orchestrate multi-step flows" | Massive scope; not old module behavior | Chain endpoints in caller or future v2 workflow |
| Real-time bi-directional streaming for all | "Modern" | Most legacy vendors are request/response | Streaming only where vendor supports (existing stream API) |
| User-editable Groovy without sandbox | Fast customization | RCE risk on admin compromise | Trusted-admin scripts + compile cache; sandbox later |
| Reuse old `system-thirdpart` classes | Faster migration | Violates rewrite constraint | Contract tests against old module responses |
| Distributed OAuth token cache (Redis) v1 | Multi-instance | Premature; not current deployment | Document single-instance; add Redis in v2 if needed |
| Building caller auth in this service | Simpler demo | Conflicts with gateway decision | Export `security.required` metadata per route |

## Feature Dependencies

```
Legacy URL compat
    └──requires──> Connector registry (all vendors migrated)
                       └──requires──> Auth plugins (per vendor outbound auth)
                       └──requires──> Mapping engine (response shape compat)

Visual admin UI
    └──requires──> Admin BFF APIs (CRUD mapping, auth, metadata)
                       └──requires──> Auth plugin registry API
                       └──requires──> Mapping rule persistence

Gateway auth metadata
    └──requires──> Endpoint security flags in ConnectorSpec
                       └──enhances──> OpenAPI / route export for gateway

Monitoring dashboard
    └──requires──> Structured invoke logs + Prometheus metrics
                       └──requires──> Per-connector health probes
```

## MVP Definition

### Launch With (v1 — blocks system-thirdpart cutover)

- [ ] Auth plugin architecture (Java built-ins covering legacy-used profiles + Groovy scripts)
- [ ] Data mapping engine (declarative + Groovy; request + response + error mapping)
- [ ] All `system-thirdpart` external HTTP contracts replicated (60+ domains)
- [ ] Legacy URL path mapping for every old controller prefix
- [ ] React + Ant Design admin: connector CRUD, auth config, mapping editor (baseline)
- [ ] Invoke logs viewer + Prometheus metrics + connector health in UI
- [ ] Gateway auth metadata on endpoints (public vs gateway-protected flag + export)
- [ ] Contract/regression test suite comparing old vs new responses

### Add After Validation (v1.x)

- [ ] Visual mapping test harness ("dry run" with sample payload)
- [ ] Mapping rule versioning and rollback
- [ ] JOLT import for bulk JSON reshape migrations
- [ ] Additional standard auth plugins beyond legacy set (mTLS, OIDC device flow)

### Future Consideration (v2+)

- [ ] Multi-step orchestration / saga across endpoints
- [ ] Distributed token cache and horizontal scale
- [ ] Hot-load PF4J plugin JARs without restart
- [ ] Self-service connector marketplace

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Legacy contract parity | HIGH | HIGH | P1 |
| Auth plugin + Groovy | HIGH | HIGH | P1 |
| Data mapping engine | HIGH | HIGH | P1 |
| React admin (config) | HIGH | MEDIUM | P1 |
| Monitoring (logs/metrics/health) | HIGH | MEDIUM | P1 |
| Gateway metadata export | MEDIUM | LOW | P1 |
| Visual mapping drag-drop | MEDIUM | HIGH | P2 |
| Script sandbox hardening | MEDIUM | MEDIUM | P2 |
| Workflow orchestration | LOW | HIGH | P3 |

## Competitor Feature Analysis

| Feature | Kong / APISIX (gateway) | Apache Camel (ESB) | Our Approach |
|---------|-------------------------|----------------------|--------------|
| Outbound vendor auth | Limited plugins | Rich EIP + custom beans | AuthProvider SPI + Groovy |
| Data transformation | JSON plugins | Jackson + Groovy DSL | Declarative mapping module + scripts |
| Admin UI | Kong Manager / ADC | Hawtio / none | Custom React Ant Design console |
| Legacy monolith replacement | Not designed for | Possible but heavy | Purpose-built spec-driven connector hub |
| Drop-in URL compat | Route rewrite | Route DSL | LegacyCompatFilter + catalog |

## Sources

- Existing `system-thirdpart` controller inventory (~60 RestControllers)
- `.planning/codebase/INTEGRATIONS.md` — current connector capabilities
- `.planning/PROJECT.md` — user-defined scope and constraints
- Industry: Kong plugins, Camel components patterns (conceptual comparison)

---
*Feature research for: API Connector*
*Researched: 2026-06-17*
