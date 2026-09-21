## ADDED Requirements

### Requirement: Assign can stamp ISO-offset time and generate a nonce
Flow assign SHALL support `{ now: epochMillis }`, `{ now: isoOffset }`, and `{ generate: nonce }`. `isoOffset` MUST format the injected clock instant in that clock's zone as `yyyy-MM-dd'T'HH:mm:ssXXX`. `generate: nonce` MUST take the next value from an injectable nonce source so successive assigns (including after `RETRY_FLOW`) yield distinct values. Flow MUST NOT compute HMAC, sort query maps, or percent-encode query keys. An unknown `now` or `generate` form MUST fail validation.

#### Scenario: isoOffset uses clock instant and zone
- **WHEN** a step assigns `{ now: isoOffset }` with a clock instant of 1000 ms in UTC
- **THEN** the assigned value is `1970-01-01T00:00:01Z`

#### Scenario: RETRY_FLOW receives a new nonce
- **WHEN** a definition assigns `{ generate: nonce }` on the first send and again after `RETRY_FLOW`
- **THEN** the second nonce differs from the first and both values appear on the corresponding outbound requests

#### Scenario: Unknown now form fails validate
- **WHEN** a definition assigns `{ now: epochSeconds }` or any `now` form other than `epochMillis` or `isoOffset`
- **THEN** validation fails and the definition is not compiled
