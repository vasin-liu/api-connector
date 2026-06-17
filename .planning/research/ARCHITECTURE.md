# Architecture Research

**Domain:** Spec-driven API integration hub replacing monolithic third-party module
**Researched:** 2026-06-17
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Driving Adapters (Inbound)                      │
├──────────────────────────────────────────────────────────────────────────┤
│  IntegrationProxyController   LegacyCompatFilter   IntegrationAdminController │
│  (unified invoke API)         (old URL → code3rd)  (BFF for React console)   │
├──────────────────────────────────────────────────────────────────────────┤
│                         Application / Engine Layer                        │
├──────────────────────────────────────────────────────────────────────────┤
│  IntegrationInvokeService → IntegrationOrchestrator → MappingEngine (NEW)   │
│         │                         │                      │                  │
│         │                    AuthEngine              AuthContext            │
│         │                    (plugin registry)       (token/signatures)     │
│         └────────────────── HttpTransport → Third-party HTTP APIs           │
├──────────────────────────────────────────────────────────────────────────┤
│                         Domain Core (framework-free)                        │
├──────────────────────────────────────────────────────────────────────────┤
│  ConnectorSpec, EndpointSpec, MappingSpec (NEW), AuthProfile, Invocation*  │
├──────────────────────────────────────────────────────────────────────────┤
│                         Driven Adapters (Outbound / Persistence)          │
├──────────────────────────────────────────────────────────────────────────┤
│  JdbcConnectorConfigStore   Builtin Java Catalogs   React static (/console/) │
│  Prometheus / Actuator      Gateway metadata export (NEW)                   │
└──────────────────────────────────────────────────────────────────────────┘
         ▲                              │
         │                              ▼
   API Gateway (caller auth)     60+ vendor HTTP APIs
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| ConnectorSpec / Catalog | Define vendor endpoints, auth profile, mapping refs | Java Catalog interfaces + DB `SPEC_JSON` |
| AuthEngine | Select AuthProvider by profile; build AuthContext | Registry of Java + Groovy providers |
| MappingEngine (new) | Transform request/response/error JSON | Declarative rules + Groovy scripts |
| IntegrationOrchestrator | Pipeline: resolve → map in → auth → HTTP → map out | Existing `DefaultIntegrationOrchestrator` extended |
| LegacyCompatFilter | Map old URL prefixes to code3rd/endpoint | Servlet filter; table driven from published specs |
| Admin BFF | CRUD connectors, mappings, scripts, export gateway routes | Spring `@RestController` under `/api/v1/admin` |
| React Console | Config UI, log viewer, metrics dashboards | Vite build → `api-connector-ui` JAR static |
| Gateway metadata exporter | Emit which routes need gateway auth | OpenAPI `x-gateway-auth` or JSON export endpoint |

## Recommended Project Structure

```
api-connector/
├── api-connector-domain/       # Models: MappingSpec, AuthContext, ports
├── api-connector-spec/         # ConnectorSpec parser; endpoint + mapping schema
├── api-connector-auth/         # AuthProvider SPI, built-in profiles, Groovy adapter
├── api-connector-mapping/      # NEW: MappingEngine, rules, Groovy mapping scripts
├── api-connector-scripting/    # NEW (optional): shared Groovy JSR-223 sandbox + cache
├── api-connector-engine/       # Orchestrator pipeline wires auth + mapping + transport
├── api-connector-api/          # Proxy + admin + legacy + gateway export controllers
├── api-connector-persistence/  # JDBC: mapping rules, script refs, gateway flags
├── api-connector-connectors/   # Builtin vendor catalogs (migrate from old controllers)
├── api-connector-ui/           # React + Ant Design → static/console/
├── api-connector-app/          # Spring Boot composition root
└── api-connector-compat-tests/ # NEW (recommended): contract tests vs old module shapes
```

### Structure Rationale

- **Separate `api-connector-mapping`:** Mapping is substantial enough to own domain ports and tests independently of HTTP transport.
- **Optional `api-connector-scripting`:** Shared Groovy compilation cache used by both auth and mapping avoids duplication.
- **`api-connector-compat-tests`:** Drop-in replacement requires automated contract verification per legacy controller domain.

## Architectural Patterns

### Pattern 1: Hexagonal Ports for Auth and Mapping

**What:** Domain defines `AuthProvider` and `MappingTransformer` ports; adapters in auth/mapping modules.
**When to use:** Any extensibility point (Java plugin or Groovy script).
**Trade-offs:** More modules upfront; pays off when 60+ vendors vary.

### Pattern 2: Spec-Driven Integration Pipeline

**What:** Single orchestration pipeline; behavior differences live in ConnectorSpec not Controller code.

```
Invoke → resolve endpoint → mapRequest → applyAuth → HTTP → evaluateSuccess → mapResponse → return
```

**When to use:** Replacing per-vendor Controller/Client/InvokeService triplets.
**Trade-offs:** Upfront spec authoring cost; massive reduction in Java duplication.

### Pattern 3: Legacy Adapter Layer

**What:** `LegacyCompatFilter` + dedicated legacy endpoint specs that preserve exact URL/method/body.

**When to use:** Drop-in migration requirement.
**Trade-offs:** Permanent compat layer until all callers migrate to unified API (may never remove).

### Pattern 4: Embedded Admin SPA (Same Port)

**What:** React build output in `api-connector-ui` JAR; Spring serves `/console/**` static; API calls same origin `/api/v1/admin`.

**When to use:** Ops wants single deployable unit (existing pattern).
**Trade-offs:** Frontend release tied to backend JAR unless split pipeline later.

## Data Flow

### Proxy Invoke Flow (new pipeline)

```
Client POST /api/v1/integrations/{code3rd}/endpoints/{id}/invoke
    → IntegrationInvokeService (rate limit, resolve)
    → MappingEngine.mapRequest(body, endpoint.mapping.in)
    → AuthEngine.apply(profile, credentials) → AuthContext
    → Merge auth mutations into HTTP request
    → HttpTransport.exchange
    → ResponseEvaluator (successWhen jsonpath)
    → MappingEngine.mapResponse(body, endpoint.mapping.out)
    → ProxyInvokeResponse (platform status + vendor body)
```

### Legacy URL Flow

```
Client POST /idps/some/old/path
    → LegacyCompatFilter resolves (code3rd, legacyEndpointId)
    → Same pipeline as above with legacy mapping profile
    → Response shaped to match old Controller JSON exactly
```

### Admin Config Flow

```
React Console → Admin BFF → JdbcConnectorConfigStore
    → publish triggers ConnectorConfigSyncService
    → ConnectorRegistry in-memory refresh
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Single instance (current ITS) | In-memory registry + OAuth cache sufficient |
| 2-5 instances behind LB | Sticky sessions or externalize OAuth token cache (Redis) |
| High QPS per vendor | Per-code3rd rate limits (exists); connection pool tuning on HttpClient |

### Scaling Priorities

1. **First bottleneck:** Synchronous Groovy scripts on hot paths — compile/cache scripts; prefer declarative mapping for hot endpoints.
2. **Second bottleneck:** DB sync on every publish — already event-driven on publish; avoid per-request DB reads.

## Anti-Patterns

### Anti-Pattern 1: One Java Controller Per Vendor (old system-thirdpart)

**What people do:** Copy Controller + Client + InvokeService for each vendor.
**Why it's wrong:** 60+ copies diverge; impossible to add cross-cutting mapping/monitoring uniformly.
**Do this instead:** One proxy + spec per vendor in Catalog.

### Anti-Pattern 2: Mapping Logic in API Controllers

**What people do:** `@PostMapping` methods manually map fields.
**Why it's wrong:** Not configurable; blocks admin UI and drop-in parity testing.
**Do this instead:** MappingEngine driven by published rules.

### Anti-Pattern 3: Caller Auth Inside Integration Service

**What people do:** Duplicate gateway JWT validation in integration layer.
**Why it's wrong:** Policy drift between gateway and service; user chose gateway-owned auth.
**Do this instead:** Export metadata; gateway enforces; service trusts internal network or mTLS to gateway.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| ITS API Gateway | Import route + auth policy from export endpoint | Metadata: `gatewayAuthRequired: true/false` per route |
| 60+ vendor HTTP APIs | Outbound via JdkHttpTransport | Per-vendor TLS, proxy, timeouts in connector client config |
| Prometheus | Scrape `/actuator/prometheus` | Tag metrics with `code3rd`, `endpointId`, `outcome` |
| MySQL (prod) | JDBC connector store | H2 for dev; same schema |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| api ↔ engine | Spring beans implementing domain ports | No direct HTTP between modules |
| engine ↔ auth/mapping | Domain SPI interfaces | Engine depends on abstractions only |
| ui ↔ api | REST JSON same origin | BFF aggregates for console screens |
| connectors ↔ spec | Java Catalog annotations scanned at startup | DB overrides for published configs |

## Sources

- `.planning/codebase/ARCHITECTURE.md` — verified existing patterns
- Enterprise integration patterns (ESB / iPaaS-lite)
- User architecture decisions (gateway auth, same-port UI, hexagonal rewrite)

---
*Architecture research for: API Connector*
*Researched: 2026-06-17*
