# pipeline-graph Specification

## Purpose

Defines Pipeline as typed data-flow graphs for encode, transform, hash, and sign operations, kept separate from Flow control, with compile-time port checks and no arbitrary cycles.

## Requirements

### Requirement: Pipeline is data flow only
A pipeline SHALL perform encode, decode, transform, encrypt, decrypt, hash, HMAC, compress, canonicalize, and sign. A pipeline MUST NOT branch, loop, retry, authenticate, or detect challenges. Those behaviors belong to Flow.

#### Scenario: HMAC is a pipeline node
- **WHEN** a definition computes HMAC over secret, nonce, and timestamp
- **THEN** that computation is a pipeline graph, not a flow transition

### Requirement: Ports are type-checked at compile time
Each node SHALL declare input and output ports with types. The compiler MUST reject type-incompatible edges, missing required inputs, unknown node types, and single-input ports with multiple inbound edges.

#### Scenario: String connected to bytes HMAC input fails compile
- **WHEN** a signer HMAC bytes input is connected to a string output without a codec
- **THEN** validation or compile fails with a pipeline type error and no execution starts

#### Scenario: Missing required edge fails compile
- **WHEN** a required HMAC input port is unconnected
- **THEN** validation fails

### Requirement: Cycles are forbidden
Pipeline graphs MUST NOT contain cycles. Iterative data processing, if needed later, MUST use an explicit loop processor rather than graph cycles.

#### Scenario: Cyclic graph is rejected
- **WHEN** pipeline edges form a cycle
- **THEN** validation fails and the definition is not compiled

### Requirement: Codec, transformer, and processor remain distinct
Codecs SHALL convert objects to and from wire formats. Transformers SHALL map like-typed structures. Data processors SHALL perform encrypt, hash, HMAC, base64, compress, and vendor encode. Canonicalizer MUST run before Signer when producing a signature over a canonical representation. The built-in catalog SHALL include a canonicalizer that sorts query parameters by key and concatenates `key=value` pairs (configurable separator) so HMAC-over-sorted-query protocols do not sort inside Flow.

#### Scenario: Canonicalize then sign
- **WHEN** a request requires HMAC over concatenated secret, nonce, and timestamp
- **THEN** the system canonicalizes those parts first and then signs the canonical bytes

#### Scenario: Sorted query then HMAC
- **WHEN** a request requires HMAC over query parameters sorted by key
- **THEN** a pipeline canonicalizer emits that canonical string or bytes and a signer HMAC node consumes it; Flow MUST NOT sort the query map

### Requirement: Secret edges declare sinks
Edges that carry secrets SHALL declare an allowed sink such as HMAC or Signer. A secret MUST NOT flow into a generic string output.

#### Scenario: Secret into HMAC is allowed
- **WHEN** an API key secret is connected to an HMAC key port with sink HMAC
- **THEN** compile succeeds for that edge

### Requirement: Sorted-query encoding is configurable
The built-in sorted-query canonicalizer SHALL accept encoding `none` or `rfc3986`. Omitted encoding MUST behave as `none`: sort keys then join unescaped `key=value` pairs with the configured separator. `rfc3986` MUST percent-encode each key and value (UTF-8; space as `%20`; `*` as `%2A`; `~` left as `~`) and then sort those encoded `key=value` items before joining. Flow MUST NOT encode or sort the query map. An encoding value other than `none` or `rfc3986` MUST fail validation.

#### Scenario: Default none keeps Gaode plaintext
- **WHEN** a sorted-query node omits encoding or sets `none` over `city=110000`, `key=test-ak`, `timestamp=1000`
- **THEN** the canonical bytes are `city=110000&key=test-ak&timestamp=1000` with no percent-encoding

#### Scenario: rfc3986 encodes then sorts
- **WHEN** a sorted-query node sets encoding `rfc3986` over a map that includes a space in a value
- **THEN** that value appears as `%20` in the canonical string and item order is the ASCII order of the encoded `key=value` strings

#### Scenario: Unknown encoding fails compile
- **WHEN** a definition sets sorted-query encoding to a value other than `none` or `rfc3986`
- **THEN** validation fails and the definition is not compiled

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

### Requirement: Secret material for hashing uses HASH sink on concat
A `secretRef` part on the concat that directly feeds `hasher.md5` SHALL declare sink `HASH`. The compiler or sink policy MUST reject `HMAC`, `AUTHORIZATION`, `BODY`, or missing sink when the secret is admitted on that hasher feeder. A secret admitted with sink `BODY` on a different concat, whose output bytes are then consumed as an opaque part, is not a hash admission. Intermediate hex digests MAY be ordinary strings for later concat/hasher steps. Username and password used in nested MD5 SHALL both enter via HASH-sunk `secretRef` parts — not via revealing a secret variable through generic string interpolation.

