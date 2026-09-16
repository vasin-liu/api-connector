## 1. Spikes and module skeleton

- [x] 1.1 Run JSONPath spike: prove a restricted library or a five-operator parser rejects Filter/`..`/`()`; write the choice in `config` notes and add a failing/passing unit that `$.a[?(@.b)]` is rejected
- [x] 1.2 Run GraalVM Polyglot spike on the chosen JDK: `HostAccess.EXPLICIT` blocks reflection and `resourceLimits` interrupts a tight loop; record JDK requirement if Zulu cannot enforce limits
- [x] 1.3 Add Maven modules `core`, `runtime`, `transport`, `config` (Java 21, no Spring in core) and verify `mvn -pl` compile of empty modules succeeds
- [x] 1.4 Add in-process `ApiClient` / `ExecuteCommand` / `ExecutionSnapshot` / `ExecutionResult` types in `core` and verify they compile without depending on existing `api-connector-engine`

## 2. Flow conditions and validation (0a)

- [x] 2.1 Implement Condition AST evaluator for status, header exists/equals, variable, all/any/not using tables H/J in `docs/design/v2.7-greenfield/05-condition-transition-tables.md` and verify J2.C2, J2.C4, J2.C8, J3.B5 unit tests pass
- [x] 2.2 Plug JSONPath spike result into JsonPathCondition and verify `VAL_JSONPATH_FORBIDDEN` for filter expressions
- [x] 2.3 Implement YAML parse + Normalize defaults from `07-plan-compiler.md` section Q and verify two compiles of Mock A YAML produce identical `planId`
- [x] 2.4 Implement DefinitionValidator codes U1–U3 needed for Mock A (missing step id, GLOBAL write, AUTH without flow, AUTHENTICATE without `then`, empty all/any) and verify each code with a YAML fragment test
- [x] 2.5 Compile Mock A to `ExecutionPlan` with `capabilities={LINEAR_FLOW}` and verify snapshot fields in `07` section S
- [x] 2.6 Compile Mock C fixture and verify `transitions[0].action` is AUTHENTICATE and 0a execute returns `PLAN_CAPABILITY_UNSUPPORTED`

## 3. Linear execute (0a)

- [x] 3.1 Implement VariableRuntime scopes and commit/discard rules; verify GLOBAL write cannot happen at runtime and FAILURE discards local mutation
- [x] 3.2 Implement FakeTransport plus `BytesBody`/`EmptyBody`/`completed=false` and verify written-then-dropped POST maps to UNKNOWN_OUTCOME (not TIMEOUT) with a single invocation (table A4 / Mock I)
- [x] 3.3 Implement linear FlowRuntime REQUEST+ASSIGN for Mock A and verify A1 SUCCESS (HTTP 200, one call) and A2 FAILURE (403 unmatched, one call)
- [x] 3.4 Implement StreamBody skip of classifier (type only) and verify a stream response does not evaluate challenge conditions
- [x] 3.5 Implement in-memory definition registry (load defaults to PUBLISHED; explicit DRAFT rejected) wired to `ApiClient.execute`, snapshot, and plan cache; verify a new PUBLISHED revision does not change an in-flight snapshot's `planId` and a DRAFT execute returns not-published with zero HTTP
- [x] 3.6 Implement `ApiClient.cancel` so outcome is CANCELLED, mutations discard, and no further outbound send; verify cancel between first response and replay sends no second request
- [x] 3.7 Reject execute input that targets SESSION/GLOBAL or passes secret literals; verify no snapshot is created

## 4. Session and authentication flow (0b)

- [x] 4.1 Implement recorded SessionKey (includes revision) with lookup key `(apiId, authProfile, credentialRef)` plus compatibility predicate; verify Mock H: pipeline-only revision reuses session and authProfile change does not
- [x] 4.2 Implement SessionCoordinator single refresh owner (AUTHENTICATE and REFRESH_SESSION share the auth flow) and verify 100 concurrent expired sessions perform one authentication and waiters re-read generation
- [x] 4.3 Implement shared failure + cooldown and verify Mock G (Mock B YAML, login always fails): one login, shared failure, cooldown execute does not login again
- [x] 4.4 Implement AUTHENTICATE then REPLAY_REQUEST by rendering `OriginalRequestTemplate` (passthrough pipeline allowed; MUST NOT clone bytes or inject headers onto the previous RawHttpRequest) and verify Mock B L1: r1 without token, r2 login, r3 with bearer, one execution id
- [x] 4.5 Implement maxAuthAttempts and maxDepth and verify exceeding either yields AUTH_ATTEMPT_EXCEEDED without another auth flow
- [x] 4.6 Implement CookieStore under session and verify Mock E YAML: cookies are attached only from the store
- [x] 4.7 Implement multi-request authentication flow and verify Mock D YAML sequential hops before business replay

## 5. Pipeline graph and replay rebuild (0c)

- [x] 5.1 Implement pipeline graph validate: cycle, type mismatch, missing required port (table W P0–P7) and verify P4 cycle fails compile
- [x] 5.2 Implement nodes passthrough, codec.json, canonicalizer.concat, signer.hmac-sha256 and verify Mock C challenge HMAC bytes against a known test vector
- [x] 5.3 Implement OriginalRequestTemplate render with an injectable clock (no byte clone) and verify Mock C r2 timestamp and Authorization differ from r1
- [x] 5.4 Implement RETRY_REQUEST for 503 without generation bump and verify Mock C C6
- [x] 5.5 Implement RETRY_FLOW when nonce is stale and verify HMAC of nonce-1 is never sent on the next business call
- [x] 5.6 Enforce ReplayPolicy UNSAFE/UNKNOWN defaults (policy MUST only downgrade resend to FAIL) and verify Mock I POST drop and Mock B login drop do not auto-replay

## 6. Secrets, scripts, and trace (0d)

- [x] 6.1 Implement SecretValue without reveal, CredentialResolver (including username-password), Environment/File SecretProvider, and SECRET_UNRESOLVABLE when missing; verify definition never stores resolved material and Mock B login body secrets are redacted
- [x] 6.2 Implement SecretSinkPolicy with SinkDestination apiId equality and verify wrong-api Authorization is denied
- [x] 6.3 Implement GraalVM script CapabilityContext per spike 1.2 and verify reflection/network denied and tight loop interrupted (unit tests only; not part of Mock A–I)
- [x] 6.4 Implement DecisionTrace + attempt ids per `06-attempt-sequences.md` and verify Mock C M1 chain plus Authorization values redacted
- [x] 6.5 Add redaction tests for logs, exceptions, and generic script output and verify secret material is absent

## 7. Phase 0 gate

- [x] 7.1 Run automated tests for Mock A–C YAML in `03-canonical-yaml-mock-a-c.md` and Mock D/E/H/I YAML in `08-canonical-yaml-mock-d-i.md` (F = Mock C permission branch; G = Mock B + failing login) covering 49.1–49.8, and verify all pass without calling existing engine/auth/mapping modules
- [x] 7.2 Add CI job for the new modules only and verify it does not require Vue build or Groovy
