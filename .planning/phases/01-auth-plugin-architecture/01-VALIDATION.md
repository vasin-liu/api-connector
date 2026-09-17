---
phase: 1
slug: auth-plugin-architecture
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-17
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Maven Surefire/Failsafe |
| **Config file** | Root `pom.xml`, `api-connector-app/pom.xml` (WireMock) |
| **Quick run command** | `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test` |
| **Full suite command** | `.\mvnw-jdk21.ps1 clean verify` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run module-scoped quick command for touched module
- **After every plan wave:** Run `.\mvnw-jdk21.ps1 clean verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | AUTH-03 | — | Script compile cache keyed by hash | unit | `.\mvnw-jdk21.ps1 -pl api-connector-scripting -am test` | ❌ W0 | ⬜ pending |
| 01-02-01 | 02 | 2 | AUTH-02 | — | Groovy script returns AuthOutcome | unit | `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test -Dtest=GroovyAuthScriptProviderTest` | ❌ W0 | ⬜ pending |
| 01-03-01 | 03 | 3 | AUTH-01 | — | TokenCache + publish compile hook | unit | `.\mvnw-jdk21.ps1 -pl api-connector-auth -am test -Dtest=TokenCacheTest` | ✅ | ⬜ pending |
| 01-04-01 | 04 | 4 | AUTH-04 | — | AuthContextSnapshot immutable with ext map | unit | `.\mvnw-jdk21.ps1 -pl api-connector-domain -am test -Dtest=AuthContextSnapshotTest` | ❌ W0 | ⬜ pending |
| 01-04-02 | 04 | 4 | AUTH-05 | — | Snapshot on InvocationResult for mapping | unit | `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=DefaultIntegrationOrchestratorTest` | ✅ | ⬜ pending |
| 01-05-01 | 05 | 4 | AUTH-06 | — | Structured error with details | integration | `.\mvnw-jdk21.ps1 -pl api-connector-app -am test -Dtest=InvokeIntegrationTest` | ✅ | ⬜ pending |
| 01-06-01 | 06 | 5 | AUTH-01 | — | Wave 1 catalogs + WireMock signed outbound | integration | `.\mvnw-jdk21.ps1 clean verify` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `api-connector-scripting` module with `ScriptCompileServiceTest.java`
- [ ] `GroovyAuthScriptProviderTest.java` — Groovy auth provider stub
- [ ] `AuthContextSnapshotTest.java` — snapshot record tests
- [ ] `api-connector-auth/src/test/resources/scripts/*.groovy` — sample scripts

*Wave 0 installs test stubs for new modules before feature tasks.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Legacy auth inventory completeness | Success criterion #6 | Requires read-only audit of system-thirdpart | Verify `docs/legacy-auth-inventory.md` has Wave 1 vendor rows |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
