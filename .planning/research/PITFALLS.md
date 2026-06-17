# Pitfalls Research

**Domain:** Legacy monolith replacement with spec-driven API integration platform
**Researched:** 2026-06-17
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Silent Contract Drift During Migration

**What goes wrong:**
New implementation returns HTTP 200 with slightly different JSON field names, null vs missing fields, or error code strings. Callers in production break subtly.

**Why it happens:**
Developers test "happy path" only against new unified API, not exact legacy controller responses. Mapping rules approximate but don't match edge cases.

**How to avoid:**
- Build `api-connector-compat-tests` with golden files captured from old `system-thirdpart`
- Per-vendor contract tests for every legacy URL + representative payloads
- CI gate: no cutover until 100% compat test pass

**Warning signs:**
- "Looks the same" manual comparisons
- Missing tests for error responses and empty bodies
- Unified API works but legacy filter path untested

**Phase to address:**
Vendor migration phases — each phase must include compat tests before marking requirements done.

---

### Pitfall 2: Groovy Scripts on the Hot Path Without Caching

**What goes wrong:**
Every invoke re-parses/compiles Groovy; latency spikes 10-50x under production QPS.

**Why it happens:**
JSR-223 naive usage creates new `GroovyShell` per request.

**How to avoid:**
- Compile scripts once on publish; cache by `(connectorId, scriptVersion)` hash
- Warm cache on `ApplicationReadyEvent` for published connectors
- Prefer declarative mapping for high-QPS endpoints; Groovy only when necessary

**Warning signs:**
- CPU high on integration nodes while QPS moderate
- P99 latency correlates with script-enabled connectors

**Phase to address:**
Auth plugin + scripting infrastructure phase.

---

### Pitfall 3: Auth Context Not Threaded Through Pipeline

**What goes wrong:**
Mapping step needs token from auth step but AuthContext is lost; scripts re-fetch tokens or duplicate OAuth calls → rate limits / inconsistent signatures.

**Why it happens:**
Auth applied only at HTTP layer; mapping runs before/after without shared context object.

**How to avoid:**
- Define immutable `AuthContext` in domain; orchestrator passes through all stages
- Document lifecycle: `mapRequest` → `auth.apply` → `merge` → `transport` → `mapResponse(authContext)`

**Warning signs:**
- Duplicate OAuth token requests in logs per single invoke
- HMAC signatures use wrong timestamp because mapping mutated body after signing

**Phase to address:**
Mapping engine + orchestrator integration phase.

---

### Pitfall 4: Underestimating 60+ Vendor Migration Surface

**What goes wrong:**
Project stalls at 70% coverage; business blocks cutover; dual maintenance continues for months.

**Why it happens:**
Each old controller hides bespoke quirks (encoding, multipart, custom headers, non-JSON bodies).

**How to avoid:**
- Inventory all controllers with endpoint count and complexity tier (S/M/L)
- Migrate in batches but **cutover only when 100%** — track coverage matrix in REQUIREMENTS
- Start with hardest 5 vendors to surface pattern gaps early

**Warning signs:**
- "We'll handle the last 10 vendors after go-live" pressure
- No published coverage dashboard

**Phase to address:**
Roadmap vendor migration phases — ordered by risk, not alphabetically.

---

### Pitfall 5: React UI Rewrite Blocks Backend Progress

**What goes wrong:**
Team spends months rebuilding admin UI while migration stalls; ops can't configure new connectors.

**Why it happens:**
Big-bang Vue → React rewrite attempted before minimal admin APIs exist.

**How to avoid:**
- Phase 1 admin: API-first (REST for connector/auth/mapping CRUD)
- Minimal React screens per capability (table + form), not pixel-perfect parity with Vue
- Keep YAML/JSON editor fallback for complex mapping until visual editor matures

**Warning signs:**
- No working admin CRUD for mapping rules by mid-project
- Frontend and backend teams blocked on each other

**Phase to address:**
Admin BFF phase before full visual mapping editor.

---

### Pitfall 6: Gateway Auth Metadata Out of Sync

