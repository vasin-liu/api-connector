## ADDED Requirements

### Requirement: Hikvision Artemis POST Content-MD5 executes as a published definition
The system SHALL compile and execute a Canonical Definition, distinct from the Artemis GET definition, for Hikvision Artemis POST that signs `SignUtil` StringToSign with HMAC-SHA256 and sends the Base64 digest plus freshness fields as `X-Ca-*` headers. The HTTP body MUST be the UTF-8 bytes of one published JSON literal. `Content-MD5` MUST be RFC 4648 Base64 of the raw MD5 of those same body bytes. StringToSign for this gate is the frozen POST shape: `METHOD` uppercase, then `Accept` value, then the `Content-MD5` value, then `Content-Type` value (no `Date`), then signed-header lines `X-Ca-Key:{ak}`, `X-Ca-Nonce:{nonce}`, `X-Ca-Timestamp:{epochMillis}` each `Name:value` plus U+000A, then resource `path?` plus query pairs sorted by key with encoding `none`. The body MUST NOT be merged into that resource. HMAC MUST use encoding `base64`. Canonicalization, MD5, and HMAC MUST be pipeline nodes (`canonicalizer.sorted-query`, `canonicalizer.concat`, `hasher.md5`, `signer.hmac-sha256`); Flow only assigns, binds credentials, and chooses transitions. The signed body MUST NOT be the JSON encoding of the execution-variable object. FakeTransport tests MUST NOT call the public internet. Secret **key** material MUST be absent from DecisionTrace, exception messages, and outbound query, headers, or body. Production secrets MUST NOT appear in git. The definition MUST NOT depend on `artemis-http-client` or a vendor `canonicalizer.artemis-*` node. The existing Artemis GET definition MUST keep compiling and MUST keep its published GET signature.

#### Scenario: Two sends differ in timestamp, nonce, and signature only
- **WHEN** FakeTransport returns a status that triggers `RETRY_FLOW` of the Artemis POST
- **THEN** the second outbound request is a new instance (not a clone of the first bytes) and MUST differ in `X-Ca-Timestamp`, `X-Ca-Nonce`, and `X-Ca-Signature`, while the body bytes and `Content-MD5` stay equal to the first send

#### Scenario: Signature and Content-MD5 match the published vectors
- **WHEN** FakeTransport first POST uses the published fixtures (AK/SK, nonce, timestamp, path, query, body)
- **THEN** `X-Ca-Signature` equals the Base64 digest published in the protocol table, `Content-MD5` equals the published Base64 MD5, and `X-Ca-Key` equals the fixture AK

#### Scenario: Content-MD5 is the MD5 of the wire body
- **WHEN** the Artemis POST is sent
- **THEN** the outbound body equals the published JSON literal, `Content-MD5` equals RFC 4648 Base64 of the raw MD5 of those body bytes, and the lowercase hex MD5 MUST NOT be sent as `Content-MD5`

#### Scenario: HMAC signs StringToSign including Content-MD5
- **WHEN** the Artemis POST definition computes the digest
- **THEN** HMAC input bytes are the full StringToSign (method, Accept, Content-MD5, Content-Type, signed `X-Ca-*` lines, resource) and the digest is Base64 not hex; the digest MUST NOT equal HMAC of the query string alone or of the body alone

#### Scenario: MD5 never runs in Flow
- **WHEN** the Artemis POST definition computes `Content-MD5`
- **THEN** the hash is a `hasher.md5` node with encoding `base64` over concat bytes of the body literal; Flow MUST NOT compute MD5

#### Scenario: SK never appears on the wire
- **WHEN** the Artemis POST is sent
- **THEN** no query, header, or body field contains the secret key, and DecisionTrace MUST NOT contain SK plaintext

#### Scenario: Unknown vendor Artemis node is rejected
- **WHEN** a definition references `canonicalizer.artemis-string-to-sign` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP
