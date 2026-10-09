## ADDED Requirements

### Requirement: Hikvision Artemis GET AK/SK executes as a published definition
The system SHALL compile and execute a Canonical Definition for Hikvision Artemis GET that signs `SignUtil` StringToSign with HMAC-SHA256 and sends the Base64 digest plus freshness fields as `X-Ca-*` headers. StringToSign for this gate is the frozen GET shape: `METHOD` uppercase, then `Accept` value, then `Content-Type` value (no `Content-MD5`, no `Date`), then signed-header lines `X-Ca-Key:{ak}`, `X-Ca-Nonce:{nonce}`, `X-Ca-Timestamp:{epochMillis}` each `Name:value` plus U+000A, then resource `path?` plus query pairs sorted by key with encoding `none`. HMAC MUST use encoding `base64`. Canonicalization and HMAC MUST be pipeline nodes (`canonicalizer.sorted-query`, `canonicalizer.concat`, `signer.hmac-sha256`); Flow only assigns, binds credentials, and chooses transitions. FakeTransport tests MUST NOT call the public internet. Secret **key** material MUST be absent from DecisionTrace, exception messages, and outbound query/headers. Production secrets MUST NOT appear in git. The definition MUST NOT depend on `artemis-http-client` or a vendor `canonicalizer.artemis-*` node.

#### Scenario: Two sends differ in timestamp, nonce, and signature
- **WHEN** FakeTransport returns a status that triggers `RETRY_FLOW` of the Artemis GET
- **THEN** the second outbound request is a new instance (not a clone of the first bytes) and MUST differ in `X-Ca-Timestamp`, `X-Ca-Nonce`, and `X-Ca-Signature`

#### Scenario: Signature matches the published Base64 vector
- **WHEN** FakeTransport first GET uses the published fixtures (AK/SK, nonce, timestamp, path, query)
- **THEN** `X-Ca-Signature` equals the Base64 digest published in the protocol table and `X-Ca-Key` equals the fixture AK

#### Scenario: HMAC signs StringToSign not the query substring
- **WHEN** the Artemis definition computes the digest
- **THEN** HMAC input bytes are the full StringToSign (method, Accept, Content-Type, signed `X-Ca-*` lines, resource), not the canonical query string alone, and the digest is Base64 not hex

#### Scenario: SK never appears on the wire
- **WHEN** the Artemis GET is sent
- **THEN** no query, header, or body field contains the secret key, and DecisionTrace MUST NOT contain SK plaintext

#### Scenario: Unknown vendor Artemis node is rejected
- **WHEN** a definition references `canonicalizer.artemis-string-to-sign` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP
