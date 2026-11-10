# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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
