# Spec Delta

## ADDED Requirements

### Requirement: RETRY_FLOW runs authentication before another send
When a completed response matches `RETRY_FLOW`, the runtime SHALL run that definition's authentication flow before sending another request for the same step. If the definition has no authentication flow, or that flow has no steps, the execution MUST end as `FAILURE` and MUST NOT send another HTTP request.

#### Scenario: Missing authentication flow fails without a second send
- **WHEN** FakeTransport returns a status that matches `RETRY_FLOW` and the definition declares no authentication flow
- **THEN** the execution outcome is `FAILURE` and FakeTransport records exactly one HTTP request

#### Scenario: Declared authentication flow runs before the next business send
- **WHEN** a status matches `RETRY_FLOW` and the authentication flow assigns a new nonce, then completes without its own HTTP call
- **THEN** exactly one additional business request is sent and that request carries the new nonce

### Requirement: RETRY_REQUEST resends without starting authentication
`RETRY_REQUEST` SHALL render another attempt of the current request when replay policy allows it, and MUST NOT start the authentication flow.

#### Scenario: Safe replay sends again with no authentication flow
- **WHEN** a safely replayable request matches `RETRY_REQUEST` and the definition declares no authentication flow
- **THEN** FakeTransport records a second request for that step and the authentication flow does not run
