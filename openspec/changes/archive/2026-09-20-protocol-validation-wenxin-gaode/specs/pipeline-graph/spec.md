## MODIFIED Requirements

### Requirement: Codec, transformer, and processor remain distinct
Codecs SHALL convert objects to and from wire formats. Transformers SHALL map like-typed structures. Data processors SHALL perform encrypt, hash, HMAC, base64, compress, and vendor encode. Canonicalizer MUST run before Signer when producing a signature over a canonical representation. The built-in catalog SHALL include a canonicalizer that sorts query parameters by key and concatenates `key=value` pairs (configurable separator) so HMAC-over-sorted-query protocols do not sort inside Flow.

#### Scenario: Canonicalize then sign
- **WHEN** a request requires HMAC over concatenated secret, nonce, and timestamp
- **THEN** the system canonicalizes those parts first and then signs the canonical bytes

#### Scenario: Sorted query then HMAC
- **WHEN** a request requires HMAC over query parameters sorted by key
- **THEN** a pipeline canonicalizer emits that canonical string or bytes and a signer HMAC node consumes it; Flow MUST NOT sort the query map
