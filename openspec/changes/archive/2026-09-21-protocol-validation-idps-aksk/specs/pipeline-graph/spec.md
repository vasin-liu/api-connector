## ADDED Requirements

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