#### Scenario: HASH sink on concat then hasher is allowed
- **WHEN** password and username secrets are concat parts with sink `HASH` for their own api id and the concat bytes feed `hasher.md5`
- **THEN** compile succeeds and execute produces digests without placing plaintext password or username in DecisionTrace

#### Scenario: HMAC sink on a hash concat part is denied
- **WHEN** a secret concat part that feeds a hasher declares sink `HMAC`
- **THEN** validation or execute denies the sink and MUST NOT emit a digest from that admission

#### Scenario: BODY sink on a hasher feeder is denied
- **WHEN** a secret concat part that directly feeds `hasher.md5` declares sink `BODY`
- **THEN** validation or execute denies the sink and MUST NOT emit a digest from that admission

### Requirement: Wire-body concat admits a BODY secret
A `canonicalizer.concat` that supplies the HTTP body SHALL accept a `secretRef` part with sink `BODY`, and that part's UTF-8 bytes SHALL be included in the outbound body. That concat MUST NOT be the direct bytes feeder of `hasher.md5`. A `secretRef` on that body concat with sink `HASH`, `HMAC`, `AUTHORIZATION`, or an omitted sink MUST fail validation and MUST NOT send HTTP.

#### Scenario: BODY secret becomes body bytes
- **WHEN** a body concat part references a secret with sink `BODY` for its own api id
- **THEN** compile succeeds and the outbound body contains that secret's UTF-8 bytes

#### Scenario: HASH sink on a wire-body concat is rejected
- **WHEN** a concat that supplies the HTTP body contains a `secretRef` whose sink is `HASH` or omitted
- **THEN** validation fails and no HTTP is sent

### Requirement: Hash material consumes the wire body bytes
A concat that feeds `hasher.md5` SHALL accept the exact bytes emitted by the body concat as one part. That part MUST NOT re-admit the body secret under sink `HASH`. A secret key appended as its own part of the hasher concat MUST still declare sink `HASH`.

#### Scenario: Body bytes plus HASH secret feed the hasher
- **WHEN** the hasher concat parts are the URI path, the body concat's bytes, and a secret with sink `HASH`
- **THEN** compile succeeds and the digest is over those concatenated bytes

#### Scenario: Body secret is not admitted twice
- **WHEN** the appkey secret is admitted only on the body concat with sink `BODY`
- **THEN** the hasher concat does not contain a second `secretRef` for that appkey

### Requirement: Built-in hasher.sm3 digests pipeline byte inputs
The built-in pipeline catalog SHALL include `hasher.sm3`. The node MUST accept one bytes input, compute SM3 over those bytes, and emit a string. `config.encoding` SHALL be `hex`, `hex-upper`, or `base64`. Omitted encoding MUST behave as lowercase hex. Hashing MUST run inside Pipeline, not Flow. A secret or string edge directly into `hasher.sm3.in` MUST fail compile. An encoding outside those three values MUST fail validation.

#### Scenario: Concat bytes hash to lowercase SM3 hex
- **WHEN** a pipeline connects concat output bytes to `hasher.sm3.in` and executes with omitted encoding or `hex`
- **THEN** the hasher output is the lowercase SM3 hex of those bytes and Flow MUST NOT compute SM3

#### Scenario: Direct secret into hasher.sm3.in fails compile
- **WHEN** a definition connects a `secretRef` edge directly to `hasher.sm3.in` without a bytes-typed producer
- **THEN** compile fails with a pipeline type error and zero HTTP

#### Scenario: hex-upper SM3 is not the lowercase digest
- **WHEN** a `hasher.sm3` node sets encoding `hex-upper` over known input bytes
- **THEN** the output is the uppercase SM3 hex of those bytes and MUST NOT equal the lowercase hex of those bytes

#### Scenario: Unknown SM3 encoding fails compile
- **WHEN** a definition sets `hasher.sm3` encoding to a value other than `hex`, `hex-upper`, or `base64`
- **THEN** validation fails and the definition is not compiled

### Requirement: Secret on an SM3 feeder uses HASH sink
A `secretRef` part on the concat that directly feeds `hasher.sm3` SHALL declare sink `HASH`. `HMAC`, `AUTHORIZATION`, `BODY`, or a missing sink on that feeder MUST be rejected and MUST NOT emit a digest from that admission.

#### Scenario: HASH sink on the SM3 feeder is allowed
- **WHEN** an app secret is a concat part with sink `HASH` for its own api id and the concat bytes feed `hasher.sm3`
- **THEN** compile succeeds and execute produces a digest without placing that secret's plaintext in DecisionTrace

#### Scenario: BODY sink on the SM3 feeder is denied
- **WHEN** a secret concat part that directly feeds `hasher.sm3` declares sink `BODY`
- **THEN** validation or execute denies the sink and MUST NOT emit a digest from that admission

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
