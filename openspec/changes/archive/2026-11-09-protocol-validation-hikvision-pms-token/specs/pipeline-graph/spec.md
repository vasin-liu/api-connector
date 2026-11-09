# Spec Delta

## MODIFIED Requirements

### Requirement: HMAC-SHA256 encoding is configurable
The built-in `signer.hmac-sha256` node SHALL accept `config.encoding` `hex`, `hex-upper`, or `base64`. Omitted encoding MUST behave as `hex`: lowercase hexadecimal digest (existing Gaode / IDPS YAML binds this output). `hex-upper` MUST emit the **uppercase** hexadecimal digest of the same raw HMAC-SHA256 bytes. `base64` MUST emit the HMAC-SHA256 digest as RFC 4648 Base64 with no line wrap, using UTF-8 key and message bytes. The node output port remains a **string**. The lowercase hex binding (`hmac.hex`) MUST stay lowercase; `hex-upper` MUST NOT write onto it. Flow MUST NOT HMAC. An encoding value other than `hex`, `hex-upper`, or `base64` MUST fail validation. A definition MUST NOT introduce a vendor signer type to obtain Base64 or uppercase HMAC.

#### Scenario: Default hex keeps Gaode and IDPS
- **WHEN** an HMAC node omits encoding or sets `hex` over UTF-8 key and message bytes
- **THEN** the signer output is the lowercase hex digest of HMAC-SHA256 and existing definitions that bind that hex port stay valid

#### Scenario: base64 emits RFC 4648 without wrap
- **WHEN** an HMAC node sets encoding `base64` over UTF-8 secret key and UTF-8 canonical bytes
- **THEN** the signer output is RFC 4648 Base64 of the raw HMAC-SHA256 digest (`=` padding as needed, no CR/LF), and MUST NOT be the hex encoding of that digest

#### Scenario: hex-upper emits uppercase digest
- **WHEN** an HMAC node sets encoding `hex-upper` over the same key and message bytes as a `hex` node
- **THEN** the signer output is the uppercase hexadecimal digest and MUST NOT equal the lowercase hex output, and MUST NOT appear on the `hmac.hex` binding

#### Scenario: Unknown HMAC encoding fails compile
- **WHEN** a definition sets HMAC encoding to a value other than `hex`, `hex-upper`, or `base64`
- **THEN** validation fails and the definition is not compiled

#### Scenario: Unknown vendor signer is rejected
- **WHEN** a definition references a pipeline node type that is not in the built-in catalog (including any `signer.artemis-*` or `canonicalizer.artemis-*` type that is not catalogued)
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Built-in hasher.md5 digests pipeline byte inputs
The built-in pipeline catalog SHALL include `hasher.md5`. The node MUST accept a single **bytes** input port, compute MD5 over those bytes, and emit a **string**. `config.encoding` SHALL be `hex`, `hex-upper`, or `base64`. Omitted encoding MUST behave as `hex`: **lowercase** hexadecimal digest (existing DaHua YAML). `hex-upper` MUST emit the **uppercase** hexadecimal digest of the raw MD5 bytes. `base64` MUST emit RFC 4648 Base64 of the raw MD5 digest with no line wrap (HTTP `Content-MD5` form). Hashing MUST run inside Pipeline, not Flow. String or secret material MUST be converted to bytes by a prior pipeline node (for example `canonicalizer.concat`) before entering `hasher.md5`. The lowercase hex binding (`hasher.hex`) MUST stay lowercase; `hex-upper` MUST NOT write onto it. An unknown hasher type MUST fail validation. An encoding value other than `hex`, `hex-upper`, or `base64` MUST fail validation. A type edge of `secret` or `string` directly into `hasher.md5.in` MUST fail compile.

#### Scenario: Concat bytes hash to lowercase hex
- **WHEN** a pipeline connects concat output bytes to `hasher.md5.in` and executes with omitted encoding or `hex`
- **THEN** the hasher output is the lowercase MD5 hex of those bytes and Flow MUST NOT compute MD5

#### Scenario: Direct secret into hasher.in fails compile
- **WHEN** a definition connects a `secretRef` edge directly to `hasher.md5.in` without a bytes-typed producer
- **THEN** compile fails with a pipeline type error and zero HTTP

#### Scenario: Unknown hasher type fails compile
- **WHEN** a definition references `hasher.md5-vendor` or another non-catalog hasher type
- **THEN** compile fails with an unknown-node validation code and zero HTTP

#### Scenario: base64 MD5 matches Content-MD5 form
- **WHEN** a hasher.md5 node sets encoding `base64` over known input bytes
- **THEN** the output is RFC 4648 Base64 of the raw MD5 digest and MUST NOT be the lowercase hex of that digest

#### Scenario: hex-upper MD5 matches uppercase token form
- **WHEN** a hasher.md5 node sets encoding `hex-upper` over known input bytes
- **THEN** the output is the uppercase MD5 hex of those bytes, MUST NOT equal the lowercase hex of those bytes, and MUST NOT appear on the `hasher.hex` binding

#### Scenario: Unknown MD5 encoding fails compile
- **WHEN** a definition sets hasher.md5 encoding to a value other than `hex`, `hex-upper`, or `base64`
- **THEN** validation fails and the definition is not compiled
