# Design

## Context

See `proposal.md` for why. Truth source is `DahuaTcplClient.publicKey`, `login`, and `getSecurityPassword` (read-only; no dependency). `publicKey()` GETs `/jg/AuthPlatform/accounts/publicKey` and reads `publicKey` plus `subjectSessionId`. `login` POSTs `/jg/AuthPlatform/accounts/login` with header `X-Subject-SessionId` and a Fastjson body of `DahuaLoginVO` (`userName`, `password`, `publicKey`, `subjectSessionId`). `getSecurityPassword` does `md5(appSecret.getBytes())`, then `md5((appId + s1).getBytes())`, then `Cipher.getInstance("RSA")` over an `X509EncodedKeySpec` of Commons `Base64.decodeBase64(publicKey)`, and emits Commons `Base64.encodeBase64String`. `Cipher.getInstance("RSA")` on the JDK is `RSA/ECB/PKCS1Padding`. PKCS1 v1.5 padding is random. The video-cloud client `TrafficDaHuaUtils` throws on any `encryptType` other than `MD5`.

Catalog today: `hasher.md5` (bytes in, lowercase hex out), `canonicalizer.concat` (parts `var` / `value` / `secretRef`), `cipher.sm4-ecb` whose `.key` is type `secret` and sink `ENCRYPT`. `codec.json` writes every set EXECUTION variable, in plan declaration order. There is no Base64-decode node. `SecretSink` already has `HASH` and `ENCRYPT`.

## Goals / Non-Goals

**Goals:**

- One FakeTransport handshake whose login `password` decrypts to the published `s2`.
- Add `cipher.rsa-pkcs1` only. Leave `HASH`, `BODY`, and `ENCRYPT` admission rules unchanged.
- The server public key stays a string variable. The password secret stays off the wire, the trace, and exception text.

**Non-Goals:**

- Video-cloud `encryptType=RSA`, `method=simple`, and blank-method.
- Keepalive `PUT`, OAEP, a padding or mode config, and response decryption.
- Copying the key or token embedded in `DahuaTcplExpectations`.
- `Security.addProvider`. Host / Catalog / Groovy. Public internet. Production secrets.

## Decisions

### D1. Fixture identity

**Choice:** Definition id `dahua-tcpl`, `authProfile` `dahua-tcpl-rsa-v1`, base `https://dahua-tcpl.example`. Two `type: secret` credentials: `tcplUser` / `test-user` at `secret/dahua-tcpl/username`, `tcplPass` / `test-pass` at `secret/dahua-tcpl/password`. `subjectSessionId` is `test-session-id`. Login token is `test-token`. Business stub is `GET /jg/tcpl/stub`. `Content-Type` on the login POST is `application/json;charset=UTF-8`.

Digests, UTF-8, lowercase hex:

| Step | Input | Digest |
|------|--------|--------|
| `s1` | `test-pass` | `380e5dc89564f30713ad54bf06aacea8` |
| `s2` | `test-user` + `s1` | `cb2cb0b9f11b040b2a169e768d266db8` |

These match the traffic-dahua `p1` / `p2` rows because the ASCII inputs match. RSA encrypts the UTF-8 of the `s2` hex string, not the raw digest.

SPKI Base64 (public, FakeTransport response and login echo):

`MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxHBkVG6VHkLn9MZyY+/2agpxbCtJpyCo9K1tVVNXLGDS1Wtx6UyR66nsCUyJiC4pYxu4JDWm4/LsccBhupazJbIA78NV2tO1201dfJaRBfYekyQHjDONEKku6ns4c8pN4qIdUU/GwKlffWA2T3L3znAvJWozMXTy+ojiTNTrgWZDLGPr2Tg/AH6NiGGjFpQIaF/iozoU09YqNDz1/LHnPgtJsFOYI3eswvB0J6CCudjWA5jTxQ1khSqvlO5ZRI5EUogkgmb5FedMT9EJQwu7L5c99jGo2yvPwVKNVYe1Q9YslRZEpLD4W4VQNICNPt3JojQ+wMqhpWKu1uCNHZTquQIDAQAB`

PKCS#8 Base64 (test decrypt only; not a definition credential):

`MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDEcGRUbpUeQuf0xnJj7/ZqCnFsK0mnIKj0rW1VU1csYNLVa3HpTJHrqewJTImILiljG7gkNabj8uxxwGG6lrMlsgDvw1Xa07XbTV18lpEF9h6TJAeMM40QqS7qezhzyk3ioh1RT8bAqV99YDZPcvfOcC8lajMxdPL6iOJM1OuBZkMsY+vZOD8Afo2IYaMWlAhoX+KjOhTT1io0PPX8sec+C0mwU5gjd6zC8HQnoIK52NYDmNPFDWSFKq+U7llEjkRSiCSCZvkV50xP0QlDC7svlz32MajbK8/BUo1Vh7VD1iyVFkSksPhbhVA0gI0+3cmiND7AyqGlYq7W4I0dlOq5AgMBAAECggEAQW+LDFEV3VG+QNyGKGI7/FDkPXsh+sk3XuIShJTRehf6w1saQ/Pcu9sLpIFZYwK1iK0oJu+0W0SFnNLP+z7OCGWGSFgSVmWls5rO5HynbIvy3WLoHLzjZcRlu3g2cuPWFAOaroZbl7wT8gjFrquGfr8CBX2d9gCWG/zTHXLjzGN135ldMrxboDGp3iG2njHBOdX0/OJGAk8aj5n2xHx5CtTr27lCnp1s+Pxhzmjr+2BE1G+2afaem++V2gMTAgnWleuterL1q/UOQkseqlN+lxU7byhHlollnFNv/M+LIJnEDtTV9MvzSQCw78z69fOoJawuxKyPGYqqD4rKxi3XPQKBgQDhIlm8xCdNN+aZLZwdthZRn/FG5Rn7KRKYIRDB2eTk/oUlmDDpnHdAtjhXL6HcpLvWsWTE16aKDUJQT0eiFhr32NKveCwXZ6Mfqk4yZgO1IPjEuUaBlNkQHSTn11wXt7jjy2PyQSxNDn9JhdiRbpYS635MdsevmEOewEzexQbJewKBgQDfXum6PSQB5qUiYh4C/d4Hs0CAhwgP82JuJgXLk+NIZ2TyctyQuF6UGjZQylLLCm1zN430ee5bguqjSki3VSNCKLMPcAiqIi92hN84gRgaAjztTT7BFLV1x5pz1BV0ArSGNzkJp2daIYfKqRlMkAwSZh9rrtTl/stdOOnj4KIkWwKBgDyDzzs+PpIzBHkIGX6jAzzNkT1NN76bO7RIrwhsWWp0P7XrQrqIpEVwtZQ56Xg1j4gWBtAiZ1GUXs7XgJnA917HmVoxIyjnYqGQVZSWd0iopDW814bdS7tZGiJ3cPmvGLOiaCsl+ZJ84xoQMDZnU0+inUBQbTx14Tc62W7H5xshAoGAGg2b/Vj8Rix8vlHrZjXuG7/Qmi0lx5Z/uJE+w8vwCpXktFKpX0fh4CxegXXXyk7hDTtBl25RWuMeMwWLDbckcacFm5tZp15tYsmEr7Z+9SmlqZgE5chgkehCH5hxGCLoo271fCbGa0DV8dFfXgq/HbZDm++8bUcWuTzWKvocb1kCgYEAkFxsYjhpIYpWX6uO9xK5Hu40Ra8mYd28OA2xjh2e4tRRmIAngnDkCTnb82tI+OQwbH1ze5mDbGvLu3DQ6JwBVDNToVphkFBtRFO1g239nZ1X0RNbSJ8ns1mK2JvAxrkCEDFunlxnTqV0FRJE05bPGwaH8DwGq2MvX3xg2IDA3+Q=`

**Why:** The keypair was generated for this gate. ASCII credentials make UTF-8 and the legacy no-charset `getBytes()` produce the same digest.

**Alternatives:** Reuse the public key in `DahuaTcplExpectations` — it is an unsourced capture. Freeze one ciphertext — PKCS1 will not repeat it.

### D2. Decrypt the password field

**Choice:** The protocol test Base64-decodes `password` and decrypts with the D1 private key. The plaintext must be the UTF-8 of `s2`. Two runs are not required to emit the same Base64. The private key is not a YAML credential and does not enter DecisionTrace.

**Why:** `Cipher.getInstance("RSA")` draws random PKCS1 padding. The old mock comments out the login body match for the same reason.

**Alternatives:** `RSA/ECB/NoPadding` with a fixed block — compiles, and is a different algorithm. Seed the padding — not what the client does.

### D3. `cipher.rsa-pkcs1` beside the hashers

**Choice:** Node type `cipher.rsa-pkcs1`. Ports: `.in` bytes, `.key` bytes, `.out` string. `.key` is the UTF-8 of the SPKI Base64; the node Base64-decodes it to an X.509 SPKI and encrypts with `RSA/ECB/PKCS1Padding`. Omitted encoding or `base64` emits RFC 4648 Base64 with no line wrap. Any other encoding fails validation. Invalid SPKI fails before HTTP, and the message does not contain the key text. Implementation uses JDK `Cipher`. No `Security.addProvider`. Flow does not encrypt.

