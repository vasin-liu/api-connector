# Huawei IVS1800 cookie session — protocol table

Canonical Definition: [`huawei-ivs.yaml`](huawei-ivs.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.huawei.HuaweiIvsClient` + `HuaweiResult` (`resultCode` string `"0"` = `IVS_SUCCEED`). Not 公服 inbound `x_auth_token` passthrough, not 科达 `jwt-token`, not 大华 two-step MD5. Old Java Catalog/AuthProvider is gone.

**No production secrets.** Tests use `test-user` / `test-pass` / `test-jsid` only. Do not check in IVS accounts.

## Endpoints

| Role | Method | URL | Notes |
|------|--------|-----|--------|
| Login | `POST` | `https://ivs.example/loginInfo/login/v1.0` | FakeTransport host; live IVS host is unverified |
| Business (stub) | `GET` | `https://ivs.example/device/deviceList/v1.0` | Smallest device-list path; RTSP/logout out of scope |

Auth profile (YAML): `huawei-ivs-cookie-v1`. Session TTL: **25m** (client hardcoded). `failureCooldown`: `5s`.

## Login request

| JSON field | Source | Fixture (tests only) |
|------------|--------|----------------------|
| `userName` | username-password credential `account.username` | `test-user` |
| `password` | `account.password` | `test-pass` |

Header: `Content-Type: application/json`. Body sink only — password MUST NOT appear as query or Authorization.

## Login success (FakeTransport shape)

HTTP **200** plus:

```json
{"resultCode":"0","data":{"sessionToken":"do-not-use-this"}}
```

| Signal | Binding |
|--------|---------|
| `$.resultCode` equals `"0"` (string, not number `0`) | login CONTINUE |
| `Set-Cookie: JSESSIONID=test-jsid; Path=/` | CookieStore (`acceptSetCookie`) |
| `data.sessionToken` | **Ignored** — not `session.token`, not Authorization |

`HuaweiResult.isSuccess()` is `resultCode.equals("0")`, not HTTP 200 alone. HTTP 200 with another `resultCode` is AUTH_FAILED and the fixture sends **no** `Set-Cookie`.

## Business request

No query token. No `Authorization`. Cookie header is **only** `CookieStore.cookiesFor(uri)` (`cookies: fromStore`).

Published fixture Cookie: `JSESSIONID=test-jsid` after Path=/ matching.

## Session flow (cookie-only Mock E minus Bearer)

1. First business GET has no Cookie.
2. FakeTransport returns **HTTP 401**.
3. Authentication POSTs login **once**, generation increments, session VALID **without** a companion token.
4. Replay rebuilds the business template; `Cookie` contains `JSESSIONID=test-jsid` and MUST NOT include `Authorization`.
5. A second execute with a still-valid session skips login.

## Assumptions (client vs live IVS)

| Topic | Frozen here | Unverified vs live IVS |
|-------|-------------|------------------------|
| Base URL | `https://ivs.example` | Production host / SSL-ignore |
| `Set-Cookie` Path | **`Path=/` required** so `/device/…` matches | Servlet JSESSIONID Path/Domain |
| `Set-Cookie` Secure / Domain | Omitted in fixture (https scheme is enough) | Live flags unknown — not a test gate |
| Failed login | HTTP 200 + `resultCode` other than `"0"`, no Set-Cookie | Live error codes in IVS 5.15 |
| Business 401 | Status-only (no body contract) | Live device-list error JSON |
| Default Path if Path omitted | CookieStore uses login URI directory (`/loginInfo/login`) — **does not** attach onto `/device/…` | Old client attached HttpCookie ignoring Path |

## FakeTransport script (acceptance)

| Execute | Outbound | Scripted response |
|---------|----------|-------------------|
| First, no session | `GET .../device/deviceList/v1.0` (no Cookie) | 401 |
| First, auth | `POST .../loginInfo/login/v1.0` body `userName`/`password` | 200 + `resultCode":"0"` + `Set-Cookie: JSESSIONID=test-jsid; Path=/` |
| First, replay | `GET .../deviceList/v1.0` with `Cookie: JSESSIONID=test-jsid`, no Authorization | 200 |
| Second, valid session | one business GET only | 200 |
| Login always `resultCode` ≠ `"0"` | login POST 200 `{"resultCode":"1"}` (no Set-Cookie) | shared failure + cooldown; follow-up does not storm |

`test-user`, `test-pass`, and `test-jsid` MUST be absent from DecisionTrace and exception messages.
