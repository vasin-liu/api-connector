# execution-observability Specification

## Purpose

Defines execution, flow, step, and request-attempt identifiers plus a decision trace that can explain challenge, retry, replay, and session choices without exposing secrets.

## Requirements

### Requirement: Nested attempt identifiers
Every execution SHALL record execution id, definition revision, plan id, flow id, step id, attempt id, and request attempt id. Authentication nested under a business step SHALL use a distinct flow-execution id while remaining under the same execution id.

#### Scenario: Challenge replay uses one execution
- **WHEN** a business request challenges, authenticates, and replays
- **THEN** there is one execution id, two flow-execution ids, distinct request-attempt ids for the first and rebuilt business calls, and authorization appears only on the replayed request

#### Scenario: Login failure does not mint a business replay attempt
- **WHEN** authentication fails
- **THEN** no additional business request-attempt is recorded after the triggering request

### Requirement: Decision trace explains control choices
The system SHALL record decisions for challenge, condition, retry, replay, session, and pipeline. Each decision SHALL include a decision id, type, action, and reason code. Policy that downgrades an action SHALL record both the matched transition and the final policy action.

#### Scenario: Challenge path is readable
- **WHEN** a 403 challenge leads to session generation increment and replay
- **THEN** the trace shows condition match, challenge bind, pipeline HMAC allowed, session generation change, and replay of the business template

#### Scenario: Shared authentication failure is marked
- **WHEN** waiters fail because the refresh owner failed
- **THEN** waiter traces include a session decision with a shared-failure reason rather than implying each waiter ran authentication

### Requirement: Secrets never enter the trace
Decision facts MAY include HTTP status, header names, JSONPath expressions, and generation numbers. Secret material, Authorization header values, token query values, and cookie values MUST be redacted.

#### Scenario: Authorization value redacted
- **WHEN** a replayed request includes an Authorization header
- **THEN** the trace may record that the header was set and MUST NOT record the header value
