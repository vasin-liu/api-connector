# Stack Research

**Domain:** Enterprise third-party API integration hub (Java backend + React admin)
**Researched:** 2026-06-17
**Confidence:** HIGH (backend brownfield); MEDIUM (mapping engine + React UI migration)

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java | 21 (LTS) | Runtime | Already in use; virtual threads, records, pattern matching fit orchestration code |
| Spring Boot | 4.0.x | Application framework | Existing stack; Web, JDBC, Security, Actuator, Validation already wired |
| Maven | 3.9.x | Multi-module build | Existing 11-module layout; flatten plugin for `${revision}` |
| Apache Groovy | 4.0.x (`groovy-jsr223`) | Auth/mapping script extension | Team choice; seamless Java interop, mature JSR-223 embedding |
| React | 18.3.x | Admin UI | Team mandate (replace Vue) |
| Vite | 6.x | Frontend build | Already used; fast HMR, `base: '/console/'` for embedded static assets |
| Ant Design | 5.26.x | UI component library | Team choice; enterprise tables/forms for connector CRUD and log viewers |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jayway JsonPath | 2.10.x | Response success evaluation | Already in engine; extend for mapping source paths |
| Jackson | 2.18.x (via Spring Boot BOM) | JSON tree manipulation | Mapping engine intermediate representation |
| BouncyCastle | 1.80+ | HMAC / 国密 | Already in auth; extend for SM profiles used by legacy vendors |
| Micrometer + Prometheus registry | via Spring Boot Actuator | Metrics export | `/actuator/prometheus` for QPS, latency, error rate |
| springdoc-openapi | 3.0.x | API docs | Already present; document admin BFF + proxy APIs |
| Groovy Sandbox (optional) | — | Script isolation | If untrusted scripts ever edited via UI; v1 can use trusted-admin-only scripts |
| frontend-maven-plugin | 1.15.x | Build React into JAR | Keep same single-port deployment model |

### Mapping Engine Candidates

| Approach | Recommendation | Notes |
|----------|----------------|-------|
| Declarative JSON mapping DSL (field paths + transforms) | **Primary** | Covers 80% cases; visual UI can edit declaratively |
| Groovy scripts | **Extension** | Complex vendor-specific logic; receives `AuthContext` + input JSON |
| JOLT / JSONata | Alternative for pure JSON reshape | JOLT good for structural shifts; less type coercion; optional v1.x add-on |
| MapStruct | Java compile-time only | Good for fixed DTOs, not runtime-configured connectors |

**Recommendation:** New `api-connector-mapping` module with Jackson `JsonNode` pipeline + declarative rule model + Groovy `MappingScript` hook.

### Auth Plugin Architecture

| Layer | Implementation |
|-------|----------------|
| Built-in profiles | Java `AuthProvider` SPI (existing) — one class per standard profile |
| Plugin JARs | Java `ServiceLoader` / Spring `spring.factories` style registration in `api-connector-auth` |
| Groovy scripts | `GroovyAuthScript` implementing same `AuthProvider` contract via adapter; compiled once, cached |
| Auth context | Immutable `AuthContext` record passed through orchestrator → mapping → transport |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| JUnit 5 + AssertJ | Unit tests | Domain + mapping rules + auth profiles |
| WireMock | Integration tests | Vendor HTTP simulation; contract tests for legacy URL compat |
| Spring Boot Test | Slice/integration | Admin API + invoke path |
| Testcontainers (optional) | DB integration | MySQL/H2 for persistence tests |

## Installation

```bash
# Backend — add to api-connector-auth or new api-connector-scripting module
# pom.xml dependency:
#   org.apache.groovy:groovy-jsr223:4.0.x

# Frontend — api-connector-ui/frontend (replace Vue deps)
npm create vite@latest . -- --template react-ts
npm install antd @ant-design/icons react-router-dom
npm install -D @types/react @types/react-dom
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Groovy scripts | GraalJS | Team prefers JS-only scripting culture |
| Declarative mapping DSL | JOLT chains | Mapping is purely structural JSON reshape, no types |
| In-process plugins (SPI) | PF4J OSGi | Need hot-deploy third-party JARs without restart |
| JDK HttpClient | WebClient/Feign | Need reactive stack or declarative HTTP clients per endpoint |
| H2 default | MySQL prod | Production ITS deployments already use MySQL |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Vue 3 for new UI work | Explicit project decision | React + Ant Design |
| Copy-paste from `system-thirdpart` | Forbidden dependency | Re-spec connectors in Catalog/YAML |
| Feign per vendor (old pattern) | 60+ duplicate clients | Spec-driven single orchestrator |
| Nashorn / Rhino JS | Removed/deprecated on modern JDK | Groovy 4 or GraalJS |
| Flyway/Liquibase (v1) | Existing raw SQL schema bootstrap works | Extend `schema.sql` incrementally until migration pain justifies tool |

## Stack Patterns by Variant

**If mapping rule is simple field rename:**
- Use declarative path map in `ConnectorSpec` / endpoint mapping section
- Because testable without script sandbox concerns

**If vendor auth is non-standard (legacy ITS vendors):**
- Groovy `AuthScript` with access to credentials + request template
- Because faster than shipping new Java profile per vendor

**If drop-in legacy URL required:**
- Keep `LegacyCompatFilter` + expand route table from published connector metadata
- Because gateway and callers expect old paths

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Spring Boot 4.0.6 | Java 21 | Current project baseline |
| Groovy 4.0.x | Java 21 | Use `groovy-jsr223`, not Groovy 2.x |
| Ant Design 5.26.x | React 18 | Ant Design 5 requires React 16+ |
| Vite 6 | Node 20 LTS | Matches existing `frontend-maven-plugin` Node pin |
| Micrometer | Spring Boot Actuator 4 | Enable `management.endpoints.web.exposure` for prometheus |

## Sources

- `/apache/groovy` (Context7) — JSR-223 embedding, Java interop
- `/ant-design/ant-design` v5.26.2 (Context7) — Form, Table, Layout for admin console
- Existing `.planning/codebase/STACK.md` — verified project versions
- Spring Boot 4 Actuator docs — Prometheus metrics export

---
*Stack research for: API Connector (system-thirdpart replacement)*
*Researched: 2026-06-17*
