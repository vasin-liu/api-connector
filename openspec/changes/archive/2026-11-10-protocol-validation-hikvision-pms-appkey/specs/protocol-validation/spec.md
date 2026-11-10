# Spec Delta

## MODIFIED Requirements

### Requirement: Hikvision PMS download token executes as a published definition
The system SHALL compile and execute a Canonical Definition, distinct from the Artemis definitions, for Hikvision PMS 下行 token: the `token` query parameter is the **uppercase** hexadecimal MD5 over the concatenation of the request URI path (no host, no query), the exact wire body bytes, and the secret key. The hashed body bytes MUST be the bytes actually sent. The MD5 MUST be a `hasher.md5` node with encoding `hex-upper` fed by `canonicalizer.concat`; Flow MUST NOT compute MD5 or uppercase strings. The body MUST be the UTF-8 bytes of compact JSON `{"appkey":"<appkey>"}`, where `<appkey>` is credential material for this api and MUST NOT be stored as a definition literal. FakeTransport tests MUST NOT call the public internet. Secret key material MUST be absent from DecisionTrace, exception messages, and outbound query, headers, or body. The appkey credential MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor `canonicalizer.*` node. The existing Artemis GET and POST definitions MUST keep compiling and MUST keep their published signatures.

#### Scenario: Token and body match the published vectors
- **WHEN** FakeTransport first POST uses the published fixtures (appkey credential, secret key, path)
- **THEN** the outbound query `token` equals `EDAA4B3136EA75DF9A435C95B920DD9E` and the outbound body equals `{"appkey":"test-ak"}`

#### Scenario: Token is the uppercase MD5 of the wire material
- **WHEN** the PMS request is sent
- **THEN** `token` equals uppercase hex MD5 of URI-path + wire body bytes + secret, and MUST NOT be the lowercase hex digest or any Base64 form

#### Scenario: Deterministic replay keeps token and body
- **WHEN** FakeTransport returns 503 and the replay policy re-sends the PMS request
- **THEN** the second outbound request is a new instance whose body bytes and `token` equal the first send, with no freshness rebuild (the token is a deterministic function of URI path and body)

#### Scenario: MD5 never runs in Flow
- **WHEN** the PMS definition computes the token
- **THEN** the digest is a `hasher.md5` `encoding: hex-upper` node over concat bytes of path + the exact wire body bytes + secret key, the secret key is admitted via sink `HASH`, the appkey is admitted via sink `BODY` on the body producer, and Flow only assigns variables and chooses transitions

#### Scenario: Appkey is a credential, not a literal
- **WHEN** the PMS body is built
- **THEN** the `appkey` field equals the resolved credential `test-ak` and the definition does not contain that material as a plain JSON literal

#### Scenario: SK never appears on the wire
- **WHEN** the PMS request is sent
- **THEN** no query, header, or body field contains the secret key, and DecisionTrace MUST NOT contain SK plaintext

#### Scenario: Unknown vendor PMS node is rejected
- **WHEN** a definition references `canonicalizer.pms-token` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP
