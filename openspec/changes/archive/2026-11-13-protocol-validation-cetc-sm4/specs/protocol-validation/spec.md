# Spec Delta

## ADDED Requirements

### Requirement: CETC collect body matches the published SM4
The system SHALL POST `/detect/third/traffic/event/collect` whose body is the compact object `{"data":"<base64>"}`. That base64 MUST be SM4/ECB/PKCS5Padding of the frozen UTF-8 plaintext. Flow MUST NOT encrypt. FakeTransport MUST NOT call the public internet.

#### Scenario: First send matches the published vector
- **WHEN** FakeTransport receives the CETC POST
- **THEN** the path is `/detect/third/traffic/event/collect`, `Content-Type` is `application/json;charset=UTF-8`, and the body equals `{"data":"ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g="}`

#### Scenario: Ciphertext is not the plaintext
- **WHEN** the body is built from the frozen plaintext
- **THEN** the UTF-8 plaintext is `{"alarmNo":"a1","alarmTime":"2026-01-01 00:00:00","deviceCode":"dev-1","factory":"test","picPath":"https://cetc.example/p","type":"98","videoPath":"https://cetc.example/v"}` and the body MUST NOT equal that plaintext

### Requirement: CETC SM4 stays inside the pipeline catalog
The ciphertext MUST be `cipher.sm4-ecb` with encoding `base64`. Its bytes input MUST be a concat of the frozen plaintext. Its key edge MUST admit the app secret with sink `ENCRYPT`. The HTTP body MUST be a concat of `{"data":"`, that base64, and `"}`, and that concat MUST be the body pipeline's bytes output.

#### Scenario: Catalog nodes produce the body
- **WHEN** the CETC definition builds the request body
- **THEN** the ciphertext comes from `cipher.sm4-ecb` and Flow only assigns variables and chooses transitions

#### Scenario: Unknown vendor CETC node is rejected
- **WHEN** a definition references `cipher.cetc-sm4` or another pipeline node type that is not in the built-in catalog
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: CETC secret stays off the wire
The CETC app secret MUST be absent from the outbound headers, body, and query, and from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor pipeline node.

#### Scenario: Secret never appears on the wire
- **WHEN** the CETC request is sent
- **THEN** no query, header, or body field contains `test-cetc-secret`, and DecisionTrace MUST NOT contain that plaintext

### Requirement: CETC success leaves the response plaintext
HTTP 200 MUST select SUCCESS. The response body MUST remain the bytes FakeTransport returned. The runtime MUST NOT decrypt that body.

#### Scenario: Plaintext 200 succeeds
- **WHEN** FakeTransport returns 200 and `{"code":200,"msg":"ok"}`
- **THEN** the outcome is SUCCESS and the response bytes still equal `{"code":200,"msg":"ok"}`
