---
status: testing
phase: 01-auth-plugin-architecture
source: [01-VERIFICATION.md]
started: 2026-06-17T08:10:00Z
updated: 2026-06-17T08:10:00Z
---

## Current Test

number: 1
name: Legacy auth inventory completeness (ROADMAP SC#6 / D-03)
expected: |
  Spot-check docs/legacy-auth-inventory.md rows against read-only scan of
  D:\Work\99_Code\ITS\suntek-system\system-thirdpart. All Wave 1 vendors
  represented with correct profile_id and implementation path (Java vs Groovy).
awaiting: user response

## Tests

### 1. Legacy auth inventory completeness (ROADMAP SC#6 / D-03)
expected: All Wave 1 vendors (IDPS, Gaode, Baidu, Hikvision, TrafficBrain, etc.) represented with correct profile_id and implementation path.
result: [pending]

### 2. Operator-facing Groovy auth configuration
expected: Publish connector with auth.type groovy_auth_script and inline script; invoke after publish applies auth headers; second invoke uses compile cache.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
