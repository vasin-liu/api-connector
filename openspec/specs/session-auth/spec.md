# session-auth Specification

## Purpose

Defines authentication as an ordinary Flow plus session coordination so concurrent callers share one refresh owner, generation, cookies, and failure cooldown.

## Requirements

### Requirement: Authentication is a flow
Authentication SHALL run as a Flow triggered by SESSION_MISSING, SESSION_EXPIRED, AUTH_CHALLENGE, or EXPLICIT. The system MUST enforce both `authAttemptCount <= maxAuthAttempts` and `depth <= maxDepth`. `transitionLimit` MUST NOT substitute for those limits.

#### Scenario: 401 starts authentication then replays business request
- **WHEN** a business request returns 401 matching the authenticate transition with then REPLAY_REQUEST
- **THEN** the system runs the authentication flow once and rebuilds the original business request with the new session

#### Scenario: Empty authentication flow cannot authenticate
- **WHEN** a definition contains an AUTHENTICATE transition but has no authentication steps
- **THEN** validation fails

#### Scenario: Attempt and depth limits stop re-entry
- **WHEN** authentication re-entry would exceed max attempts or max depth
- **THEN** the execution fails with AUTH_ATTEMPT_EXCEEDED and MUST NOT start another authentication flow

### Requirement: Session key and generation
A recorded SessionKey SHALL include api id, definition revision, auth profile, and credential ref, without tenant or caller identity. The **lookup key** for reuse SHALL be `(apiId, authProfile, credentialRef)` plus the compatibility predicate: a stored session is reusable across revisions only when `authProfile` and `credentialRef` are unchanged. The system MUST NOT use equality of the full SessionKey (including revision) as the reuse test. Successful authentication SHALL increment generation. Waiters MUST re-read the session after refresh and MUST NOT reuse a pre-wait token snapshot.

#### Scenario: Concurrent expiry has one authentication owner
- **WHEN** one hundred executions observe the same expired session
- **THEN** exactly one authentication runs, generation increases by one, and the remaining executions continue with the new generation

#### Scenario: Waiters do not reuse stale credentials
- **WHEN** waiters resume after a successful refresh
- **THEN** each waiter reads the current session generation and materials before rebuilding the business request

#### Scenario: Lookup ignores revision when profile and credential match
- **WHEN** a new definition revision changes only pipeline configuration
- **THEN** session lookup still finds the existing session for the same api id, auth profile, and credential ref

### Requirement: Refresh uses the authentication flow through the coordinator
`REFRESH_SESSION` and `AUTHENTICATE` SHALL share the same authentication flow when the definition has no distinct refresh request. Both MUST acquire the SessionCoordinator so concurrent callers have a single owner. `REFRESH_SESSION` MUST still apply the `then` action after a successful refresh.

#### Scenario: Expired session refresh is single-owner authentication
- **WHEN** many executions choose REFRESH_SESSION because the session is expired
- **THEN** one owner runs the authentication flow and waiters replay with the new generation without each running login

### Requirement: Shared failure and cooldown
When the refresh owner fails, waiters SHALL observe the same failure. The system SHALL enter failure cooldown and MUST NOT start another authentication for that session key during cooldown.

#### Scenario: Authentication failure storm
- **WHEN** one hundred executions require authentication and the owner login fails
- **THEN** authentication HTTP occurs once, all executions fail with the shared reason, and a subsequent execute during cooldown does not call login again

### Requirement: Cross-interface multi-step authentication
Authentication Flow SHALL be able to issue multiple distinct requests (login, challenge, token exchange) before the business request is replayed.

#### Scenario: Multi-hop login
- **WHEN** vendor authentication requires sequential calls A then B then C to obtain a token
- **THEN** the authentication flow performs those calls in order and the business request runs only after the session is VALID

### Requirement: Cookie store is the cookie authority
Cookie state SHALL live under the session cookie context. Cookie persistence MUST honor Domain, Path, Secure, HttpOnly, SameSite, and Expires. Outbound requests MUST attach cookies only from that store.

#### Scenario: Cookie plus token session
- **WHEN** authentication sets cookies and a token
- **THEN** subsequent business requests send those cookies from the session cookie store and MUST NOT invent cookies from prior raw responses outside the store
