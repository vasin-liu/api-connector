## Context

See `proposal.md`. Runtime on `main` already has Wenxin (token-in-query), Gaode (sorted-query HMAC), and IDPS (envelope HMAC). Mock E YAML (`definitions/mock-e.yaml`) is cookie **plus** Bearer on `https://mock-e.example`. `CookieStore` uses `HttpCookie.parse` / `domainMatches`; Secure cookies require `https`; omitted Path defaults to the login URI directory (not `/`). Production formula is `system-thirdpart` `HuaweiIvsClient.auth` + `HuaweiResult.resultCode`. Cross-review rejected 大华 this round: hop-1 HTTP 401 is auth `CONTINUE`, not Flow `CHALLENGE`; nested MD5 needs `hasher.md5` + HASH sink.

## Goals / Non-Goals

**Goals:**

- Table-first Huawei login + stub business GET; FakeTransport 401 → AUTHENTICATE → REPLAY with store cookie.
- Cookie-only (no `session.token`). Login success = `$.resultCode == "0"`.
- Keep Wenxin / Gaode / IDPS green; no new pipeline node types.

**Non-Goals:**

- `hasher.md5`, HASH sink, DaHua authorize, Huawei logout/SSL-ignore/RTSP.
- Changing CookieStore RFC matching (fix the fixture Path=/ instead).
- Host constructor changes.

## Decisions

### D1. Source of truth is HuaweiIvsClient + HuaweiResult

**Choice:** Login `POST /loginInfo/login/v1.0` JSON `{userName, password}`; success `resultCode` string `"0"`; session material is `JSESSIONID` from `Set-Cookie`; business header is Cookie only (`GET /device/deviceList/v1.0` stub). Headers `Content-Type: application/json`.

**Why:** That is production `system-thirdpart`. Hutool SSL-ignore and `usersLogout` are transport/product, not CookieStore.

**Alternatives:** 公服 `x_auth_token` — inbound passthrough. 科达 `jwt-token` — Wenxin-shaped. 大华 two-step MD5 — rejected in review.

### D2. FakeTransport only

**Choice:** Scripted 401 on first business, 200 + `resultCode=0` + `Set-Cookie` on login, 200 on replay. No public IVS.

**Why:** Same as Wenxin/Gaode/IDPS. CI cannot hold IVS passwords.

### D3. HTTPS base URL + Set-Cookie Path=/

**Choice:** Freeze `https://ivs.example`. Login `Set-Cookie` MUST include `Path=/` (and MAY omit Secure, or include Secure because scheme is https). Tests MUST fail if Path is omitted and business path is `/device/…`.

**Why:** `CookieStore.matches` drops Secure cookies on `http`. Default path for `/loginInfo/login/v1.0` is `/loginInfo/login`, which does not prefix `/device/deviceList/v1.0`. Production servlet JSESSIONID is typically `Path=/`; the old client also bypasses path matching by attaching the `HttpCookie` object directly. We follow CookieStore RFC, not Hutool attach-any-cookie.

**Alternatives:** Relax default path to `/` — changes Mock E semantics. Put business under `/loginInfo/` — not the vendor path.

### D4. Cookie-only YAML (no session.token)

**Choice:** `session.cookies: true`. Login `cookies: acceptSetCookie`. Business `cookies: fromStore`. No Authorization binding. Username/password go in login JSON via credential refs with BODY sink. Auth flow: login → extract/commit cookies → `sessionStatus: VALID` + generation increment (same shape as Mock E storeToken, without jsonpath token).

**Why:** Mock E cannot prove cookie-only. Huawei has no Bearer.

**Alternatives:** Keep a dummy session.token — false §59 proof.

### D5. resultCode over HTTP status

**Choice:** Login transitions require `status: 200` AND jsonpath `$.resultCode` equals `0`. HTTP 200 with other resultCode is AUTH_FAILED (no cookie store write). Business 401 → AUTHENTICATE + REPLAY_REQUEST (SESSION_MISSING / expired), matching Wenxin.

**Why:** `HuaweiResult.isSuccess()` is `resultCode.equals("0")`, not HTTP 200.

**Alternatives:** Status-only 200 — would accept failed IVS bodies.

### D6. TTL 25 minutes, no keepalive/logout

**Choice:** `session.ttl: 25m` (client hardcoded). Expiry via FakeTransport 401 / ttl, not `PUT keepalive` or `/users/logout`.

**Why:** Keepalive was correctly out of scope in review.

### D7. Secrets and fixtures

**Choice:** `test-user` / `test-pass` via username-password credential. Cookie value fixture `test-jsid`. Trace MUST redact password and `JSESSIONID` value.

### D8. Gap discipline

**Choice:** If business `fromStore` emits no Cookie without an Authorization binding, stop and update session-auth / renderer — do not invent a `session.token` from the cookie. If Path=/ still does not attach, stop and spec CookieStore — do not copy `Set-Cookie` in Flow.

## Risks / Trade-offs

- [Default Path hides a green test] → Table and FakeTransport MUST set `Path=/`; add a negative unit or protocol assertion that omitted Path does not attach on `/device/…`.
- [Secure cookie dropped] → https base URL; document if fixture omits Secure.
- [resultCode type] → Freeze string `"0"` as in `HuaweiResult.IVS_SUCCEED`.
- [Live IVS Set-Cookie flags unverified] → Assumption row in the protocol table; do not block on a packet capture.

## Migration Plan

1. Protocol table (`huawei-ivs.md`).
2. Canonical YAML + FakeTransport tests (RED then GREEN).
3. Renderer/CookieStore fix only if cookie-only fails compile or attach.
4. No production deploy; Host remains later.

## Open Questions

None that change this change's specs. Live JSESSIONID `Secure`/`Domain` flags stay assumption rows.
