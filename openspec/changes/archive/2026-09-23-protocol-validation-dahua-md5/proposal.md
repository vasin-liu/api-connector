## Why

Huawei cookie-only proved CookieStore without Bearer. The next §59 hole that needs **new Runtime surface** is 大华交警视频云 two-step authorize: hop-1 HTTP **401** carries challenge params (not failure / not Flow `CHALLENGE`), then nested MD5 over password **and** username material, then hop-2 returns `token` for `X-Subject-Token`. Prior review deferred this until `hasher.md5`, `HASH` sink, and auth-flow `CONTINUE` on 401 exist.

## What Changes

- Unlock pipeline: add built-in `hasher.md5` (**bytes in** → lowercase hex out); secrets reach the hasher only as concat output bytes after `sink: HASH` on `secretRef` parts (password and username).
- Unlock secrets: add `SecretSink.HASH`; fix **concat and sorted-query** paths that hardcode `HMAC` for every `secretRef` so declared sinks are honored (omitted sink still defaults to `HMAC` for Gaode/IDPS).
- Unlock auth Flow: authentication request steps MAY `CONTINUE` on HTTP 401 when matched; challenge fields are bound in **following EXTRACT steps** (not inline on the request). This is **not** business `CHALLENGE` / `AUTHENTICATE`.
- Protocol gate: table + Canonical YAML + FakeTransport for no-`method` nested MD5; freeze `clientType=web`, `expiredTime=86400`, `X-Api-Version=V1.0`, charset Content-Type; credentials are two `type: secret` (`dahuaUser` / `dahuaPass`); business sends `X-Subject-Token` with explicit `sink: HEADER`.
- **不做**：RSA；`method=simple` / blank-method；keepalive / unauthorize；Host / Catalog / AuthProvider / Groovy；真外网；hop-1 401→CHALLENGE；username 当明文 GLOBAL 绕过 HASH；本 gate 扩展 concat `field:`（用双 secret 代替）。

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `pipeline-graph`: built-in `hasher.md5` (bytes→hex); secret material for hashing enters via HASH-sunk concat (or equivalent), not Flow MD5.
- `secret-script`: `HASH` sink with destination apiId checks; password/username plaintext MUST NOT appear in DecisionTrace.
- `flow-runtime`: `CONTINUE` on HTTP 401 when matched ≠ `CHALLENGE` / business `AUTHENTICATE`.
- `session-auth`: multi-hop 401 challenge → extracts → token hop → VALID.
- `protocol-validation`: 大华 nested-MD5 authorize + `X-Subject-Token` FakeTransport gate.

## Impact

- Runtime: `SecretSink`, concat + sorted-query sink selection, `PipelineGraphValidator` / `PipelineExecutor`, FakeTransport protocol tests.
- Docs: `docs/design/v2.7-protocols/traffic-dahua.md` + `traffic-dahua.yaml`.
- Existing Gaode / IDPS / Huawei definitions must keep compiling and passing.
- Credentials: fixtures only; no production 大华 secrets in git.
