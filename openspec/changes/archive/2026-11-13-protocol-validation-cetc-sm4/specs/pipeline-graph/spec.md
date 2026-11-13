# Spec Delta

## ADDED Requirements

### Requirement: Built-in cipher.sm4-ecb encrypts pipeline byte inputs
The built-in pipeline catalog SHALL include `cipher.sm4-ecb`. The node MUST encrypt one bytes input with a secret key using SM4/ECB/PKCS5Padding and emit RFC 4648 Base64 with no line wrap. Omitted encoding MUST behave as `base64`. Encryption MUST run inside Pipeline, not Flow.

#### Scenario: Known plaintext encrypts to the published Base64
- **WHEN** a pipeline connects concat bytes of `{"alarmNo":"a1","alarmTime":"2026-01-01 00:00:00","deviceCode":"dev-1","factory":"test","picPath":"https://cetc.example/p","type":"98","videoPath":"https://cetc.example/v"}` to `cipher.sm4-ecb.in` with key `test-cetc-secret` and omitted encoding or `base64`
- **THEN** the output is `ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g=` and Flow MUST NOT compute SM4

#### Scenario: Unknown vendor cipher is rejected
- **WHEN** a definition references `cipher.cetc-sm4`
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: cipher.sm4-ecb.in accepts bytes only
A secret or string edge directly into `cipher.sm4-ecb.in` MUST fail compile and send zero HTTP.

#### Scenario: Direct secret into cipher.in fails compile
- **WHEN** a definition connects a `secretRef` edge directly to `cipher.sm4-ecb.in`
- **THEN** compile fails with a pipeline type error and zero HTTP

### Requirement: cipher.sm4-ecb encoding is base64
An encoding other than `base64` MUST fail validation, and the definition MUST NOT compile.

#### Scenario: Non-base64 encoding fails compile
- **WHEN** a definition sets `cipher.sm4-ecb` encoding to `hex`
- **THEN** validation fails and the definition is not compiled

### Requirement: cipher.sm4-ecb key is 16 UTF-8 bytes
A key whose UTF-8 length is not 16 MUST fail before HTTP. The failure text MUST NOT contain the key.

#### Scenario: Non-16-byte key fails before HTTP
- **WHEN** the key UTF-8 length is not 16
- **THEN** execution fails with zero HTTP and the exception message does not contain the key

### Requirement: Cipher key uses ENCRYPT sink
A `secretRef` on the edge into `cipher.sm4-ecb.key` SHALL declare sink `ENCRYPT`. `HMAC`, `HASH`, `AUTHORIZATION`, `BODY`, or a missing sink MUST be rejected and MUST NOT emit ciphertext. Sink `ENCRYPT` on a concat that supplies the HTTP body MUST still be rejected.

#### Scenario: ENCRYPT sink on the key edge is allowed
- **WHEN** the app secret is the `.key` edge with sink `ENCRYPT` for its own api id
- **THEN** compile succeeds and execute produces ciphertext without placing that secret's plaintext in DecisionTrace

#### Scenario: HMAC sink on the key edge is denied
- **WHEN** the `.key` edge declares sink `HMAC`
- **THEN** validation or execute denies the sink and MUST NOT emit ciphertext from that admission

#### Scenario: ENCRYPT does not admit a wire-body secret
- **WHEN** a concat that supplies the HTTP body contains a `secretRef` with sink `ENCRYPT`
- **THEN** validation fails and no HTTP is sent
