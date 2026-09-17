---
phase: 2
slug: data-mapping-engine
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-17
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Maven Surefire |
| **Config file** | Root `pom.xml`, module `api-connector-mapping/pom.xml` |
| **Quick run command** | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test` |
| **Full suite command** | `.\mvnw-jdk21.ps1 clean verify` |
| **Estimated runtime** | ~90s (mapping module); ~150s full |

---

## Sampling Rate

- **After every task commit:** Run module-scoped quick command for touched module
- **After every plan wave:** Run `.\mvnw-jdk21.ps1 clean verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 150 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | MAP-01 | — | Publish rejects invalid JSONPath | unit | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=MappingSpecValidatorTest` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 2 | MAP-01 | — | rename + coerce declarative rules | unit | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=DeclarativeRuleExecutorTest` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 3 | MAP-02 | — | nest + array_map rules | unit | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=ArrayMapRuleTest` | ❌ W0 | ⬜ pending |
| 02-04-01 | 04 | 4 | MAP-03 | — | Groovy mapping script bindings | unit | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=GroovyMappingScriptProviderTest` | ❌ W0 | ⬜ pending |
| 02-04-02 | 04 | 4 | MAP-03 | — | Mapping script compile on publish | unit | `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=ConnectorPublishListenerTest` | ✅ | ⬜ pending |
| 02-05-01 | 05 | 5 | MAP-04 | — | Error JSON → legacy shape | unit | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=ErrorMappingEngineTest` | ❌ W0 | ⬜ pending |
| 02-05-02 | 05 | 5 | MAP-07 | — | Passthrough skips MappingEngine | unit | `.\mvnw-jdk21.ps1 -pl api-connector-engine -am test -Dtest=PassthroughMappingGuardTest,MappingConfigResolverTest` | ❌ W0 | ⬜ pending |
| 02-06-01 | 06 | 6 | MAP-05 | — | JDBC persist + publish reload | integration | `.\mvnw-jdk21.ps1 -pl api-connector-persistence -am test -Dtest=MappingPublishIntegrationTest` | ❌ W0 | ⬜ pending |
| 02-06-02 | 06 | 6 | MAP-01 | — | SM4 TransformStep SPI | unit | `.\mvnw-jdk21.ps1 -pl api-connector-mapping -am test -Dtest=Sm4TransformStepTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `api-connector-mapping` module in root `pom.xml` + BOM entry
- [ ] `MappingSpecValidatorTest.java` — rejects invalid JSONPath
- [ ] `DeclarativeRuleExecutorTest.java` — rename skeleton
- [ ] `src/test/resources/mapping/demo-rename.yaml` — fixture spec
- [ ] Extend `ConnectorPublishListenerTest` — mapping script compile case

*Wave 0 installs test stubs for new module before feature tasks.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CETC SM4 interop | SM4 transform | No Wave 1 vendor sandbox in CI | Compare output with `CetcUtils.sm4Encrypt` golden vector |
| Legacy error parity per vendor | MAP-04 | Golden files in Phase 6 | Spot-check one vendor from system-thirdpart audit |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 150s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
