## ADDED Requirements

### Requirement: Multi-hop auth may use 401 as an intermediate success
Authentication Flow SHALL support a request hop that returns HTTP 401 with challenge parameters, selects `CONTINUE`, then binds challenge fields in **subsequent EXTRACT steps**, then issues a follow-up request that mints a session token. Session becomes VALID only after the token-minting hop succeeds and its extract/onCommit runs. Concurrent callers MUST still share a single authentication owner and cooldown on failure.

#### Scenario: 401 challenge hop then extracts then token hop
- **WHEN** authentication request A returns 401 with `randomKey`, continues, EXTRACT steps bind challenge fields, then request B returns 200 with a token stored in `session.token`
- **THEN** generation increments, session status is VALID, and the replayed business request carries that token material

#### Scenario: Missing challenge fields fail without token hop
- **WHEN** authentication request A returns 401 but required challenge fields are absent so CONTINUE does not match or extracts cannot bind them
- **THEN** authentication fails, shared failure plus cooldown apply, and the token hop MUST NOT run
