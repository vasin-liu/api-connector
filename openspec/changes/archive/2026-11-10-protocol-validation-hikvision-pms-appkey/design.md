# Design

## Context

See `proposal.md`. The published PMS definition (`docs/design/v2.7-protocols/hikvision-pms.yaml`) builds the body with one `canonicalizer.concat` of GLOBAL `bodyJson` = `{"appkey":"test-ak"}`, and a second concat hashes `uriPath + bodyJson + secretKey(HASH)`. `PipelineGraphValidator.checkHashAdmission` requires sink `HASH` on every `secretRef` of a concat that edges into `hasher.md5.in`. `SecretSink.BODY` already exists; `SecretSinkPolicy.check` only compares api id and does not distinguish sink kinds. Concat parts today are `value`, `var`, or `secretRef` — not another node's port. Cross-pipeline edges are illegal. `request.body.pipeline` is what the sender executes.

## Goals / Non-Goals

**Goals:**

- One JSON assembly: prefix + appkey `secretRef` sink `BODY` + suffix. Hash and HTTP both use those bytes.
- Compile-time split: a request-body concat admits only sink `BODY`; a hasher-feeder concat admits only sink `HASH`.
- Published vector stays `{"appkey":"test-ak"}` / token `EDAA4B3136EA75DF9A435C95B920DD9E`.

**Non-Goals:**

- A new node type, a `from: node.port` concat part, or `codec.json` for this body.
- JSON escaping. Fixture appkey has no `"` or `\`.
- Changing `RETRY_REQUEST`, Artemis, DaHua HASH admission, or SK's sink.

## Decisions

### D1. Appkey is a second credential, not a literal

**Choice:** Add credential `appkey` (`valueRef: secret/hikvision-pms/appkey`, `apiId: hikvision-pms`) beside `secretKey`. Delete GLOBAL `bodyJson`. Tests still inject `test-ak` and `test-sk`.

**Why:** The definition must not contain the appkey material. The published body stays the same only because the fixture secret is `test-ak`.

**Alternatives:** Keep the literal and add a second definition — two gates for one vector, and the literal is the gap this change closes.

### D2. Body bytes cross the hash step as an execution variable

**Choice:** `wireBody` concat parts are `{"appkey":"`, `secretRef: appkey` sink `BODY`, `"}`. An earlier flow step runs `wireBody` and binds `execution.wireBody` from that concat output. `tokenHash` concat parts are `global.uriPath`, `var: execution.wireBody`, `secretRef: secretKey` sink `HASH`. The request's `body.pipeline` is a concat of only `{ var: execution.wireBody }`, so the sent bytes are that binding, not a second secret admission.

**Why:** Hash and body stay equal without a new part type and without a cross-pipeline edge. The hasher concat never names `appkey`. Running the BODY concat once keeps a single admission.

**Alternatives:** Rebuild the JSON inside `tokenHash` with a second `secretRef` — violates the "not admitted twice" scenario. Edge `wireBody` into `tokenHash` — cross-pipeline edges already fail validation. One pipeline with a new `from` part — catalog shape the previous PMS design deferred.

### D3. Admission is which pipeline the concat sits in

**Choice:** For every concat in a pipeline referenced by `request.body.pipeline`, each `secretRef` must declare sink `BODY`; `HASH`, `HMAC`, `AUTHORIZATION`, or an omitted sink fails validation. For every concat with an edge to `hasher.md5.in`, each `secretRef` must declare sink `HASH`; `BODY` fails the same way `HMAC` already does. A pipeline that is both the request body and a hasher feeder cannot carry a `secretRef`.

**Why:** The two rules name different pipelines, so the split is structural. DaHua's hasher feeder still requires `HASH`. `SecretSinkPolicy` stays api-id-only; wrong-api `BODY` is already denied and gets a test.

**Alternatives:** Infer "body supplier" by following variable bindings — more analysis than this gate needs, because D2 makes the request-body concat secret-free and puts the `BODY` secret on `wireBody`. The validator therefore also treats a concat as a wire-body concat when a flow step binds its output and a later body concat reads that variable. A `HASH` secret on that producer fails validation.

### D4. UTF-8 string binding, then stop

**Choice:** Bind the body concat output as the UTF-8 string of its bytes. `test-ak` is ASCII, so the hash material `/getParkingInfo{"appkey":"test-ak"}test-sk` is unchanged. If the binder cannot store those bytes, or the re-encoded hash input differs from the sent body, stop and update this design. Do not MD5 in Flow, do not put appkey back into a literal, and do not add an encoder.

**Why:** The existing output binding (`execution.token: hasher.out`) is a string port. This fixture does not need a bytes variable type.

### D5. Replay stays RETRY_REQUEST

**Choice:** Keep `replayability: SAFE`, 503 → `RETRY_REQUEST`, no authentication flow. The second send is a new `RawHttpRequest` whose body and `token` equal the first.

**Why:** The token is still a pure function of path + body + SK. Freshness rebuild would change the published vector.

## Risks / Trade-offs

- [Appkey with `"` or `\` breaks the JSON wrapper] → Fixture is `test-ak` only. Escaping is out of scope; stop rather than encoding ad hoc.
- [BODY plaintext lands in DecisionTrace] → Protocol test asserts appkey and SK are absent from the trace and from exception messages. The secret is still on the wire body; that is the protocol.
- [Body pipeline and hash pipeline drift] → The request body concat has no secret and no literal JSON; it only rereads `execution.wireBody`. The test compares sent bytes to the hashed body portion.
- [DaHua HASH rule loosens] → `BODY` on a hasher feeder is an added denial scenario. Existing DaHua and PMS token tests stay green.
- [String binding splits a multibyte character] → Not reachable for this ASCII fixture. D4's stop condition covers a mismatch.

## Migration Plan

No host or stored-definition migration. Replace the canonical YAML and the PMS FakeTransport expectation in the same change. Rollback is reverting that change. Other protocol fixtures are not edited.

## Open Questions

None. JSON escaping and other PMS endpoints stay deferred, and neither changes these decisions.
