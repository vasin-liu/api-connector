---
status: testing
phase: 03-orchestrator-pipeline-integration
source: [03-VERIFICATION.md]
started: 2026-07-19T17:52:00+08:00
updated: 2026-07-19T17:52:00+08:00
---

## Current Test

number: 1
name: Confirm mapping-enabled=false runtime toggle (ops smoke)
expected: |
  With integration.invoke.mapping-enabled=false, mapped connectors pass body through without mapRequest/mapResponse
awaiting: user response

## Tests

### 1. Confirm mapping-enabled=false runtime toggle (ops smoke)
expected: With integration.invoke.mapping-enabled=false, mapped connectors pass body through without mapRequest/mapResponse
result: [pending]

### 2. Confirm publish invalidates ResolvedMappingCache
expected: After admin/publish of a connector, next invoke picks up new mapping without restart
result: [pending]

### 3. Confirm PIPELINE_ERROR audit outcome on request-side failure
expected: Failed mapRequest/auth produces audit outcome=PIPELINE_ERROR(<stage>) then structured API error
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
