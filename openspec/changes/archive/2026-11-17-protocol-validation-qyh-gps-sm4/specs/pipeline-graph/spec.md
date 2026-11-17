# Spec Delta

## ADDED Requirements

### Requirement: Built-in cipher.sm4-ecb-decrypt decrypts pipeline byte inputs
The built-in pipeline catalog SHALL include `cipher.sm4-ecb-decrypt`. The node MUST decrypt one bytes input with SM4/ECB/PKCS5Padding and emit the plaintext as a string. The input bytes MUST be the UTF-8 of lowercase hex ciphertext. The key bytes MUST be the UTF-8 of a 32-character hex string, decoded to 16 bytes. Decryption MUST run inside Pipeline, not Flow. The node MUST NOT accept an encoding config.

#### Scenario: Known ciphertext decrypts to the fixture symKey
- **WHEN** a pipeline connects concat bytes of `8cd2182b020f1d459a719234394e2f06` to `cipher.sm4-ecb-decrypt.in` and the key UTF-8 is `0123456789abcdeffedcba9876543210`
- **THEN** the output is `symkey-fixture` and Flow MUST NOT compute SM4

#### Scenario: Encoding config fails validation
- **WHEN** a definition sets `cipher.sm4-ecb-decrypt` config `encoding`
- **THEN** validation fails and the definition is not compiled

#### Scenario: Unknown vendor cipher is rejected
- **WHEN** a definition references `cipher.qyh-sm4`
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: cipher.sm4-ecb-decrypt.in accepts bytes only
A secret or string edge directly into `cipher.sm4-ecb-decrypt.in` MUST fail compile and send zero HTTP.

#### Scenario: Direct secret into cipher.in fails compile
- **WHEN** a definition connects a `secretRef` edge directly to `cipher.sm4-ecb-decrypt.in`
- **THEN** compile fails with a pipeline type error and zero HTTP

### Requirement: cipher.sm4-ecb-decrypt key is 32 hex characters
A key whose UTF-8 is not 32 hexadecimal characters MUST fail before HTTP. The failure text MUST NOT contain the key. A valid key MUST be hex-decoded to 16 bytes before SM4.

#### Scenario: Non-hex key fails before HTTP
- **WHEN** the key UTF-8 is not 32 hexadecimal characters
- **THEN** execution fails with zero HTTP and the exception message does not contain the key

### Requirement: cipher.sm4-ecb-decrypt key uses ENCRYPT sink
A `secretRef` on the edge into `cipher.sm4-ecb-decrypt.key` SHALL declare sink `ENCRYPT`. `HMAC`, `HASH`, `AUTHORIZATION`, `BODY`, or a missing sink MUST be rejected and MUST NOT emit plaintext.

#### Scenario: ENCRYPT sink on the decrypt key is allowed
- **WHEN** the agreed key is the `.key` edge with sink `ENCRYPT` for its own api id
- **THEN** compile succeeds and execute produces plaintext without placing that key in DecisionTrace

#### Scenario: HASH sink on the decrypt key is denied
- **WHEN** the `.key` edge declares sink `HASH`
- **THEN** validation or execute denies the sink and MUST NOT emit plaintext from that admission

### Requirement: cipher.sm4-ecb-decrypt leaves cipher.sm4-ecb unchanged
`cipher.sm4-ecb` MUST still encrypt, require a 16-byte UTF-8 key, emit Base64, and require sink `ENCRYPT` on `.key`. A 32-character hex key MUST still fail that encrypt node before HTTP.

#### Scenario: Encrypt node still rejects a 32-character key
- **WHEN** `cipher.sm4-ecb` is given key UTF-8 `0123456789abcdeffedcba9876543210`
- **THEN** execution fails with zero HTTP and emits no Base64 ciphertext

#### Scenario: Encrypt node still rejects hex encoding
- **WHEN** a definition sets `cipher.sm4-ecb` encoding to `hex`
- **THEN** validation fails and the definition is not compiled

### Requirement: Built-in canonicalizer.percent-encode encodes a whole string
The built-in pipeline catalog SHALL include `canonicalizer.percent-encode`. The node MUST accept one bytes input and emit the percent-encoded bytes of that whole string. Unreserved octets (ALPHA, DIGIT, `-`, `.`, `_`, `~`) MUST stay as themselves. Every other octet MUST become uppercase `%HH`. Space MUST become `%20`. Encoding MUST run inside Pipeline, not Flow.

#### Scenario: The published canonical string is encoded
- **WHEN** the input bytes are the UTF-8 of `{"plateNo":"A12345"}&_t=1700000000000&symKey=symkey-fixture`
- **THEN** the output bytes are the UTF-8 of `%7B%22plateNo%22%3A%22A12345%22%7D%26_t%3D1700000000000%26symKey%3Dsymkey-fixture`

#### Scenario: Space and tilde follow RFC 3986 unreserved
- **WHEN** the input bytes are the UTF-8 of `a b~`
- **THEN** the output bytes are the UTF-8 of `a%20b~`

#### Scenario: Separators inside the string are encoded
- **WHEN** the input bytes are the UTF-8 of `a&b=c`
- **THEN** the output bytes are the UTF-8 of `a%26b%3Dc`

### Requirement: Secret variable parts declare HASH
A concat part whose variable holds secret material MUST declare sink `HASH`. A missing sink, or any sink other than `HASH`, MUST fail validation and MUST NOT copy that secret into the concat output.

#### Scenario: HASH on a secret variable is allowed
- **WHEN** a concat part names a secret variable and declares sink `HASH` for its own api id
- **THEN** compile succeeds and those bytes may enter the concat without placing the plaintext in DecisionTrace

#### Scenario: Missing sink on a secret variable is denied
- **WHEN** a concat part names a secret variable and omits sink
- **THEN** validation fails and no digest is emitted from that concat
