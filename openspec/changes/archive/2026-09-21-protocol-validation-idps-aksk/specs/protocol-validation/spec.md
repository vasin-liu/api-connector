## ADDED Requirements

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
