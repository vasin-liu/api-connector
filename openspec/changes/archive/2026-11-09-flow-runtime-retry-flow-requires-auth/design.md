# Design

## Context

See `proposal.md`. `FlowRuntime.retryFlow` calls `authenticate` with `then = REPLAY_REQUEST`. `authenticate` returns `FAILURE` when `plan.authentication()` is absent or has no steps, and that terminal result leaves the request loop, so the 503 response is not followed by another send. When the authentication flow exists and succeeds, the same request step continues and renders a new request. `RETRY_REQUEST` stays in that loop and never calls `authenticate`. Replay downgrade (`UNSAFE` / `UNKNOWN`, or `allowAutomaticReplay: false`) and `maxAttempts` already apply only to `RETRY_REQUEST` and `REPLAY_REQUEST`; this change does not alter them.

## Goals / Non-Goals

**Goals:**

- One `flow-runtime` requirement that states the two retry actions as they run today.
- A FakeTransport test for the missing-authentication-flow failure, plus a small `RETRY_REQUEST` resend in the same test.
- Artemis POST remains the witness that a declared re-sign flow runs before the second business send.

**Non-Goals:**

- Changing `FlowRuntime`, replay downgrade, `maxAuthAttempts`, cooldown, or stale-binding `RETRY_FLOW`.
- A new vendor protocol, or duplicating the Artemis HMAC / PMS token vectors inside the new test.
- Making `RETRY_FLOW` succeed without an authentication flow.

## Decisions

### D1. Freeze the current route

**Choice:** Document `RETRY_FLOW` → authentication flow → optional resend. Do not add a branch that retries freshness inside the business flow when authentication is absent.

**Why:** Artemis GET, Artemis POST, IDPS, and Gaode already declare a re-sign authentication flow for this reason. Changing the route would rewrite those plans' meaning.

**Alternatives:** Treat missing authentication as "re-run business from the top" — that is a different runtime, and it would hide the POST design mistake this change exists to prevent.

### D2. The failure witness asserts outcome and send count

**Choice:** The new test uses a minimal definition: one business POST, `503 → RETRY_FLOW`, no `authentication` key, `maxAuthAttempts` at least 1. Expect `StepOutcomeType.FAILURE` and exactly one `FakeTransport` invocation.

**Why:** `authenticate` builds that `FAILURE` with an empty HTTP status. The observable contract is "no second send", not "the result status is 503".

**Alternatives:** Assert the result's HTTP status is 503 — that would fail against current code and force a runtime change this change does not want.

### D3. RETRY_REQUEST witness stays replay-legal

**Choice:** Same test class, second definition: `replayability: SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`, `503 → RETRY_REQUEST`, no authentication flow. First response 503, second 200. Expect two sends and `SUCCESS`.

**Why:** PMS already shows this on a real definition. A tiny definition locks the action itself, without depending on the PMS token formula. Downgrade and attempt limits stay as they are; the scenario states the SAFE / automatic / attempts≥2 precondition.

### D4. Positive RETRY_FLOW stays on Artemis POST

**Choice:** Do not add another nonce fixture. Task verification re-runs `TrafficHikvisionArtemisPostProtocolTest`, whose second send already differs in nonce and signature after the re-sign authentication flow.

**Why:** That test is the published vector for "authentication flow runs, then one more business send".

## Risks / Trade-offs

- [Empty authentication steps look like a declared flow] → The runtime treats empty steps as missing. The requirement says both "no flow" and "no steps" end as `FAILURE`.
- [Readers treat `RETRY_FLOW` as exempt from auth limits] → `maxAuthAttempts` / depth still return `AUTH_ATTEMPT_EXCEEDED` before the missing-flow check. Out of scope; the new fixture keeps `maxAuthAttempts` at the usual 2 so it hits the missing-flow path.
- [A green test is taken as permission to edit `FlowRuntime`] → Tasks say to stop and update this change if the observed sends do not match. No drive-by runtime edit.

## Migration Plan

Spec and test only. Rollback is dropping the new test and the added requirement. Published protocol YAML does not change.

## Open Questions

None.
