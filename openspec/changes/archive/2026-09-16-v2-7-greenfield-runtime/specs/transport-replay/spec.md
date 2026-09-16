## Purpose

Defines outbound HTTP transport, response body kinds, and the separation of retry, session refresh, and request replay, including unknown-outcome safety.

## ADDED Requirements

### Requirement: Replay rebuilds from the original template
Automatic replay SHALL reconstruct the outbound request from the original request template plus current variables, session, dynamic values, pipeline, and signer. The system MUST NOT clone previously sent request bytes.

#### Scenario: Challenge replay refreshes timestamp and signature
- **WHEN** a POST returns 403 with X-Challenge and authentication produces a new HMAC
- **THEN** the replayed POST uses a newly generated timestamp and a newly generated signature

#### Scenario: Replay does not clone the first packet
- **WHEN** authentication updates flow signature state
- **THEN** the second request headers differ from the first (timestamp and authorization) even if the JSON body is unchanged

### Requirement: Retry, refresh, and replay are distinct
On failure the policy SHALL choose exactly one of NO_ACTION, REFRESH_SESSION, REPLAY_REQUEST, RETRY_REQUEST, RETRY_FLOW, or FAIL. RETRY_REQUEST SHALL resend the current business request without invalidating flow bindings. RETRY_FLOW SHALL restart from the designated flow step when an intermediate binding such as a one-time nonce is stale.

#### Scenario: 503 retries the request
- **WHEN** the business request returns 503 and a RETRY_REQUEST transition matches
- **THEN** the system resends that request without running authentication and without incrementing session generation

#### Scenario: Stale nonce uses retry flow
- **WHEN** a one-time nonce has been consumed and a later attempt would replay the last signed request
- **THEN** the system uses RETRY_FLOW and MUST NOT send HMAC computed from the consumed nonce

### Requirement: Unknown outcome is not auto-replayed
If the request was written and the client cannot determine whether the server executed it, the outcome SHALL be UNKNOWN_OUTCOME. TIMEOUT is reserved for a deadline that fires before the request is written. The system MUST NOT automatically replay UNKNOWN_OUTCOME unless the replay policy explicitly allows it. Default for UNKNOWN replayability is to fail.

#### Scenario: POST connection drop does not replay
- **WHEN** a POST is written and the connection is lost before a response
- **THEN** the outcome is UNKNOWN_OUTCOME, outbound HTTP occurs once, and the execution fails without a second send

#### Scenario: Deadline before write is timeout not unknown
- **WHEN** the execution deadline fires before the request is written
- **THEN** the outcome is TIMEOUT and the transport does not send

### Requirement: Policy may only downgrade automatic resend
After a transition selects an action, ReplayPolicy and UNKNOWN_OUTCOME rules MUST replace a disallowed resend with FAIL. The system MUST NOT upgrade FAIL, NO_ACTION, or a disallowed replay into RETRY_REQUEST, REPLAY_REQUEST, or RETRY_FLOW.

#### Scenario: Unsafe login drop stays failed
- **WHEN** a matched transition would replay but the request is UNSAFE and the connection dropped
- **THEN** the final action is FAIL and login is not sent again

### Requirement: Replay policy is not method-only
Replayability SHALL consider idempotency keys, vendor semantics, and side effects, not only HTTP method. UNSAFE requests MUST NOT be automatically replayed.

#### Scenario: Login is unsafe
- **WHEN** a login request is marked UNSAFE and the connection drops
- **THEN** the system MUST NOT automatically replay login

### Requirement: Stream body is reserved but excluded from auth classification
The transport model SHALL allow bytes, stream, and empty bodies. Stream bodies MUST NOT enter challenge or authentication classifiers.

#### Scenario: Bytes challenge still classified
- **WHEN** a small JSON 403 challenge is returned as bytes
- **THEN** challenge detection runs as usual
