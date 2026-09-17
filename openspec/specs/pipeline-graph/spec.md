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
Codecs SHALL convert objects to and from wire formats. Transformers SHALL map like-typed structures. Data processors SHALL perform encrypt, hash, HMAC, base64, compress, and vendor encode. Canonicalizer MUST run before Signer when producing a signature over a canonical representation.

#### Scenario: Canonicalize then sign
- **WHEN** a request requires HMAC over concatenated secret, nonce, and timestamp
- **THEN** the system canonicalizes those parts first and then signs the canonical bytes

### Requirement: Secret edges declare sinks
Edges that carry secrets SHALL declare an allowed sink such as HMAC or Signer. A secret MUST NOT flow into a generic string output.

#### Scenario: Secret into HMAC is allowed
- **WHEN** an API key secret is connected to an HMAC key port with sink HMAC
- **THEN** compile succeeds for that edge
