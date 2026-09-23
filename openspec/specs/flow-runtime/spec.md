# flow-runtime Specification

## Purpose

Defines Flow as the single control-flow model: conditions, transitions, step outcomes, and scoped state mutations independent of raw HTTP status.

## Requirements

### Requirement: HTTP status is not the step outcome
The system SHALL classify a completed transport response into a StepOutcome. Transport success MUST NOT imply step success. Socket timeout or an incomplete write MUST NOT be classified as a normal HTTP failure.

#### Scenario: 200 is success
- **WHEN** transport completes with HTTP 200 and a matching success transition
- **THEN** the step outcome is SUCCESS

#### Scenario: 403 challenge is not failure
- **WHEN** transport completes with HTTP 403 and a challenge condition matches
- **THEN** the step outcome is CHALLENGE rather than FAILURE

#### Scenario: Incomplete transport after write is unknown outcome
- **WHEN** the request is written but the connection is lost before a response
- **THEN** the outcome is UNKNOWN_OUTCOME and MUST NOT be treated as a classified HTTP status or as TIMEOUT

#### Scenario: Deadline before write is timeout
- **WHEN** the execution deadline fires before the request is written to the transport
- **THEN** the outcome is TIMEOUT and the transport MUST NOT send the request

### Requirement: Transitions use first match
The system SHALL evaluate a step's transitions in list order and take the first matching condition. Empty `all` or `any` groups MUST fail validation. Header names MUST match case-insensitively; header values MUST match case-sensitively.

#### Scenario: Challenge beats permission JSON
- **WHEN** a 403 response has both `X-Challenge` and body `error=PERMISSION_DENIED` and the challenge transition is listed first
- **THEN** the system authenticates and MUST NOT fail as permission denied

#### Scenario: Permission denied does not authenticate
- **WHEN** a 403 response has `error=PERMISSION_DENIED` and no challenge header
- **THEN** the step fails and Authentication Flow MUST NOT start

#### Scenario: Unmatched 4xx fails
- **WHEN** transport completes with a 4xx that matches no transition
- **THEN** the default action is FAIL with FAILURE and no implicit authentication

### Requirement: Conditions use a structured AST
Conditions SHALL be Status, Header, JSONPath, Variable, All, Any, and Not. The system MUST NOT evaluate SpEL, OGNL, or MVEL. JSONPath SHALL support only root, property, array index, existence, and simple equality.

#### Scenario: Forbidden JSONPath is rejected at validate
- **WHEN** a condition uses a JSONPath filter expression or recursive descent
- **THEN** validation fails and the definition is not compiled

#### Scenario: Status and body must both match when required
- **WHEN** a transition requires status 401 and JSONPath `$.error` equals `UNAUTHORIZED` but the status is 403 with the same body
- **THEN** the transition MUST NOT match

#### Scenario: Variable condition uses committed state
- **WHEN** a transition requires `variable.exists` for an EXECUTION variable that was assigned and committed on a prior SUCCESS step
- **THEN** the variable condition is true

### Requirement: Stream bodies skip classification
A response declared or detected as a stream body MUST NOT enter challenge detection or authentication classification.

#### Scenario: Stream success skips challenge
- **WHEN** a response is a stream body
- **THEN** the system does not evaluate response conditions for challenge or authentication state

### Requirement: Scoped state with commit and discard
Variables SHALL have scopes GLOBAL, SESSION, EXECUTION, FLOW, and LOCAL. GLOBAL MUST be read-only at runtime. SUCCESS MUST commit local mutations. FAILURE, CANCELLED, and TIMEOUT MUST discard them unless an explicit `commitOn` allows CHALLENGE commits.

#### Scenario: Global write is rejected
- **WHEN** a definition assigns into GLOBAL scope
- **THEN** validation fails

#### Scenario: Failure discards local mutation
- **WHEN** a step fails after a local mutation
- **THEN** the mutation is discarded and is not visible to later steps

#### Scenario: Challenge may commit allowed mutation
- **WHEN** a challenge extract declares commit-on CHALLENGE
- **THEN** the extracted nonce is committed for the authentication flow

### Requirement: Host can cancel an in-flight execution
`ApiClient.cancel` SHALL stop further steps for that execution id. The outcome MUST be CANCELLED. The transport MUST abort an in-flight call when it supports abort; whether or not abort succeeds, the execution MUST NOT start new sends. Local mutations MUST be discarded. CANCELLED MUST NOT trigger retry, replay, or authentication.

#### Scenario: Cancel before second send
- **WHEN** the host cancels after the first business request and before a replay
- **THEN** the execution ends with CANCELLED and no additional outbound request is sent

### Requirement: Assign can stamp ISO-offset time and generate a nonce
Flow assign SHALL support `{ now: epochMillis }`, `{ now: isoOffset }`, and `{ generate: nonce }`. `isoOffset` MUST format the injected clock instant in that clock's zone as `yyyy-MM-dd'T'HH:mm:ssXXX`. `generate: nonce` MUST take the next value from an injectable nonce source so successive assigns (including after `RETRY_FLOW`) yield distinct values. Flow MUST NOT compute HMAC, sort query maps, or percent-encode query keys. An unknown `now` or `generate` form MUST fail validation.

#### Scenario: isoOffset uses clock instant and zone
- **WHEN** a step assigns `{ now: isoOffset }` with a clock instant of 1000 ms in UTC
- **THEN** the assigned value is `1970-01-01T00:00:01Z`

#### Scenario: RETRY_FLOW receives a new nonce
- **WHEN** a definition assigns `{ generate: nonce }` on the first send and again after `RETRY_FLOW`
- **THEN** the second nonce differs from the first and both values appear on the corresponding outbound requests

#### Scenario: Unknown now form fails validate
- **WHEN** a definition assigns `{ now: epochSeconds }` or any `now` form other than `epochMillis` or `isoOffset`
- **THEN** validation fails and the definition is not compiled

### Requirement: CONTINUE on HTTP 401 is not CHALLENGE
When a step transition matches HTTP 401 (alone or with body conditions) and selects action `CONTINUE`, the system SHALL advance to the next step and MUST NOT classify that request step as `StepOutcome.CHALLENGE`, MUST NOT start Authentication Flow solely because of that transition, and MUST NOT treat the 401 as an unmatched failure. Challenge field binding SHALL use subsequent `EXTRACT` steps against the retained last response (request steps do not apply extract inline). Business-step `AUTHENTICATE` on 401 remains a separate transition action.

#### Scenario: Auth hop continues after 401 challenge body
- **WHEN** an authentication-flow request returns HTTP 401 with a JSON body containing challenge fields and the first matching transition is `CONTINUE`
- **THEN** the next authentication step runs, the request step outcome is not `CHALLENGE`, and following EXTRACT steps may bind fields from that response

#### Scenario: Unmatched 401 still fails
- **WHEN** a step receives HTTP 401 and no transition matches
- **THEN** the default action is FAIL with FAILURE and Authentication Flow MUST NOT start from that unmatched status alone

#### Scenario: Business AUTHENTICATE on 401 still works
- **WHEN** a business step transition matches status 401 with action `AUTHENTICATE` and then `REPLAY_REQUEST`
- **THEN** Authentication Flow runs once and the 401 is not handled as auth-hop `CONTINUE`
