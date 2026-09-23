## ADDED Requirements

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
