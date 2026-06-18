---
phase: 3
slug: orchestrator-pipeline-integration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-18
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + AssertJ; Spring Boot Test 4.0.6 (integration); WireMock 3.13.1 (vendor stubbing, test scope) |
| **Config file** | none — Maven Surefire convention (`*Test.java` auto-discovered); Surefire configured in `api-connector-parent/pom.xml` |
| **Quick run command** | `.\mvnw-jdk21.ps1 -pl api-connector-engine test -Dtest=DefaultIntegrationOrchestratorTest` |
| **Full suite command** | `.\mvnw-jdk21.ps1 clean verify` |
| **Estimated runtime** | quick ~20-40s; full suite ~minutes |

> Module-scoped integration runs need `-am` to build upstream modules: `.\mvnw-jdk21.ps1 -pl api-connector-app -am test -Dtest=InvokeIntegrationTest`.

---

## Sampling Rate

- **After every task commit:** Run `.\mvnw-jdk21.ps1 -pl api-connector-engine test -Dtest=DefaultIntegrationOrchestratorTest`
- **After every plan wave:** Run `.\mvnw-jdk21.ps1 -pl api-connector-app -am test`
- **Before `/gsd-verify-work`:** `.\mvnw-jdk21.ps1 clean verify` must be green
- **Max feedback latency:** ~40 seconds (quick), full suite at wave merge

---

## Per-Task Verification Map

> Populated by the planner against final task IDs. Source map (requirement → behavior → command) from RESEARCH.md `## Validation Architecture`.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | 01 | 0 | MAP-06 | T-03-01 | HMAC signs mapped+transformed body | unit | `...-Dtest=DefaultIntegrationOrchestratorTest` | ⚠️ method ❌ W0 | ⏳ pending |
| TBD | 01 | 0 | MAP-06 | T-03-01 | HMAC over mapped body (e2e) | integration | `...-pl api-connector-app -am -Dtest=InvokeIntegrationTest` | ⚠️ method ❌ W0 | ⏳ pending |
| TBD | — | — | PIPE-01 | — | full pipeline resolve→map→auth→HTTP→eval→mapResponse | integration | `...-Dtest=InvokeIntegrationTest` | ⚠️ method ❌ W0 | ⏳ pending |
| TBD | — | — | PIPE-02 | T-03-EoP | legacy + unified identical core result (D-08) | integration | `...-Dtest=LegacyCompatIntegrationTest` | ⚠️ method ❌ W0 | ⏳ pending |
| TBD | — | — | PIPE-03 | T-03-ID | audit line carries requestId + outcome | unit | `...-pl api-connector-api -Dtest=InvokeAuditLoggerTest` | ❌ W0 | ⏳ pending |
| TBD | — | — | PIPE-03 | T-03-Tamper | correlation id from X-Request-Id / UUID fallback | unit | `...-pl api-connector-api -Dtest=IntegrationInvokeServiceTest` | ❌ W0 | ⏳ pending |
| TBD | — | — | PIPE-04 | — | streaming request-side applied, chunks raw, warn-once | integration | `...-Dtest=InvokeIntegrationTest` | ⚠️ method ❌ W0 | ⏳ pending |

*Status: ⏳ pending → ✅ green → ❌ red → ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `DefaultIntegrationOrchestratorTest.java` (exists) — ADD request-ordering, response-mirror, error-routing, passthrough-skip, pre-stream-failure methods (MAP-06, PIPE-01, PIPE-04, D-02/D-03)
- [ ] `InvokeIntegrationTest.java` (exists) — ADD `hmacSignsMappedRequestBody`, `unifiedInvokeRunsFullPipeline`, SSE streaming test (MAP-06, PIPE-01, PIPE-04)
- [ ] `LegacyCompatIntegrationTest.java` (exists) — ADD `legacyAndUnifiedCoreResultParity` (PIPE-02, D-08)
- [ ] `IntegrationInvokeServiceTest.java` (NEW) — correlation-id resolution + MDC put/clear (PIPE-03, D-10/D-11)
- [ ] `InvokeAuditLoggerTest.java` (NEW or extend) — assert requestId + outcome in audit line (PIPE-03, D-13/D-14)
- [ ] No new framework install — JUnit 5 / Spring Boot Test / WireMock all present

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | — | All phase behaviors have automated verification. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 40s (quick)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
