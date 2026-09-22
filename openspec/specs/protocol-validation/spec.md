# protocol-validation Specification

## Purpose

Phase 0 follow-on gate for real vendor protocols: Canonical Definitions plus FakeTransport tests for Wenxin OAuth token-in-query and Gaode traffic sorted-query HMAC. Gaps must become OpenSpec updates, not implicit Runtime, Groovy, or Catalog code.

## Requirements

### Requirement: Wenxin OAuth token-in-query executes as a published definition
The system SHALL compile and execute a Canonical Definition for Baidu Wenxin client-credentials token exchange that stores the token in SESSION and attaches it as the `access_token` query parameter on the rebuilt business request. FakeTransport tests MUST NOT call the public internet. Secrets MUST be absent from DecisionTrace and exception messages.

#### Scenario: First business 401 then token then replay
- **WHEN** a Wenxin business request is sent without a session token and the FakeTransport returns 401
- **THEN** the authentication flow calls the token endpoint once, generation increments, and the replayed business request includes `access_token` in the query

#### Scenario: Valid session skips token endpoint
- **WHEN** a second execution reuses a still-valid Wenxin session
- **THEN** FakeTransport records zero token-endpoint calls and one business call

#### Scenario: Failed token exchange does not storm
- **WHEN** the token endpoint always fails
- **THEN** shared failure plus cooldown prevent a second token call from a follow-up execute during cooldown

### Requirement: Gaode traffic sorted-query HMAC rebuilds on replay
The system SHALL compile and execute a Canonical Definition for Gaode traffic HMAC over a canonical string of **sorted query parameters**. Replay and challenge (or timestamp refresh) MUST re-render the template and recompute HMAC; the second outbound query MUST NOT clone the first request bytes.

#### Scenario: Two sends differ in timestamp and digest
- **WHEN** FakeTransport returns a challenge or retry that triggers rebuild of the Gaode business request
- **THEN** the second outbound query string has a different timestamp (or equivalent freshness field) and a different HMAC digest than the first

#### Scenario: HMAC never runs in Flow
- **WHEN** the Gaode definition computes the digest
- **THEN** sorting, concat, and HMAC are pipeline nodes; Flow only assigns variables and chooses transitions

### Requirement: IDPS header AK/SK executes as a published definition
The system SHALL compile and execute a Canonical Definition for IDPS `aksk_hmac_sha256` that signs a seven-line envelope (algorithm, access key, ISO-offset timestamp, HTTP method, URI path, RFC3986 canonical query, client nonce) with HMAC-SHA256 and sends the digest plus freshness fields as `X-Auth-*` headers. FakeTransport tests MUST NOT call the public internet. Secret key material MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The outbound query string MAY remain unencoded even when the canonical query is percent-encoded.

#### Scenario: Two sends differ in timestamp, nonce, and digest
- **WHEN** FakeTransport returns a status that triggers `RETRY_FLOW` of the IDPS business request
- **THEN** the second outbound request is a new instance (not a clone of the first bytes) and MUST differ in `X-Auth-Timestamp`, `X-Auth-SnowflakeID`, and `X-Auth-Signature`

#### Scenario: Canonical query is RFC3986 then sorted
- **WHEN** the IDPS definition signs a query map whose values include a character that RFC3986 percent-encodes
- **THEN** the HMAC envelope's query line contains the encoded form (for example space as `%20`) while the outbound URL query MAY still carry the unencoded value

#### Scenario: HMAC signs the envelope not the query substring
- **WHEN** the IDPS definition computes the digest
- **THEN** HMAC input bytes are the full seven-line envelope joined by U+000A, not the canonical query string alone, and Flow MUST NOT sort, encode, or HMAC

#### Scenario: Unknown vendor node is rejected
- **WHEN** a definition references a pipeline node type that is not in the built-in catalog (including any `canonicalizer.aksk-*` vendor type that is not catalogued)
- **THEN** compile fails with an unknown-node validation code and zero HTTP

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

### Requirement: Protocol gaps become spec changes
If a vendor rule cannot be expressed with Canonical YAML plus the built-in pipeline catalog, implementation MUST stop and update OpenSpec rather than adding implicit orchestrator behavior, Groovy, or Java Catalog endpoints.

#### Scenario: Missing node type fails compile
- **WHEN** a definition references a pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP
