# Spec Delta

## ADDED Requirements

### Requirement: BODY sink may write a credential into the outbound body
The secret sink catalog SHALL allow `BODY` for placing secret bytes into the outbound body of the credential's own api. Before applying the secret, the system MUST check destination api id equality the same way as `HASH` and `AUTHORIZATION`. A `BODY` sink toward a different api MUST be denied and MUST NOT send the request. DecisionTrace and exception messages MUST NOT contain the plaintext. A `BODY` admission MUST NOT satisfy a `HASH` admission.

#### Scenario: Matching api BODY sink is allowed
- **WHEN** a secret belonging to api A is applied to a `BODY` sink toward api A
- **THEN** the sink is allowed and those bytes may appear in the outbound body

#### Scenario: BODY sink with wrong api id is denied
- **WHEN** a secret belonging to api A would be placed on a `BODY` sink toward api B
- **THEN** the system denies the sink and does not send the request

#### Scenario: BODY plaintext stays out of the trace
- **WHEN** a BODY-sunk secret is sent in the body
- **THEN** DecisionTrace and exception messages MUST NOT contain that plaintext
