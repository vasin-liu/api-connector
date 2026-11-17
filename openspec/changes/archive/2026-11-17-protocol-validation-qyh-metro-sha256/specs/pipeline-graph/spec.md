# Spec Delta

## ADDED Requirements

### Requirement: Built-in hasher.sha256 digests pipeline byte inputs
The built-in pipeline catalog SHALL include `hasher.sha256`. The node MUST accept one bytes input, compute SHA-256 over those bytes, and emit a string. `config.encoding` SHALL be `hex`, `hex-upper`, or `base64`. Omitted encoding MUST behave as lowercase hex. Hashing MUST run inside Pipeline, not Flow. A secret or string edge directly into `hasher.sha256.in` MUST fail compile. An encoding outside those three values MUST fail validation.

#### Scenario: Concat bytes hash to lowercase SHA-256 hex
- **WHEN** a pipeline connects concat output bytes to `hasher.sha256.in` and executes with omitted encoding or `hex`
- **THEN** the hasher output is the lowercase SHA-256 hex of those bytes and Flow MUST NOT compute SHA-256

#### Scenario: Direct secret into hasher.sha256.in fails compile
- **WHEN** a definition connects a `secretRef` edge directly to `hasher.sha256.in` without a bytes-typed producer
- **THEN** compile fails with a pipeline type error and zero HTTP

#### Scenario: hex-upper SHA-256 is not the lowercase digest
- **WHEN** a `hasher.sha256` node sets encoding `hex-upper` over the UTF-8 bytes of `1700000000test-metro-token11700000000`
- **THEN** the output is `F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5` and MUST NOT equal the lowercase hex of those bytes

#### Scenario: Unknown SHA-256 encoding fails compile
- **WHEN** a definition sets `hasher.sha256` encoding to a value other than `hex`, `hex-upper`, or `base64`
- **THEN** validation fails and the definition is not compiled

### Requirement: Secret on a SHA-256 feeder uses HASH sink
A `secretRef` part on the concat that directly feeds `hasher.sha256` SHALL declare sink `HASH`. `HMAC`, `AUTHORIZATION`, `BODY`, or a missing sink on that feeder MUST be rejected and MUST NOT emit a digest from that admission.

#### Scenario: HASH sink on the SHA-256 feeder is allowed
- **WHEN** a token is a concat part with sink `HASH` for its own api id and the concat bytes feed `hasher.sha256`
- **THEN** compile succeeds and execute produces a digest without placing that token's plaintext in DecisionTrace

#### Scenario: BODY sink on the SHA-256 feeder is denied
- **WHEN** a secret concat part that directly feeds `hasher.sha256` declares sink `BODY`
- **THEN** validation or execute denies the sink and MUST NOT emit a digest from that admission
