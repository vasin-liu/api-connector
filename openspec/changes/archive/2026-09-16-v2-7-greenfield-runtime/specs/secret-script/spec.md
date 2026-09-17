## Purpose

Defines typed data values, secret material isolation, sink destination checks, and a capability-limited script runtime so secrets and host APIs cannot leak through generic strings, logs, or scripts.

## ADDED Requirements

### Requirement: Secrets are not generic strings
Secret values SHALL be a distinct data type. The system MUST NOT provide a general-purpose reveal-to-string operation for secrets. Secrets MUST NOT appear in ordinary logs, traces, generic script output, or exception messages.

#### Scenario: API key is redacted from trace
- **WHEN** an execution sends an API key query parameter
- **THEN** traces and logs record a secret reference or redacted marker and MUST NOT contain the key material

#### Scenario: Secret literal in definition is rejected
- **WHEN** a definition places secret material as a plain value instead of a reference
- **THEN** validation fails

### Requirement: Definition stores references not material
Credentials in a definition SHALL reference secret providers through a CredentialResolver. A username-password credential SHALL resolve to two secret materials (username and password) without becoming generic strings. The compiled definition MUST NOT embed resolved secret material.

#### Scenario: Environment secret is resolved at execute
- **WHEN** a secret reference exists and the provider can resolve it at execute time
- **THEN** the outbound request is signed or authorized using that material without storing it in the definition

#### Scenario: Missing secret fails execute
- **WHEN** the provider cannot resolve a required secret
- **THEN** execution fails with SECRET_UNRESOLVABLE and no outbound call is made that requires that secret

#### Scenario: Username-password is a composite credential
- **WHEN** Mock B login builds a JSON body from `account.username` and `account.password`
- **THEN** both fields are resolved via CredentialResolver and MUST NOT appear as plain strings in traces

### Requirement: Sink policy includes destination
Before a secret is applied to a sink, the system SHALL check both sink type and destination. Authorization sinks MUST be allowed only when the destination api id equals the credential's declared api id. Trace, log, and generic string sinks MUST be denied.

#### Scenario: Wrong api id authorization is denied
- **WHEN** a secret belonging to api A would be placed on an authorization sink toward api B
- **THEN** the system denies the sink and does not send the request

#### Scenario: HMAC sink is allowed
- **WHEN** the same secret is used as HMAC key for its own api
- **THEN** the sink is allowed

### Requirement: Scripts receive capabilities not the full execution context
Scripts SHALL access only approved variables, request/response views, deterministic clock and random, and approved crypto utilities. Scripts MUST NOT access filesystem, network, process, database, classloader, or reflection. Resource limits MUST be able to interrupt runaway scripts.

#### Scenario: Host isolation
- **WHEN** a script attempts to reflect or open a network connection
- **THEN** the runtime denies the access

#### Scenario: Runaway script is stopped
- **WHEN** a script enters a tight loop
- **THEN** resource limits interrupt the script and the step fails without hanging the execution

### Requirement: Groovy default sandbox is not the built-in engine
The built-in script engine SHALL be a capability-isolated polyglot context. Nashorn, Rhino, and Groovy default sandbox MUST NOT be the built-in implementation.

#### Scenario: Unapproved language runtime is absent
- **WHEN** a definition requests the built-in script engine
- **THEN** the engine is the polyglot capability host, not Groovy default sandbox
