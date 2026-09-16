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
