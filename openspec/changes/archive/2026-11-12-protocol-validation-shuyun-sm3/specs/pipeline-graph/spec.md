# Spec Delta

## ADDED Requirements

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
