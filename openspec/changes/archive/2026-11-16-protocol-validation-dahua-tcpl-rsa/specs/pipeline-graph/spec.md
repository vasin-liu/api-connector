# Spec Delta

## ADDED Requirements

### Requirement: Built-in cipher.rsa-pkcs1 encrypts pipeline byte inputs
The built-in pipeline catalog SHALL include `cipher.rsa-pkcs1`. The node MUST encrypt one bytes input with an X.509 public key using RSA/ECB/PKCS1Padding and emit RFC 4648 Base64 with no line wrap. Omitted encoding MUST behave as `base64`. The key bytes MUST be the UTF-8 of an SPKI Base64 string. Encryption MUST run inside Pipeline, not Flow.

#### Scenario: Known plaintext decrypts back to s2
- **WHEN** a pipeline connects concat bytes of `cb2cb0b9f11b040b2a169e768d266db8` to `cipher.rsa-pkcs1.in` and concat bytes of the Dahua TCPL fixture SPKI Base64 to `cipher.rsa-pkcs1.key`, with omitted encoding or `base64`
- **THEN** decoding the output with the Dahua TCPL fixture PKCS#8 private key and RSA/ECB/PKCS1Padding yields the UTF-8 of `cb2cb0b9f11b040b2a169e768d266db8`, and Flow MUST NOT compute RSA

#### Scenario: Unknown vendor cipher is rejected
- **WHEN** a definition references `cipher.dahua-rsa`
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: cipher.rsa-pkcs1.in accepts bytes only
A secret or string edge directly into `cipher.rsa-pkcs1.in` MUST fail compile and send zero HTTP.

#### Scenario: Direct secret into cipher.in fails compile
- **WHEN** a definition connects a `secretRef` edge directly to `cipher.rsa-pkcs1.in`
- **THEN** compile fails with a pipeline type error and zero HTTP

### Requirement: cipher.rsa-pkcs1 encoding is base64
An encoding other than `base64` MUST fail validation, and the definition MUST NOT compile.

#### Scenario: Non-base64 encoding fails compile
- **WHEN** a definition sets `cipher.rsa-pkcs1` encoding to `hex`
- **THEN** validation fails and the definition is not compiled

### Requirement: cipher.rsa-pkcs1 key is SPKI Base64 bytes
The `.key` port MUST receive bytes of an X.509 SubjectPublicKeyInfo Base64 text. A `secretRef` edge into `.key` MUST fail compile and MUST NOT emit ciphertext. An invalid SPKI MUST fail before HTTP, and the failure text MUST NOT contain the key text.

#### Scenario: Secret reference into the key port fails compile
- **WHEN** a definition connects a `secretRef` edge to `cipher.rsa-pkcs1.key`
- **THEN** compile fails with a pipeline type error, no ciphertext is emitted, and zero HTTP is sent

#### Scenario: Invalid SPKI fails before HTTP
- **WHEN** the key bytes are not a Base64 X.509 SPKI
- **THEN** execution fails with zero HTTP and the exception message does not contain the key text

### Requirement: RSA public-key cipher leaves ENCRYPT unchanged
`cipher.rsa-pkcs1` MUST NOT require sink `ENCRYPT`. The existing rule that `cipher.sm4-ecb.key` requires sink `ENCRYPT` MUST stay in force.

#### Scenario: SM4 key still requires ENCRYPT
- **WHEN** a `cipher.sm4-ecb.key` edge declares sink `HMAC`
- **THEN** validation or execute denies the sink and MUST NOT emit SM4 ciphertext
