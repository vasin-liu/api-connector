# Spec Delta

## ADDED Requirements

### Requirement: Qyh GPS login sign matches the published SM3
The system SHALL POST getToken so that `sign` is the lowercase SM3 of `test-user`, the login epoch millis, and the agreed key concatenated with an empty separator. Flow MUST NOT hash. FakeTransport MUST NOT call the public internet.

#### Scenario: Login body carries the frozen sign
- **WHEN** the login epoch is `1700000001000`
- **THEN** the path is `/zhjt/sportservice/basic-api/gzqyh/open/api/getToken`, the query contains `app_id=test-token-app` and `api_key=test-token-key`, `Content-Type` is `application/json;charset=UTF-8`, and the body equals `{"sign":"1a625df0ea71ff8731a498b38638987d0ad71e0bcfe1cb79e987de599a29c86f","t":1700000001000,"sysId":"test-sys"}`

### Requirement: Qyh GPS login hash stays inside the pipeline catalog
The login SM3 MUST be `hasher.sm3` with encoding `hex`, over `canonicalizer.concat` of the username, the login epoch, and the agreed key admitted with sink `HASH`. Flow MUST only assign, extract, and choose transitions.

#### Scenario: Catalog nodes produce the login sign
- **WHEN** the definition builds the getToken sign
- **THEN** the digest comes from `hasher.sm3` and Flow does not compute SM3

#### Scenario: Unknown vendor cipher is rejected
- **WHEN** a definition references `cipher.qyh-sm4`
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Qyh GPS query sign matches the published SM3
The system SHALL POST queryGps whose body is `{"plateNo":"A12345"}`. `X-Sign` MUST be the lowercase SM3 of the percent-encoded canonical string for that request's `X-Time`. `X-Access-Token`, `X-Username`, `X-System`, and `X-Time` MUST be `test-token`, `test-user`, `test-sys`, and that epoch. Flow MUST NOT encrypt, percent-encode, or hash.

#### Scenario: Replay after login matches the published sign
- **WHEN** the business epoch is `1700000000000` and getToken has stored token `test-token`
- **THEN** the path is `/zhjt/sportservice/basic-api/gzqyh/open/api/queryGps`, the query contains `app_id=test-gps-app` and `api_key=test-gps-key`, the body equals `{"plateNo":"A12345"}`, `X-Time` is `1700000000000`, and `X-Sign` is `8da4c1ef6d160c49f7e5494c8caa76b02c677f0d795e77a914a74b81029a3647`

#### Scenario: Probe before login has no token
- **WHEN** the first queryGps is sent before a token exists
- **THEN** that request does not include `X-Access-Token` and FakeTransport's 401 selects authentication

### Requirement: Qyh GPS sign stays inside the pipeline catalog
`X-Sign` MUST be `hasher.sm3` over `canonicalizer.percent-encode` of a concat of `{"plateNo":"A12345"}&_t=`, the business epoch, `&symKey=`, and the decrypt output. The decrypt MUST be `cipher.sm4-ecb-decrypt` of the stored `result.str` with the agreed key admitted with sink `ENCRYPT`. The decrypt plaintext MUST be admitted to that concat with sink `HASH`.

#### Scenario: Catalog nodes produce X-Sign
- **WHEN** the definition builds the queryGps signature
- **THEN** the plaintext comes from `cipher.sm4-ecb-decrypt`, the encoded preimage comes from `canonicalizer.percent-encode`, and the digest comes from `hasher.sm3`

### Requirement: Qyh GPS session stores ciphertext
A token response with `success` true MUST store `$.result.token` and `$.result.str` in the session. The session MUST NOT store the decrypted symKey. The runtime MUST NOT read `timeOut`.

#### Scenario: Token response keeps the ciphertext
- **WHEN** getToken returns 200 with `success` true, `result.token` `test-token`, and `result.str` `8cd2182b020f1d459a719234394e2f06`
- **THEN** the session contains that token and that ciphertext and does not contain `symkey-fixture`

### Requirement: Qyh GPS session replays without another login
A later execute with a valid session MUST NOT call getToken. It MUST send one queryGps with token `test-token`, a new `X-Time`, and the `X-Sign` for that new epoch.

#### Scenario: Second execute skips getToken
- **WHEN** a second execute uses business epoch `1700000002000`
- **THEN** getToken is not called, queryGps is sent once, `X-Time` is `1700000002000`, and `X-Sign` is `9b4464d0f66c0b9de9d640bcfe9a5fc8126772f4533985198b85b88ea6f1675c`

### Requirement: Qyh GPS missing ciphertext fails the session
HTTP 200 whose `success` is not true, or whose `result.str` is missing, MUST fail the session and MUST NOT send a signed queryGps. A follow-up during cooldown MUST send no getToken and no queryGps.

#### Scenario: Success false does not query GPS
- **WHEN** getToken returns 200 with `success` false
- **THEN** the outcome is failure, queryGps is not signed, and a follow-up during cooldown sends no HTTP

#### Scenario: Missing str does not query GPS
- **WHEN** getToken returns 200 with `success` true and no `result.str`
- **THEN** the session stores no token and a follow-up during cooldown does not call getToken

### Requirement: Qyh GPS secrets stay off the wire
The agreed key and the decrypted symKey MUST be absent from outbound headers, body, and query, and from DecisionTrace and exception messages. `test-user` MUST appear as `X-Username` and MUST be absent from DecisionTrace and exception messages. `test-token` MUST appear as `X-Access-Token` and MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor pipeline node.

#### Scenario: Agreed key and symKey never appear on the wire
- **WHEN** getToken and queryGps are sent
- **THEN** no query, header, or body contains `0123456789abcdeffedcba9876543210` or `symkey-fixture`, and DecisionTrace and exception messages do not contain them

#### Scenario: Username and token stay out of the trace
- **WHEN** the signed queryGps is sent
- **THEN** `X-Username` is `test-user`, `X-Access-Token` is `test-token`, and DecisionTrace and exception messages contain neither value
