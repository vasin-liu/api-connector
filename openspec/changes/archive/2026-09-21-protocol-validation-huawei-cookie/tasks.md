## 1. Protocol table

- [x] 1.1 Write `docs/design/v2.7-protocols/huawei-ivs.md` with login path, JSON field names, `resultCode` `"0"`, `Set-Cookie` `JSESSIONID` with `Path=/`, https base URL, stub business GET `/device/deviceList/v1.0`, 25m ttl, and fixture `test-user` / `test-pass` / `test-jsid`, and verify the file cites `HuaweiIvsClient` / `HuaweiResult`, contains no production secrets, and has an assumption row for live Secure/Domain flags

## 2. Canonical definition

- [x] 2.1 Add Canonical YAML `docs/design/v2.7-protocols/huawei-ivs.yaml` citing the table: username-password credentials, `session.cookies: true`, login `cookies: acceptSetCookie`, business `cookies: fromStore` with no Authorization or `session.token`, login transitions requiring status 200 and `$.resultCode` equals `0`, and verify compile succeeds and two compiles produce the same `planId`

## 3. FakeTransport tests

- [x] 3.1 Add FakeTransport tests: first GET 401 → one login POST → replay GET with `Cookie` containing `JSESSIONID=test-jsid` and no `Authorization`; second execute with valid session skips login; HTTP 200 with `resultCode` other than `"0"` plus cooldown does not storm; cookie and password absent from trace, and verify Wenxin, Gaode, and IDPS protocol tests still pass
- [x] 3.2 Add an assertion that a login `Set-Cookie` without `Path=/` does not attach `JSESSIONID` onto `/device/deviceList/v1.0`, and verify the published table fixture with `Path=/` does attach

## 4. Gap discipline

- [x] 4.1 If cookie-only YAML cannot emit a store-built `Cookie` header (or requires a dummy `session.token`), stop and update this change's specs/design rather than adding AuthProvider, Catalog, hasher.md5, or copying Set-Cookie in Flow, and verify no new pipeline node types or `*ConnectorCatalog` types were introduced