A `secretRef` edge into `.key` or `.in` fails compile. Do not add a sink. Do not change the `cipher.sm4-ecb` rule that `.key` requires `ENCRYPT`.

**Why:** The public key arrives from the previous response. `ENCRYPT` means "our secret is the cipher key." Naming the padding in the type avoids an OAEP switch. JDK RSA does not need BouncyCastle.

**Alternatives:** `cipher.rsa` plus a padding field — invites OAEP with no witness. `cipher.dahua-rsa` — a vendor node. Reuse `cipher.sm4-ecb` — wrong algorithm and a secret key port. A new Base64-decode node — one protocol does not need it; this node decodes its own key text.

### D4. Digests stay on `hasher.md5`

**Choice:** `s1` is concat of `tcplPass` with sink `HASH`, then `hasher.md5`, bound to `flow.s1`. `s2` is concat of `tcplUser` with sink `HASH` plus `var: flow.s1`, then `hasher.md5`, bound to `flow.s2`. Both are FLOW, not EXECUTION.

**Why:** The catalog already hashes bytes to lowercase hex. FLOW keeps the intermediate hex out of the login JSON.

**Alternatives:** Assign the password secret to a variable and let concat stringify it — the traffic-dahua review already rejected that. A second MD5 node — no new digest.

### D5. Login JSON is the EXECUTION map

**Choice:** Declare EXECUTION variables in this order only: `userName`, `password`, `publicKey`, `subjectSessionId`. Extract `$.publicKey` and `$.subjectSessionId` into the last two. After the cipher step, assign `execution.userName` from `tcplUser.value` and `execution.password` from `flow.passwordCipher`. The login request uses `codec.json`. Header `X-Subject-SessionId` is `{execution.subjectSessionId}`.

`execution.password` is the ciphertext string. The password secret is never assigned to it.

**Why:** `renderBody` emits every set EXECUTION variable in declaration order. That order is the Fastjson field order of `DahuaLoginVO`. No other EXECUTION variable may be set before the POST, or it becomes an extra JSON field.

**Alternatives:** A hand-built concat of the JSON envelope — the ciphertext can contain `+`, `/`, and `=`, and `codec.json` already leaves those raw inside a string. A body template language — not in the catalog.

### D6. Auth shape matches the traffic-dahua session, not a new trigger

**Choice:**

```text
business GET /jg/tcpl/stub
  401 → AUTHENTICATE, then REPLAY_REQUEST
  200 → SUCCESS
authentication:
  GET publicKey
    200 and $.publicKey exists → CONTINUE
    else FAIL + AUTH_FAILED
  EXTRACT publicKey, subjectSessionId
  pipeline s1, s2, cipher.rsa-pkcs1
  assign userName and password
  POST login
    200 → CONTINUE
    else FAIL + AUTH_FAILED
  EXTRACT $.token as secret → session.token
    onCommit VALID, generation increment
```

Business replay is `SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`, header `X-Subject-Token` with sink `HEADER`. Login and the public-key GET are `UNSAFE`, `allowAutomaticReplay: false`, `maxAttempts: 1`. A 200 without `publicKey` stores no token and shares cooldown.

**Why:** The legacy invoke service calls `publicKey()` then `login()` itself. This runtime's authentication is the flow started by the business 401, which is the same shape as traffic-dahua. The stub path is not a real TCPL route; keepalive stays out.

**Alternatives:** Model only the two auth calls with no business replay — drops the session requirement already in the spec. Treat missing `publicKey` as a transport retry — the client throws `DahuaTcplAuthException` and does not sign.

### D7. Stop rather than invent a vendor node

**Choice:** If concat, `hasher.md5`, and `cipher.rsa-pkcs1` cannot produce a `password` that decrypts to `s2`, stop and update this change's spec and design. Do not encrypt in Flow, do not add `cipher.dahua-rsa`, and do not copy a production host or the mock's key into git.

## Risks / Trade-offs

- [PKCS1 ciphertext changes every run] → Assert decryption to `s2`. Do not paste a sample Base64 into the test as the expected body.
- [An extra EXECUTION variable becomes a JSON field] → D5 lists the only four. The test asserts field order and the key set.
- [`Map.copyOf` reorders LinkedHashMap keys on JDK 21] → `PlanAssembler.compileVariables` and `FlowRuntime.renderBody` use `Collections.unmodifiableMap` so `codec.json` keeps YAML declaration order.
- [`.key` typed `secret` would demand `ENCRYPT`] → D3 types `.key` as bytes. A `secretRef` edge fails compile.
- [Legacy `getBytes()` uses the platform charset] → Fixtures are ASCII, and the table freezes UTF-8. A non-ASCII password is a different gate.
- [Username is a secret that the login body must show] → Same assign path as traffic-dahua. Trace and exceptions still omit `test-user` and `test-pass`.
