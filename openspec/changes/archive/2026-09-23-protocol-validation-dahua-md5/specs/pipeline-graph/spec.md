## ADDED Requirements

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
