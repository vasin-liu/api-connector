---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 03
status: Executing Phase 03
last_updated: "2026-06-18T07:22:51.978Z"
progress:
  total_phases: 8
  completed_phases: 2
  total_plans: 16
  completed_plans: 15
  percent: 25
---

# State: API Connector

**Last updated:** 2026-06-17
**Current phase:** 03
**Project mode:** yolo | granularity: standard | execution: parallel

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-06-17)

**Core value:** Zero-disruption replacement of system-thirdpart via configurable auth plugins and data mapping.
**Current focus:** Phase 03 — orchestrator-pipeline-integration

## Progress

| Phase | Name | Status | Plans |
|-------|------|--------|-------|
| 1 | Auth Plugin Architecture | ● Complete | 6/6 |
| 2 | Data Mapping Engine | ● Complete | 6/6 |
| 3 | Orchestrator Pipeline Integration | ◐ In Progress | 2/4 |
| 4 | Admin BFF & Gateway Metadata | ○ Pending | 0/0 |
| 5 | React Console & Observability | ○ Pending | 0/0 |
| 6 | Legacy Compat Test Harness | ○ Pending | 0/0 |
| 7 | Vendor Migration Wave 1 | ○ Pending | 0/0 |
| 8 | Vendor Migration Wave 2 & Cutover | ○ Pending | 0/0 |

**Requirements:** 13/44 complete

## Decisions

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-17 | Drop-in compat + all-at-once cutover | Caller impact minimization |
| 2026-06-17 | Gateway owns caller auth | Platform architecture |
| 2026-06-17 | Java auth plugins + Groovy scripts | Standard + legacy flexibility |
| 2026-06-17 | React + Ant Design replaces Vue | Team stack mandate |
| 2026-06-17 | mappingOverride is full MappingSpec block per endpoint | Mirrors authOverride; per-direction override resolved in 02-03 |
| 2026-06-18 | Transform pipeline is a distinct bean stage; orchestrator wires order in Phase 3 | ADR-002 D-20/D-21; prevents accidental crypto/auth reorder (MAP-06) |
| 2026-06-18 | SM4 transform resolves key via keyRef credential, never inline | Pitfall 6 — no secrets in spec/DB/logs |
| 2026-06-18 | Orchestrator finalizes request body (mapRequest + transform.applyRequest) BEFORE authenticate | MAP-06/D-01 — HMAC must sign the body actually sent to the vendor |
| 2026-06-18 | Null endpointSpec (legacy raw-path dispatch) is a mapping passthrough | D-04 — no resolvable mapping key; transforms still apply via spec.transform() |
| 2026-06-18 | Correlation id (X-Request-Id→X-Trace-Id→UUID, sanitized) via MDC + passed explicitly into audit event on sync AND stream paths | D-10/D-11/D-13 — stream callback thread differs; MDC alone would attribute requestId=- |

- [Phase ?]: 03-03 (PIPE-02): Option A - ThirdpartLegacyDispatcher.resolveEndpointId resolves a uniquely matching (method,path) endpointId before the shared invoke so matched legacy routes run mapping; ambiguous/absent matches stay null, preserving the engine D-04 passthrough invariant. Engine untouched (commit 0f1ad4a).

## Blockers

(None)

## Notes

- Codebase map available: `.planning/codebase/`
- Research complete: `.planning/research/SUMMARY.md`
- Old module reference: `D:\Work\99_Code\ITS\suntek-system\system-thirdpart` (read-only for contract capture, no code reuse)
- Phase 2 plan 02-01 complete: mapping spec foundation (`.planning/phases/02-data-mapping-engine/02-01-SUMMARY.md`)
- Phase 2 complete (02-06): TransformStep SPI + SM4 + JDBC publish reload (`.planning/phases/02-data-mapping-engine/02-06-SUMMARY.md`)

---

*State initialized: 2026-06-17*
