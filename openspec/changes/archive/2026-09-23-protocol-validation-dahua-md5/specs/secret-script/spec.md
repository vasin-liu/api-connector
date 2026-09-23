## ADDED Requirements

### Requirement: HASH is an allowed secret sink
The secret sink catalog SHALL include `HASH` for feeding secret material into bytes that a hash DataProcessor consumes (for example via `canonicalizer.concat` parts into `hasher.md5`). Before applying a secret to a `HASH` sink, the system SHALL check sink type and destination api id equality exactly as for `HMAC` and `AUTHORIZATION`. Trace, log, and generic-string sinks MUST remain denied. A successful hash MUST NOT leave plaintext secret material in DecisionTrace or exception messages.

#### Scenario: HASH sink with matching api id is allowed
- **WHEN** a password or username secret belonging to api A is applied to a `HASH` sink toward api A
- **THEN** the sink is allowed and those secret bytes may enter the hash input path

#### Scenario: HASH sink with wrong api id is denied
- **WHEN** a secret belonging to api A would be placed on a `HASH` sink toward api B
- **THEN** the system denies the sink and does not hash that secret for the request

#### Scenario: Password and username plaintext are redacted from trace
- **WHEN** authentication hashes password and username via `HASH` into nested MD5
- **THEN** DecisionTrace and exception messages MUST NOT contain that plaintext
