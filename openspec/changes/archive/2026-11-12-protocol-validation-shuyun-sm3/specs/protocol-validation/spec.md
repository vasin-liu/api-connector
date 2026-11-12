# Spec Delta

## ADDED Requirements

### Requirement: Shuyun list signature matches the published SM3
The system SHALL send the published Shuyun POST so that header `signature` is the lowercase hexadecimal SM3 of the UTF-8 concatenation of app id, app secret, timestamp, and nonce with an empty separator. Flow MUST NOT hash. FakeTransport MUST NOT call the public internet.

#### Scenario: First send matches the published vector
- **WHEN** FakeTransport receives the first Shuyun POST
- **THEN** the path is `/sznszhddpt/rest/event/list`, `x-app-id` is `test-app-id`, `timestamp` is `1000`, `nonce` is `n1`, and `signature` equals `bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb`

#### Scenario: Signature is lowercase hex of the four-part string
- **WHEN** the signature is computed for timestamp `1000` and nonce `n1`
- **THEN** the hashed UTF-8 string is `test-app-idtest-app-secret1000n1` and `signature` MUST NOT equal `BCE0B91E9BA1E45AAE2CCD3341C7F5BCF0889E2B08D12ED7B3CD6DC9744C05CB`

#### Scenario: Operation headers are present and unsigned
- **WHEN** the first Shuyun POST is sent
- **THEN** the headers include `operationUserId` `1`, `operationUserName` `idps`, `operationUserCode` `idps`, and `operationAppCode` `jtdn`, and `signature` remains the four-part digest above

### Requirement: Shuyun SM3 stays inside the pipeline catalog
The SM3 digest MUST be `hasher.sm3` with encoding `hex`, over `canonicalizer.concat` with an empty separator whose parts are the app id, the app secret admitted with sink `HASH`, the timestamp, and the nonce.

#### Scenario: Catalog nodes produce the digest
- **WHEN** the Shuyun definition computes `signature`
- **THEN** the digest is that `hasher.sm3` node and Flow only assigns variables and chooses transitions

#### Scenario: Unknown vendor Shuyun node is rejected
- **WHEN** a definition references `hasher.shuyun-sm3` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Shuyun retry rebuilds timestamp, nonce, and signature
HTTP 503 MUST select `RETRY_FLOW`. The authentication flow MUST assign a new timestamp and a new nonce and MUST NOT send HTTP. The following business request MUST be a new instance whose `timestamp`, `nonce`, and `signature` differ from the first.

#### Scenario: Second send uses the next timestamp and nonce
- **WHEN** FakeTransport returns 503 and then 200
- **THEN** authentication performs zero HTTP calls, and the second business request has `timestamp` `1001`, `nonce` `n2`, and `signature` `c6261c5b5b0eb59b92993bcb8954aec24e5436e1c50b8246d80765e9733aa882`

#### Scenario: Body bytes stay fixed across the rebuild
- **WHEN** the second business request is sent
- **THEN** its body bytes equal the first body and the request instance is not the first request

### Requirement: Shuyun secret stays off the wire
The Shuyun app secret MUST be absent from the outbound headers, body, and query, and from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor pipeline node.

#### Scenario: Secret never appears on the wire
- **WHEN** the Shuyun request is sent
- **THEN** no query, header, or body field contains `test-app-secret`, and DecisionTrace MUST NOT contain that plaintext
