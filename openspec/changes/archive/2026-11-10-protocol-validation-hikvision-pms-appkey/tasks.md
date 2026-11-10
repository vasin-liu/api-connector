# Tasks

## 1. Sink admission

- [x] 1.1 Extend `PipelineGraphValidator` so a `secretRef` on a concat in `request.body.pipeline` must declare sink `BODY`; `HASH`, `HMAC`, `AUTHORIZATION`, or an omitted sink fails validation. Also reject sink `BODY` on a concat that edges into `hasher.md5.in`, and reject a `secretRef` when that pipeline is both the request body and a hasher feeder. Verify with `PipelineGraphValidatorTest` cases for the allowed BODY body-concat, the rejected HASH body-concat, and the rejected BODY hasher-feeder, and that an existing DaHua-style HASH feeder still validates.
- [x] 1.2 Treat a concat as a wire-body producer when a flow step binds its output and a later body concat reads that variable; a `HASH` secret on that producer fails validation. Verify with a validator fixture shaped like design D2 (`wireBody` BODY secret bound to `execution.wireBody`, request body rereads the variable) that a `HASH` sink on `wireBody` fails and a `BODY` sink passes.
- [x] 1.3 Add `SecretSinkPolicyTest` coverage that sink `BODY` is allowed when the secret api id matches the destination and denied when it does not, with no secret plaintext in the exception message. Verify those two tests pass.

## 2. Body binding

- [x] 2.1 Bind the `wireBody` concat output as the UTF-8 string of its bytes onto an execution variable, and let a later concat `{ var: execution.wireBody }` reread those same bytes. Verify with a runtime test that prefix + BODY secret `test-ak` + suffix yields `{"appkey":"test-ak"}` and the reread bytes equal the first output. If the binder cannot store that output, or the reread bytes differ, stop and update this change's specs and design instead of adding a node or a JSON encoder.

## 3. Protocol table and definition

- [x] 3.1 Update `docs/design/v2.7-protocols/hikvision-pms.md` and `hikvision-pms.yaml`: credential `appkey` beside `secretKey`, both `apiId: hikvision-pms`; remove GLOBAL `bodyJson`; `wireBody` concat is `{"appkey":"` + `secretRef: appkey` sink `BODY` + `"}`; a flow step binds `execution.wireBody`; `tokenHash` parts are `uriPath`, `var: execution.wireBody`, `secretRef: secretKey` sink `HASH`; request body concat rereads only that variable; 503 stays `RETRY_REQUEST`. Verify the table still shows token `EDAA4B3136EA75DF9A435C95B920DD9E` and body `{"appkey":"test-ak"}`, and that the YAML has no `codec.json`, no vendor node, and no second `secretRef` for appkey.
- [x] 3.2 Leave the Artemis and Keytop protocol docs unchanged. Verify the diff does not touch `hikvision-artemis.md`, `hikvision-artemis.yaml`, `hikvision-artemis-post.md`, `hikvision-artemis-post.yaml`, `keytop.md`, or `keytop.yaml`.

## 4. PMS gate

- [x] 4.1 Update `TrafficHikvisionPmsProtocolTest` for the credential appkey: r1 body equals `{"appkey":"test-ak"}`, query `token` equals `EDAA4B3136EA75DF9A435C95B920DD9E` and equals uppercase hex MD5 of path + sent body + `test-sk` recomputed in the test; 503 sends a new `RawHttpRequest` with the same body and token; SK is absent from query, headers, body, and DecisionTrace; appkey is absent from DecisionTrace and exception messages; the definition contains no plain `{"appkey":"test-ak"}` literal. Verify the test passes on FakeTransport with no public HTTP.
- [x] 4.2 Verify the Wenxin, Gaode, IDPS, Huawei, DaHua, Artemis GET, Artemis POST, and Keytop protocol tests still pass.

## 5. Gap discipline

- [x] 5.1 If the sent body and the hashed body portion cannot stay the same bytes without a new node, a `from` part, or hashing in Flow, stop and update this change's specs and design. Verify the tree has no new pipeline node type, no vendor SDK dependency, and no production secret.