**What goes wrong:**
Gateway protects public endpoints or leaves sensitive legacy paths open because metadata wasn't exported after publish.

**Why it happens:**
Manual gateway config updates decoupled from connector publish events.

**How to avoid:**
- Store `gatewayAuthRequired` on each endpoint in spec
- Export endpoint on publish (webhook or pull API for gateway team)
- Integration test: metadata matches spec for every legacy route

**Warning signs:**
- Gateway routes updated manually in tickets
- Mismatch between console display and actual gateway policy

**Phase to address:**
Gateway metadata + endpoint security flags phase.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hardcode legacy routes in filter | Fast first vendor | Unmaintainable table | Never — drive from published spec |
| Skip error response mapping | Faster vendor port | Caller breaks on failures | Never for drop-in compat |
| Admin-only Groovy without sandbox | Faster v1 | Security incident if console compromised | v1 internal trusted network only |
| Keep Vue console temporarily | Avoid UI gap | Two UIs to maintain | Never — explicit out of scope |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| OAuth2 vendors (Baidu Wenxin) | Token in query stale | Cache per code3rd; refresh before expiry |
| HMAC vendors (Gaode traffic) | Sign after body mutation | Sign after final body; mapping order matters |
| 国密 SM2/SM3 | Wrong curve/provider | Use BouncyCastle profile; test against vendor sandbox |
| Multipart uploads (some legacy) | Force JSON mapping | Binary passthrough endpoint type |
| GBK/charset responses | Jackson default UTF-8 mojibake | Charset in endpoint spec |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Large response mapping | OOM on big JSON | Stream or truncate in logs; JsonNode reuse | >10MB vendor payloads |
| Synchronous vendor timeouts | Thread exhaustion | Virtual threads (enabled) + aggressive timeouts | Vendor slow >30s |
| Registry reload storm | Publish all connectors at once | Debounce sync; incremental registry update | Bulk import |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Groovy scripts access full JVM | RCE | Limit bindings; no `System.exit`; admin auth for script edit |
| Credentials in mapping scripts | Secret leak in logs | Pass secrets via AuthContext only; redact audit logs |
| Legacy paths without gateway auth flag | Data exposure | Explicit `gatewayAuthRequired` default true for sensitive domains |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Mapping editor shows raw JSON only | Ops errors | Form-based path builder + JSON preview |
| No dry-run test | Publish breaks prod | "Test invoke" in console with sample payload |
| Logs without correlation id | Cannot trace | Propagate `X-Request-Id` through pipeline |

## "Looks Done But Isn't" Checklist

- [ ] **Legacy compat:** Tested error responses, empty body, non-JSON, and charset — not just 200 OK
- [ ] **Auth plugin:** Token refresh and clock skew tested for OAuth/HMAC vendors
- [ ] **Mapping:** Round-trip type coercion (string ↔ number ↔ date) verified
- [ ] **Monitoring:** Prometheus dashboards show per-code3rd error rate, not just global health
- [ ] **Gateway export:** Generated after every publish; diff reviewed
- [ ] **React console:** Works on same port behind prod-like reverse proxy path `/console/`

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Contract drift discovered in UAT | HIGH | Golden test per failure; fix mapping; re-run full suite |
| Groovy perf regression | MEDIUM | Switch hot endpoints to declarative; add compile cache |
| Incomplete vendor coverage | HIGH | Freeze cutover; prioritize remaining vendors by business criticality |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Contract drift | Each vendor migration phase | Compat test pass rate 100% |
| Groovy perf | Scripting infrastructure | Load test P99 baseline |
| Auth context | Orchestrator refactor | Single OAuth call per invoke in logs |
| Migration surface | Roadmap coverage tracking | REQ traceability matrix complete |
| UI blocks ops | Admin BFF before UI polish | CRUD via API without UI |
| Gateway sync | Metadata export phase | Automated diff vs gateway config |

## Sources

- `.planning/codebase/CONCERNS.md` — known project risks
- Legacy `system-thirdpart` maintenance experience (inferred)
- Integration platform post-mortems (contract testing, script caching)

---
*Pitfalls research for: API Connector*
*Researched: 2026-06-17*
