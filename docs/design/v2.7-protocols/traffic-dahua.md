# 大华交警视频云 nested-MD5 authorize — protocol table

Canonical Definition: [`traffic-dahua.yaml`](traffic-dahua.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.trafficDaHua.TrafficDaHuaClient.login` + `TrafficDaHuaUtils.calculateSign` (**no-`method`-key** branch only). Not Huawei cookie, not Wenxin OAuth, not Gaode HMAC.

**No production secrets.** Tests use `test-user` / `test-pass` / `test-token` / `test-realm` / `test-random-key` only.

## Endpoints

| Role | Method | URL | Notes |
|------|--------|-----|--------|
| Authorize (challenge + token) | `POST` | `https://dahua.example/videoService/accounts/authorize` | Same path for hop-1 and hop-2 |
| Business (stub) | `GET` | `https://dahua.example/videoService/devices/stub` | Smallest stub; keepalive / unauthorize out of scope |

Auth profile (YAML): `traffic-dahua-md5-v1`. Session TTL: **18h** (`86400 * 0.75` legacy keepalive hint, frozen). `failureCooldown`: `5s`.

## Credentials (two `type: secret`)

| Credential id | Field | Fixture value | Ref |
|---------------|-------|---------------|-----|
| `dahuaUser` | `value` | `test-user` | `secret/traffic-dahua/username` |
| `dahuaPass` | `value` | `test-pass` | `secret/traffic-dahua/password` |

## Login headers (both authorize hops)

| Header | Value |
|--------|-------|
| `Content-Type` | `application/json;charset=UTF-8` |
| `X-Api-Version` | `V1.0` |

## Hop-1 challenge request

| JSON field | Source | Fixture |
|------------|--------|---------|
| `userName` | `dahuaUser.value` | `test-user` |
| `clientType` | frozen constant | `web` |

FakeTransport success shape: HTTP **401** (challenge, not failure) plus JSON **without** a `method` key:

```json
{"realm":"test-realm","randomKey":"test-random-key","encryptType":"MD5"}
```

| Signal | Binding |
|--------|---------|
| status `401` + `$.randomKey` exists + `$.realm` exists + `$.encryptType` equals `MD5` | CONTINUE (not CHALLENGE) |
| then EXTRACT `$.realm` → `flow.realm` | string |
| then EXTRACT `$.randomKey` → `execution.randomKey` | string (also hop-2 body) |
| then EXTRACT `$.encryptType` → `execution.encryptType` | string |

`realm` MUST be non-blank. Missing `randomKey` → FAIL + AUTH_FAILED + cooldown.

## Nested MD5 (no-`method` branch)

From `TrafficDaHuaUtils.calculateSign` when `containsMethod == false`:

```
p1   = md5(password)
p2   = md5(userName + p1)
pTmp = md5(p2)
enc  = md5(userName + ":" + realm + ":" + pTmp)
sign = md5(enc + ":" + randomKey)   // lowercase hex
```

Pipeline only: each nest step is `canonicalizer.concat` (secrets with `sink: HASH`) → `hasher.md5` (bytes → lowercase hex). Flow never hashes.

### Published fixture signature

| Input | Value |
|-------|-------|
| userName | `test-user` |
| password | `test-pass` |
| realm | `test-realm` |
| randomKey | `test-random-key` |
| encryptType | `MD5` |

| Step | Digest |
|------|--------|
| `p1` | `380e5dc89564f30713ad54bf06aacea8` |
| `p2` | `cb2cb0b9f11b040b2a169e768d266db8` |
| `pTmp` | `e642b41ce855e724dea9f8d99160b105` |
| `enc` | `77aa4a34841bcc3acd476b6d10bfcb28` |
| **`signature`** | **`c11f8ffa3cd99605a9f23331c512b8b2`** |

## Hop-2 token request

| JSON field | Source | Fixture / freeze |
|------------|--------|------------------|
| `userName` | `dahuaUser.value` | `test-user` |
| `signature` | pipeline nest | `c11f8ffa3cd99605a9f23331c512b8b2` |
| `randomKey` | challenge extract | `test-random-key` |
| `clientType` | frozen | `web` |
| `encryptType` | challenge extract | `MD5` |
| `expiredTime` | frozen | `86400` |

FakeTransport success: HTTP **200**

```json
{"token":"test-token","duration":86400}
```

| Signal | Binding |
|--------|---------|
| status `200` | CONTINUE |
| EXTRACT `$.token` as secret → `session.token` | + `onCommit` VALID / generation++ |

## Business request

Header `X-Subject-Token` binds `session.token` with explicit **`sink: HEADER`** (must not default to Authorization / Bearer).

## Session flow

1. Business GET without token → FakeTransport **401**.
2. Auth: one challenge authorize (401 body) → EXTRACT×3 → five MD5 pipelines → one token authorize (200) → store token.
3. Replay business with `X-Subject-Token: test-token`.
4. Second execute with valid session: **zero** authorize calls.

## FakeTransport script (acceptance)

| Execute | Outbound | Scripted response |
|---------|----------|-------------------|
| First, no session | GET stub (no token) | 401 |
| First, auth hop-1 | POST authorize `{userName,clientType}` | 401 + realm/randomKey/encryptType=MD5 (no method) |
| First, auth hop-2 | POST authorize with `signature=c11f8ffa…` | 200 + `token`/`duration` |
| First, replay | GET stub with `X-Subject-Token: test-token` | 200 |
| Second, valid session | one business GET only | 200 |
| Challenge always 401 without `randomKey` | authorize POST once | shared failure + cooldown; follow-up does not storm |

`test-user`, `test-pass`, and `test-token` MUST be absent from DecisionTrace and exception messages. RSA / `method=simple` / blank-method / keepalive / unauthorize are out of scope.
