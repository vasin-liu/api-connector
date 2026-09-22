## ADDED Requirements

### Requirement: Huawei IVS cookie session executes as a published definition
The system SHALL compile and execute a Canonical Definition for Huawei IVS1800 login that posts JSON `userName` and `password` to the login path, treats JSONPath `$.resultCode` equal to `"0"` as authentication success, accepts `Set-Cookie` into the session cookie store, and attaches those cookies on the rebuilt business request. The business request MUST NOT send an Authorization header or a session token query/header. FakeTransport tests MUST NOT call the public internet. Cookie values and password material MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git.

#### Scenario: First business 401 then login then cookie replay
- **WHEN** a Huawei business request is sent without a session cookie and FakeTransport returns 401
- **THEN** the authentication flow calls the login endpoint once, generation increments, and the replayed business request includes `Cookie` with `JSESSIONID` from the cookie store and MUST NOT include `Authorization`

#### Scenario: Valid cookie session skips login
- **WHEN** a second execution reuses a still-valid Huawei session
- **THEN** FakeTransport records zero login-endpoint calls and one business call that still carries `JSESSIONID` from the store

#### Scenario: Failed login does not storm
- **WHEN** login always returns HTTP 200 with `resultCode` other than `"0"`
- **THEN** shared failure plus cooldown prevent a second login call from a follow-up execute during cooldown, and no `JSESSIONID` is stored

#### Scenario: Cookie is not copied from the raw login body
- **WHEN** login succeeds with `Set-Cookie: JSESSIONID=…` and a JSON body that also contains some session-looking field
- **THEN** the outbound business `Cookie` header is built only from the cookie store and MUST NOT take a cookie value from the JSON body
