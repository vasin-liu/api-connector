---
phase: 3
slug: orchestrator-pipeline-integration
status: approved
nyquist_compliant: true
wave_0_complete: true
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
| 03-01-T1 | 03-01 | 1 | MAP-06 | T-03-01 | HMAC signs mapped+transformed body (ordering) | unit | `...-pl api-connector-engine -Dtest=DefaultIntegrationOrchestratorTest` | ⚠️ method (W0 task 03-01-T1) | ⏳ pending |
| 03-01-T1 | 03-01 | 1 | MAP-06 | T-03-01 | HMAC over mapped body (e2e) | integration | `...-pl api-connector-app -am -Dtest=InvokeIntegrationTest` | ⚠️ method (W0 task 03-01-T1) | ⏳ pending |
| 03-01-T1 | 03-01 | 1 | PIPE-01 | T-03-01 | full pipeline resolve→map→auth→HTTP→eval→mapResponse | integration | `...-pl api-connector-app -am -Dtest=InvokeIntegrationTest` | ⚠️ method (W0 task 03-01-T1) | ⏳ pending |
| 03-03-T1 | 03-03 | 2 | PIPE-02 | T-03-05 | legacy + unified identical core result (D-08) | integration | `...-pl api-connector-app -am -Dtest=LegacyCompatIntegrationTest` | ⚠️ method (W0 task 03-03-T1) | ⏳ pending |
| 03-02-T2 | 03-02 | 1 | PIPE-03 | T-03-02 | audit line carries requestId + outcome (sync) | unit | `...-pl api-connector-api -Dtest=InvokeAuditLoggerTest` | ❌ new (W0 task 03-02-T1) | ⏳ pending |
| 03-02-T3 | 03-02 | 1 | PIPE-03 | T-03-03 | correlation id from X-Request-Id / UUID fallback + CRLF sanitize | unit | `...-pl api-connector-api -Dtest=IntegrationInvokeServiceTest` | ❌ new (W0 task 03-02-T1) | ⏳ pending |
| 03-02-T3 | 03-02 | 1 | PIPE-03 | T-03-04 | streaming audit line carries captured requestId + outcome (SC#4 streaming, not back-compat defaults) | unit | `...-pl api-connector-api -Dtest=IntegrationInvokeServiceTest` | ❌ new (W0 task 03-02-T1) | ⏳ pending |
| 03-04-T1 | 03-04 | 2 | PIPE-04 | T-03-09 | streaming request-side applied, chunks raw, warn-once | integration | `...-pl api-connector-app -am -Dtest=InvokeIntegrationTest` | ⚠️ method (W0 task 03-04-T1) | ⏳ pending |
| 03-04-T1 | 03-04 | 2 | PIPE-04 | T-03-06 | pre-stream failure throws before any chunk (D-18) | unit | `...-pl api-connector-engine -Dtest=DefaultIntegrationOrchestratorTest#preStreamMappingFailureThrowsNoChunk` | ⚠️ method (W0 task 03-04-T1) | ⏳ pending |

*Status: ⏳ pending → ✅ green → ❌ red → ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `DefaultIntegrationOrchestratorTest.java` (exists) — ADD request-ordering, response-mirror, error-routing, passthrough-skip, pre-stream-failure methods (MAP-06, PIPE-01, PIPE-04, D-02/D-03) → bound to 03-01-T1 (ordering/mirror/passthrough) + 03-04-T1 (pre-stream-failure)
- [x] `InvokeIntegrationTest.java` (exists) — ADD `hmacSignsMappedRequestBody`, `unifiedInvokeRunsFullPipeline`, SSE streaming test (MAP-06, PIPE-01, PIPE-04) → bound to 03-01-T1 + 03-04-T1
- [x] `LegacyCompatIntegrationTest.java` (exists) — ADD `legacyAndUnifiedCoreResultParity` (PIPE-02, D-08) → bound to 03-03-T1
- [x] `IntegrationInvokeServiceTest.java` (NEW) — correlation-id resolution + MDC put/clear + streaming-audit requestId/outcome assertion (PIPE-03, D-10/D-11/D-13/D-14) → bound to 03-02-T1 (RED), green at 03-02-T3
- [x] `InvokeAuditLoggerTest.java` (NEW) — assert requestId + outcome in audit line (PIPE-03, D-13/D-14) → bound to 03-02-T1 (RED), green at 03-02-T2
- [x] No new framework install — JUnit 5 / Spring Boot Test / WireMock all present

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | — | All phase behaviors have automated verification. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (every NEW/extended test bound to a Wave-0 RED task: 03-01-T1, 03-02-T1, 03-03-T1, 03-04-T1)
- [x] No watch-mode flags
- [x] Feedback latency < 40s (quick)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved
