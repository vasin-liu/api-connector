## Why

IDPS header AK/SK is on `main`, so query HMAC, envelope HMAC, and OAuth query-token are proven. CookieStore has only Mock E (cookie **plus** Bearer). Inventory's 公服 is inbound cookie passthrough, not outbound login. Huawei IVS (`HuaweiIvsClient`) is the remaining §59 hole that Canonical YAML can express without new pipeline nodes: login JSON → `Set-Cookie: JSESSIONID` → business Cookie from the store.

## What Changes

- Add protocol table + Canonical YAML for Huawei IVS1800 login (`POST /loginInfo/login/v1.0`) and a stub business GET, sourced from `system-thirdpart` `HuaweiIvsClient` + `HuaweiResult` — **no production secrets**.
- FakeTransport tests: first business 401 → one login → replay with `Cookie: JSESSIONID=…` from CookieStore; valid session skips login; failed `resultCode` does not storm; cookie values redacted.
- Login success is JSONPath `$.resultCode` equals `"0"`, not HTTP status alone. Business request MUST NOT send Authorization / session token — Cookie only.
- Freeze `https://` base URL and `Set-Cookie` with `Path=/` so CookieStore matching actually attaches the cookie on `/device/…`.
- **不做**：Host / 管理台 / Java Catalog / AuthProvider；大华两步 MD5（交叉评审否决：401≠CHALLENGE，且需要 `hasher.md5` + HASH sink）；华为 logout / SSL ignore / RTSP；科达 password OAuth（与文心过近）；真实外网。若 YAML 无法在无 Bearer 的情况下从 CookieStore 出站，停下来改 spec。

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `protocol-validation`: Huawei IVS Cookie session Canonical Definition + FakeTransport 验收（login JSON、`resultCode=0`、`JSESSIONID` 只从 CookieStore 出、无 Bearer）。
- `session-auth`: Cookie-only session is valid — authentication MAY populate CookieStore without a companion `session.token` / Authorization header.

## Impact

- 测试与文档：`docs/design/v2.7-protocols/huawei-ivs.md` + `huawei-ivs.yaml`；`api-connector-runtime` FakeTransport 协议测。
- Runtime：预期无新 pipeline 节点。若 cookie-only 请求渲染失败，修 `RequestRenderer` / CookieStore 匹配，不改 `ApiClient` 宿主契约。
- 凭证：username-password SecretRef；夹具 `test-user` / `test-pass`。禁止提交 IVS 生产账号。
- 后续：大华 challenge MD5 留给 hasher/HASH/CONTINUE-on-401 齐套之后的独立 change。
