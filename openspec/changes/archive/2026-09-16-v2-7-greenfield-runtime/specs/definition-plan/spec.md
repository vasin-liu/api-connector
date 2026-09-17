## Purpose

Defines how a Canonical API Definition is parsed, validated, compiled into an immutable execution plan, and bound to a single execution snapshot, including revision isolation.

## ADDED Requirements

### Requirement: Definition is not executed directly
The system SHALL execute only a compiled ExecutionPlan derived from a Canonical Definition. The system MUST NOT interpret raw YAML or unvalidated definition objects at request time.

#### Scenario: Published definition compiles then executes
- **WHEN** a host submits an execute command for a published definition revision
- **THEN** the system compiles or reuses a cached plan for that definition id and revision and binds the execution to that plan

#### Scenario: Invalid definition is rejected before execute
- **WHEN** a definition fails validation
- **THEN** the system MUST NOT create an execution snapshot and MUST return a validation failure listing all violations

### Requirement: Execution binds an immutable snapshot
Each execution SHALL be bound to an immutable snapshot containing execution id, api id, definition revision, plan id, and start time. The snapshot MUST remain unchanged for the lifetime of that execution.

#### Scenario: Snapshot fields are stable
- **WHEN** an execution starts
- **THEN** later steps, retries, and traces for that execution use the same snapshot values

#### Scenario: New revision does not mutate in-flight execution
- **WHEN** a new definition revision is published while an execution is running
- **THEN** the in-flight execution continues on its original plan id and revision

### Requirement: Plan cache keys on definition id and revision
The system SHALL cache compiled plans by definition id plus revision. An unchanged revision MUST reuse the same plan id. A changed revision MUST compile a new plan.

#### Scenario: Same revision reuses plan
- **WHEN** two execute commands target the same published definition id and revision
- **THEN** both executions use the same plan id

#### Scenario: Revision change yields a new plan
- **WHEN** the published revision changes and a new execute command is issued
- **THEN** the new execution uses a different plan id than executions of the previous revision

### Requirement: Session compatibility across revisions
A session MUST be reused across definition revisions when `authProfile` and `credentialRef` are unchanged, even if `definitionRevision` on the recorded SessionKey differs. If either `authProfile` or `credentialRef` changes, the system MUST treat the previous session as incompatible and MUST run Authentication Flow before treating the session as valid. Reuse MUST use the lookup key and predicate in `session-auth`, not full SessionKey equality.

#### Scenario: Pipeline-only revision reuses session
- **WHEN** a new revision changes only pipeline or codec configuration and `authProfile` plus `credentialRef` are unchanged
- **THEN** an existing valid session is reused

#### Scenario: Auth profile change invalidates session
- **WHEN** a new revision changes `authProfile` or `credentialRef`
- **THEN** the previous session MUST NOT be reused for the new revision

### Requirement: Runtime executes only published definitions
The system SHALL execute only definitions in the PUBLISHED lifecycle state. Phase 0 SHALL use an in-memory definition registry: newly loaded valid definitions default to PUBLISHED. Draft or disabled definitions MUST be rejected.

#### Scenario: Draft cannot execute
- **WHEN** a host requests execute for a non-published revision
- **THEN** the system returns a not-published error and performs no outbound HTTP

#### Scenario: Loaded definition defaults to published
- **WHEN** a valid definition is registered in the in-memory registry without an explicit lifecycle
- **THEN** execute is allowed and outbound HTTP may proceed

### Requirement: Execute input is execution-scoped
Host input values SHALL populate EXECUTION scope only. The system MUST reject input that targets SESSION or GLOBAL. The system MUST reject secret literals in input; secrets MUST enter only through SecretProvider references.

#### Scenario: Input cannot write session
- **WHEN** an execute command includes an input bound to SESSION scope
- **THEN** the command is rejected and no execution snapshot is created

#### Scenario: Secret literal in input is rejected
- **WHEN** an execute command passes secret material as a plain string value
- **THEN** the command is rejected
