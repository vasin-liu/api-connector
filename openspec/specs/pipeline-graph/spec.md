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

### Requirement: Built-in hasher.md5 digests pipeline byte inputs
The built-in pipeline catalog SHALL include `hasher.md5`. The node MUST accept a single **bytes** input port, compute MD5 over those bytes, and emit a **lowercase** hexadecimal string. Hashing MUST run inside Pipeline, not Flow. String or secret material MUST be converted to bytes by a prior pipeline node (for example `canonicalizer.concat`) before entering `hasher.md5`. An unknown hasher type MUST fail validation. A type edge of `secret` or `string` directly into `hasher.md5.in` MUST fail compile.

#### Scenario: Concat bytes hash to lowercase hex
- **WHEN** a pipeline connects concat output bytes to `hasher.md5.in` and executes
- **THEN** the hasher output is the lowercase MD5 hex of those bytes and Flow MUST NOT compute MD5

#### Scenario: Direct secret into hasher.in fails compile
- **WHEN** a definition connects a `secretRef` edge directly to `hasher.md5.in` without a bytes-typed producer
- **THEN** compile fails with a pipeline type error and zero HTTP

#### Scenario: Unknown hasher type fails compile
- **WHEN** a definition references `hasher.md5-vendor` or another non-catalog hasher type
- **THEN** compile fails with an unknown-node validation code and zero HTTP

### Requirement: Secret material for hashing uses HASH sink on concat
A `secretRef` part that contributes bytes later hashed by `hasher.md5` SHALL declare sink `HASH`. The compiler or sink policy MUST reject `HMAC`, `AUTHORIZATION`, or missing sink when the secret is admitted for hashing. Intermediate hex digests MAY be ordinary strings for later concat/hasher steps. Username and password used in nested MD5 SHALL both enter via HASH-sunk `secretRef` parts — not via revealing a secret variable through generic string interpolation.

#### Scenario: HASH sink on concat then hasher is allowed
- **WHEN** password and username secrets are concat parts with sink `HASH` for their own api id and the concat bytes feed `hasher.md5`
- **THEN** compile succeeds and execute produces digests without placing plaintext password or username in DecisionTrace

#### Scenario: HMAC sink on a hash concat part is denied
- **WHEN** a secret concat part that feeds a hasher declares sink `HMAC`
- **THEN** validation or execute denies the sink and MUST NOT emit a digest from that admission
