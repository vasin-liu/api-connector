## 1. Protocol tables

- [x] 1.1 Write `docs/design/v2.7-protocols/wenxin.md` with token URL, grant type, JSON path for access_token, business URL, `access_token` query name, and FakeTransport fixture shapes, and verify the file contains no real secrets
- [x] 1.2 Write `docs/design/v2.7-protocols/gaode-traffic.md` with base URL, query param list, sort order, HMAC algorithm, signature query/header name, timestamp field, and a known test vector, and verify `sig` inclusion/exclusion is explicit

## 2. Sorted-query canonicalizer

- [x] 2.1 Implement the built-in `canonicalizer.sorted-query` node (or the name recorded in `gaode-traffic.md`) with typed ports and sink rules, and verify `PipelineGraphValidator` still rejects unknown types
- [x] 2.2 Add a unit test that HMAC-SHA256 of the table’s canonical string matches the published test vector, and verify the test fails if key order is shuffled

## 3. Wenxin definition and tests

- [x] 3.1 Add Canonical YAML under `docs/design/v2.7-protocols/` for Wenxin (token flow + business `access_token` query) citing `wenxin.md`, and verify two compiles of that YAML produce the same `planId`
- [x] 3.2 Add FakeTransport tests: 401 → one token call → replay with query token; second execute with valid session skips token; failing token + cooldown does not storm, and verify secret material is absent from trace

## 4. Gaode definition and tests

- [x] 4.1 Add Canonical YAML for Gaode traffic using sorted-query canonicalizer + `signer.hmac-sha256`, citing `gaode-traffic.md`, and verify compile succeeds and a concat-only graph is not used for the digest
- [x] 4.2 Add FakeTransport tests that two outbound Gaode calls differ in freshness field and HMAC and that Flow does not sort the query map, and verify bytes of request one are not cloned onto request two

## 5. Gap discipline

- [x] 5.1 If YAML cannot express a table rule, stop and update this change’s specs/design rather than adding AuthProvider or Catalog code, and verify no new `AuthProvider` / `*ConnectorCatalog` types were introduced
