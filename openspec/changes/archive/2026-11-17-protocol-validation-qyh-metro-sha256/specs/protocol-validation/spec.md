# Spec Delta

## ADDED Requirements

### Requirement: Qyh metro signature matches the published SHA-256
The system SHALL GET the published metro path so that header `signature` is the uppercase hexadecimal SHA-256 of the timestamp, the token, the nonce, and that same timestamp, joined with an empty separator. Flow MUST NOT hash. FakeTransport MUST NOT call the public internet.

#### Scenario: First GET carries the frozen signature
- **WHEN** the clock instant is `1700000000000` ms and the nonce source yields `1`
- **THEN** the request is `GET https://qyh-metro.example/jtj/in-out` with query `start_time=2026-01-01T00:00:00&end_time=2026-01-01T00:10:00`, headers `x-tif-paasid` `test-pass`, `timestamp` `1700000000`, `nonce` `1`, and `signature` `F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5`, and it has no body

### Requirement: Qyh metro hash stays inside the pipeline catalog
The signature MUST be `hasher.sha256` with encoding `hex-upper`, over `canonicalizer.concat` with an empty separator. The concat parts are the timestamp, the token admitted with sink `HASH`, the nonce, and the timestamp again. Flow MUST only assign and choose transitions.

#### Scenario: Catalog nodes produce the signature
- **WHEN** the definition builds the metro signature
- **THEN** the digest comes from `hasher.sha256` and Flow does not compute SHA-256

### Requirement: Qyh metro retry rebuilds timestamp, nonce, and signature
HTTP 503 MUST select `RETRY_FLOW`. The authentication flow MUST assign a new epoch-second timestamp and a new nonce, MUST recompute the signature, and MUST NOT send HTTP. The following GET MUST differ in `timestamp`, `nonce`, and `signature`. The query MUST stay unchanged.

#### Scenario: Second GET uses the next second and nonce
- **WHEN** the first GET returns 503 and the next clock instant is `1700000002000` ms with nonce `2`
- **THEN** authentication sends no HTTP, and the following GET keeps the same query, with `timestamp` `1700000002`, `nonce` `2`, and `signature` `0B3FF5CB28670D23AA4266EF97AF9512DBB7463BE874501E10800789CF45033E`

### Requirement: Qyh metro success leaves the response bytes
HTTP 200 MUST select SUCCESS. The response body MUST remain the bytes FakeTransport returned.

#### Scenario: Success body is not rewritten
- **WHEN** FakeTransport returns 200 with body `{"ok":true}`
- **THEN** the outcome is SUCCESS and the result body equals those bytes

### Requirement: Qyh metro token stays off the wire
The metro token MUST be absent from outbound headers, body, and query, and from DecisionTrace and exception messages. `test-pass` MUST appear as `x-tif-paasid` and MUST be absent from DecisionTrace and exception messages. Production secrets MUST NOT appear in git. The definition MUST NOT depend on a vendor SDK or a vendor pipeline node.

#### Scenario: Token never appears on the wire
- **WHEN** the signed GET is sent
- **THEN** no query, header, or body contains `test-metro-token`, and DecisionTrace and exception messages do not contain it

#### Scenario: Pass id stays out of the trace
- **WHEN** the signed GET is sent
- **THEN** `x-tif-paasid` is `test-pass`, and DecisionTrace and exception messages do not contain `test-pass`
