# Dahua TCPL RSA public-key login — protocol table

Canonical Definition: [`dahua-tcpl.yaml`](dahua-tcpl.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.dahua.tcpl.DahuaTcplClient.publicKey`, `login`, and `getSecurityPassword`. Not video-cloud `TrafficDaHuaUtils` (`encryptType=RSA` throws). Old Java Catalog/AuthProvider is gone.

**No production secrets.** Tests use `test-user` / `test-pass` / `test-session-id` / `test-token` and the fixture keypair below. Do not copy a production host, the `DahuaTcplExpectations` key, or a production secret.

## Endpoints

| Role | Method | URL | Notes |
|------|--------|-----|--------|
| Public key | `GET` | `https://dahua-tcpl.example/jg/AuthPlatform/accounts/publicKey` | Returns SPKI + session id |
| Login | `POST` | `https://dahua-tcpl.example/jg/AuthPlatform/accounts/login` | RSA-encrypted password |
| Business (stub) | `GET` | `https://dahua-tcpl.example/jg/tcpl/stub` | Smallest stub; keepalive `PUT` out of scope |

Auth profile (YAML): `dahua-tcpl-rsa-v1`. Session TTL: **18h**. `failureCooldown`: `5s`.

## Credentials (two `type: secret`)

| Credential id | Field | Fixture value | Ref |
|---------------|-------|---------------|-----|
| `tcplUser` | `value` | `test-user` | `secret/dahua-tcpl/username` |
| `tcplPass` | `value` | `test-pass` | `secret/dahua-tcpl/password` |

## Nested MD5 (UTF-8, lowercase hex)

| Step | Input | Digest |
|------|--------|--------|
| `s1` | `test-pass` | `380e5dc89564f30713ad54bf06aacea8` |
| `s2` | `test-user` + `s1` | `cb2cb0b9f11b040b2a169e768d266db8` |

Pipeline only: `canonicalizer.concat` (secrets with `sink: HASH`) → `hasher.md5`. Flow never hashes. RSA encrypts the **UTF-8 of the `s2` hex string**, not the raw digest bytes.

## Fixture RSA keypair (FakeTransport / decrypt only)

SPKI Base64 (public; FakeTransport `publicKey` response and login echo):

```text
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxHBkVG6VHkLn9MZyY+/2agpxbCtJpyCo9K1tVVNXLGDS1Wtx6UyR66nsCUyJiC4pYxu4JDWm4/LsccBhupazJbIA78NV2tO1201dfJaRBfYekyQHjDONEKku6ns4c8pN4qIdUU/GwKlffWA2T3L3znAvJWozMXTy+ojiTNTrgWZDLGPr2Tg/AH6NiGGjFpQIaF/iozoU09YqNDz1/LHnPgtJsFOYI3eswvB0J6CCudjWA5jTxQ1khSqvlO5ZRI5EUogkgmb5FedMT9EJQwu7L5c99jGo2yvPwVKNVYe1Q9YslRZEpLD4W4VQNICNPt3JojQ+wMqhpWKu1uCNHZTquQIDAQAB
```

PKCS#8 Base64 (test decrypt only; not a definition credential):

```text
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDEcGRUbpUeQuf0xnJj7/ZqCnFsK0mnIKj0rW1VU1csYNLVa3HpTJHrqewJTImILiljG7gkNabj8uxxwGG6lrMlsgDvw1Xa07XbTV18lpEF9h6TJAeMM40QqS7qezhzyk3ioh1RT8bAqV99YDZPcvfOcC8lajMxdPL6iOJM1OuBZkMsY+vZOD8Afo2IYaMWlAhoX+KjOhTT1io0PPX8sec+C0mwU5gjd6zC8HQnoIK52NYDmNPFDWSFKq+U7llEjkRSiCSCZvkV50xP0QlDC7svlz32MajbK8/BUo1Vh7VD1iyVFkSksPhbhVA0gI0+3cmiND7AyqGlYq7W4I0dlOq5AgMBAAECggEAQW+LDFEV3VG+QNyGKGI7/FDkPXsh+sk3XuIShJTRehf6w1saQ/Pcu9sLpIFZYwK1iK0oJu+0W0SFnNLP+z7OCGWGSFgSVmWls5rO5HynbIvy3WLoHLzjZcRlu3g2cuPWFAOaroZbl7wT8gjFrquGfr8CBX2d9gCWG/zTHXLjzGN135ldMrxboDGp3iG2njHBOdX0/OJGAk8aj5n2xHx5CtTr27lCnp1s+Pxhzmjr+2BE1G+2afaem++V2gMTAgnWleuterL1q/UOQkseqlN+lxU7byhHlollnFNv/M+LIJnEDtTV9MvzSQCw78z69fOoJawuxKyPGYqqD4rKxi3XPQKBgQDhIlm8xCdNN+aZLZwdthZRn/FG5Rn7KRKYIRDB2eTk/oUlmDDpnHdAtjhXL6HcpLvWsWTE16aKDUJQT0eiFhr32NKveCwXZ6Mfqk4yZgO1IPjEuUaBlNkQHSTn11wXt7jjy2PyQSxNDn9JhdiRbpYS635MdsevmEOewEzexQbJewKBgQDfXum6PSQB5qUiYh4C/d4Hs0CAhwgP82JuJgXLk+NIZ2TyctyQuF6UGjZQylLLCm1zN430ee5bguqjSki3VSNCKLMPcAiqIi92hN84gRgaAjztTT7BFLV1x5pz1BV0ArSGNzkJp2daIYfKqRlMkAwSZh9rrtTl/stdOOnj4KIkWwKBgDyDzzs+PpIzBHkIGX6jAzzNkT1NN76bO7RIrwhsWWp0P7XrQrqIpEVwtZQ56Xg1j4gWBtAiZ1GUXs7XgJnA917HmVoxIyjnYqGQVZSWd0iopDW814bdS7tZGiJ3cPmvGLOiaCsl+ZJ84xoQMDZnU0+inUBQbTx14Tc62W7H5xshAoGAGg2b/Vj8Rix8vlHrZjXuG7/Qmi0lx5Z/uJE+w8vwCpXktFKpX0fh4CxegXXXyk7hDTtBl25RWuMeMwWLDbckcacFm5tZp15tYsmEr7Z+9SmlqZgE5chgkehCH5hxGCLoo271fCbGa0DV8dFfXgq/HbZDm++8bUcWuTzWKvocb1kCgYEAkFxsYjhpIYpWX6uO9xK5Hu40Ra8mYd28OA2xjh2e4tRRmIAngnDkCTnb82tI+OQwbH1ze5mDbGvLu3DQ6JwBVDNToVphkFBtRFO1g239nZ1X0RNbSJ8ns1mK2JvAxrkCEDFunlxnTqV0FRJE05bPGwaH8DwGq2MvX3xg2IDA3+Q=
```

## Public-key hop

FakeTransport success: HTTP **200**

```json
{"publicKey":"<SPKI above>","subjectSessionId":"test-session-id"}
```

| Signal | Binding |
|--------|---------|
| status `200` + `$.publicKey` exists | CONTINUE |
| then EXTRACT `$.publicKey` → `execution.publicKey` | string (SPKI Base64) |
| then EXTRACT `$.subjectSessionId` → `execution.subjectSessionId` | string |

Missing `publicKey` → FAIL + AUTH_FAILED + cooldown. Do not call login.

## RSA password (PKCS1)

1. Plaintext = UTF-8 bytes of `s2` hex (`cb2cb0b9f11b040b2a169e768d266db8`).
2. Key bytes = UTF-8 of the SPKI Base64 string; node Base64-decodes to X.509 SPKI.
3. Encrypt with **RSA / ECB / PKCS1Padding** (`Cipher.getInstance("RSA")` on the JDK).
4. Emit **RFC 4648 Base64** with padding, no line wrap.

Pipeline: concat of `flow.s2` → `cipher.rsa-pkcs1` (`encoding: base64`, `.key` from concat of `execution.publicKey`, not a `secretRef`) → bind to `flow.passwordCipher`. Flow MUST NOT encrypt.

PKCS1 v1.5 padding is random. Do **not** freeze one ciphertext. Acceptance decrypts the login `password` with the PKCS#8 above; plaintext MUST equal the UTF-8 of `s2`.

## Login hop

| Header | Value |
|--------|-------|
| `Content-Type` | `application/json;charset=UTF-8` |
| `X-Subject-SessionId` | `execution.subjectSessionId` (`test-session-id`) |

JSON fields in **declaration order** (Fastjson `DahuaLoginVO`):

| JSON field | Source | Fixture |
|------------|--------|---------|
| `userName` | `tcplUser.value` | `test-user` |
| `password` | `flow.passwordCipher` | RSA Base64 of `s2` |
| `publicKey` | challenge extract | SPKI above |
| `subjectSessionId` | challenge extract | `test-session-id` |

FakeTransport success: HTTP **200**

```json
{"token":"test-token"}
```

| Signal | Binding |
|--------|---------|
| status `200` | CONTINUE |
| EXTRACT `$.token` as secret → `session.token` | + `onCommit` VALID / generation++ |

## Business request

Header `X-Subject-Token` binds `session.token` with explicit **`sink: HEADER`**. Replay is `SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`. Public-key GET and login POST are `UNSAFE`, `allowAutomaticReplay: false`, `maxAttempts: 1`.

## Session flow

1. Business GET without token → FakeTransport **401**.
2. Auth: one public-key GET → EXTRACT×2 → MD5×2 → RSA → one login POST → store token.
3. Replay business with `X-Subject-Token: test-token`.
4. Second execute with valid session: **zero** public-key or login calls.

## Out of scope

Video-cloud `encryptType=RSA` / `method=simple` / blank-method, keepalive `PUT`, OAEP and configurable padding, response decryption, copying `DahuaTcplExpectations` keys or tokens, Host / Catalog / Groovy, public internet, production secrets.

## FakeTransport script (acceptance)

| Execute | Outbound | Scripted response |
|---------|----------|-------------------|
| First, no session | GET stub (no token) | 401 |
| First, auth public key | GET `/jg/AuthPlatform/accounts/publicKey` | 200 + SPKI + `test-session-id` |
| First, auth login | POST login; decrypt `password` → `s2` | 200 + `token` |
| First, replay | GET stub with `X-Subject-Token: test-token` | 200 |
| Second, valid session | one business GET only | 200 |
| Public key 200 without `publicKey` | GET once | shared failure + cooldown; follow-up does not storm login |

`test-pass` MUST be absent from the outbound body, headers, query, DecisionTrace, and exception messages. `test-user` MUST be absent from DecisionTrace and exception messages and MUST appear as login JSON `userName`.
