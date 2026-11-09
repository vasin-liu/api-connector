# Tasks

## 1. Spec

- [x] 1.1 Confirm `openspec/changes/flow-runtime-retry-flow-requires-auth/specs/flow-runtime/spec.md` has two requirements: `RETRY_FLOW` runs the authentication flow before another send and a missing or empty flow ends as `FAILURE` with no second HTTP; `RETRY_REQUEST` resends the current request without starting authentication when replay is allowed. Verify `openspec validate "flow-runtime-retry-flow-requires-auth"` passes.

## 2. Missing-flow and RETRY_REQUEST tests

- [x] 2.1 Add a FakeTransport test whose definition has one business POST, `503 → RETRY_FLOW`, `maxAuthAttempts` of at least 1, and no authentication flow. Verify the outcome is `FAILURE` and exactly one HTTP invocation is recorded. Do not assert that the result's HTTP status is 503.
- [x] 2.2 In the same test class, add a definition with `replayability: SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`, `503 → RETRY_REQUEST`, and no authentication flow. Verify a 503 then a 200 produces `SUCCESS` and exactly two HTTP invocations.
- [x] 2.3 If either test disagrees with `FlowRuntime`, stop and update this change's spec and design. Do not change the retry route to make the test pass.

## 3. Existing witnesses

- [x] 3.1 Re-run `TrafficHikvisionArtemisPostProtocolTest` and verify the published second signature still differs while the body and `Content-MD5` stay fixed.
- [x] 3.2 Re-run `TrafficHikvisionPmsProtocolTest` and verify the 503 `RETRY_REQUEST` still sends a second request with the same body and token.
