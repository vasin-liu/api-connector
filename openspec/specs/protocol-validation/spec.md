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

### Requirement: DaHua nested-MD5 authorize executes as a published definition
The system SHALL compile and execute a Canonical Definition for 大华交警视频云 authorize that: (1) posts `{userName, clientType}` with frozen `clientType=web` and login headers `Content-Type: application/json;charset=UTF-8` plus `X-Api-Version: V1.0` to `/videoService/accounts/authorize`, using two `type: secret` credentials for username and password, treats HTTP **401** when `$.randomKey` and `$.realm` exist and `$.encryptType` equals `MD5` as challenge hop (`CONTINUE`), then binds `realm` / `randomKey` / `encryptType` in **following EXTRACT steps**; (2) computes signature with nested MD5 for the **no-`method`-key** branch from `TrafficDaHuaUtils.calculateSign` inside Pipeline (`canonicalizer.concat` with `HASH` for both secrets + `hasher.md5` on bytes); (3) posts the signed body including `signature`, `randomKey`, `encryptType`, and `expiredTime=86400` to the same authorize path; (4) stores `$.token` in `session.token` and attaches it as header `X-Subject-Token` with sink `HEADER` on the rebuilt business request. FakeTransport tests MUST NOT call the public internet. Password, username, and token material MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. RSA and `method=simple` / blank-method branches are out of scope. Challenge fixture `realm` MUST be non-blank.

#### Scenario: First business 401 then two-hop auth then token replay
- **WHEN** a DaHua business request is sent without a session token and FakeTransport returns 401
- **THEN** authentication performs exactly one challenge authorize (HTTP 401 body) and one token authorize (HTTP 200 with token), generation increments, and the replayed business request includes `X-Subject-Token` equal to the stored token

#### Scenario: Valid session skips authorize
- **WHEN** a second execution reuses a still-valid DaHua session
- **THEN** FakeTransport records zero authorize-endpoint calls and one business call that still carries `X-Subject-Token`

#### Scenario: Failed challenge hop does not storm
- **WHEN** the challenge authorize always returns 401 without `randomKey`
- **THEN** shared failure plus cooldown prevent a second authorize storm from a follow-up execute during cooldown, and no token is stored

#### Scenario: Signature matches nested MD5 fixture
- **WHEN** FakeTransport challenge returns fixed non-blank `realm` / `randomKey` / `encryptType=MD5` without a `method` key and credentials are the published fixtures
- **THEN** the token authorize request body `signature` equals the lowercase nested-MD5 digest published in the protocol table

#### Scenario: MD5 never runs in Flow
- **WHEN** the DaHua definition computes the signature
- **THEN** nested MD5 steps are pipeline concat + `hasher.md5` nodes; Flow only assigns, extracts, and chooses transitions

### Requirement: Protocol gaps become spec changes
If a vendor rule cannot be expressed with Canonical YAML plus the built-in pipeline catalog, implementation MUST stop and update OpenSpec rather than adding implicit orchestrator behavior, Groovy, or Java Catalog endpoints.

#### Scenario: Missing node type fails compile
- **WHEN** a definition references a pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP
