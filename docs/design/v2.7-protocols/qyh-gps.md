# Qyh GPS SM4 decrypt + SM3 sign — protocol table

Canonical Definition: [`qyh-gps.yaml`](qyh-gps.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.qyh.QyhGameVehicleGpsClient.login`, `buildHeader`, and `getVehicleGps`. Old Java Catalog/AuthProvider is gone. SM4 defaults match `antherd` `sm-crypto` 0.3.2 (ECB, `pkcs#5`, hex key and hex ciphertext). Do not add that dependency.

**No production secrets.** Tests use the synthetic hex key `0123456789abcdeffedcba9876543210` (`sm-crypto` sample). Do not copy a production host, gateway `app_id` / `api_key`, or key from `QyhGameVehicleGpsExtendParam`.

## Endpoints

| Field | getToken | queryGps |
|-------|----------|----------|
| Base URL | `https://qyh-gps.example` (FakeTransport only) | same |
| Method | `POST` | `POST` |
| Path | `/zhjt/sportservice/basic-api/gzqyh/open/api/getToken` | `/zhjt/sportservice/basic-api/gzqyh/open/api/queryGps` |
| Query | `app_id=test-token-app` & `api_key=test-token-key` | `app_id=test-gps-app` & `api_key=test-gps-key` |
| Auth profile (YAML) | `qyh-gps-sm4-v1` | same |
| `Content-Type` | `application/json;charset=UTF-8` | same |

## Identities (frozen)

| Item | Value |
|------|--------|
| Username | `test-user` |
| `sysId` | `test-sys` |
| Agreed key (32 hex chars) | `0123456789abcdeffedcba9876543210` |
| Token | `test-token` |
| Ciphertext `result.str` | `8cd2182b020f1d459a719234394e2f06` |
| Decrypted `symKey` | `symkey-fixture` |
| Body | `{"plateNo":"A12345"}` |

## Clock order

The business flow stamps `execution.businessT` before the first queryGps. Login stamps `execution.loginT` only after the 401, inside authentication. One `ScriptedClock` therefore yields the business epoch first.

| Tick | Value | Use |
|------|--------|-----|
| 1 | `1700000000000` | first `X-Time` (probe + replay) |
| 2 | `1700000001000` | login body `t` |
| 3 | `1700000002000` | second execute `X-Time` |

## Login

1. `sign` = lowercase SM3 of UTF-8 `test-user` + `t` + agreed key (empty separator). The key string is hashed as hex text, not as decoded bytes.
2. Body field order is frozen: `sign`, `t`, `sysId`.
3. Pipeline: `canonicalizer.concat` (username, epoch, agreed key with sink `HASH`) → `hasher.sm3` (`encoding: hex`). Flow MUST NOT hash.
4. Login body is a concat of `{"sign":"`, digest, `","t":`, epoch, `,"sysId":"test-sys"}`, not `codec.json`.

### Known login vector

```text
{"sign":"1a625df0ea71ff8731a498b38638987d0ad71e0bcfe1cb79e987de599a29c86f","t":1700000001000,"sysId":"test-sys"}
```

Computed with BouncyCastle 1.80 `SM3Digest`. Do not edit this body to fit a failing test.

## Cipher (response decrypt)

1. Key = UTF-8 of the 32-character hex string, decoded to 16 bytes.
2. Ciphertext = UTF-8 of lowercase hex `result.str`.
3. Decrypt with **SM4 / ECB / PKCS5Padding** (PKCS7 on a 16-byte block).
4. Output is UTF-8 plaintext `symkey-fixture`.

Pipeline node: `cipher.sm4-ecb-decrypt`. `.key` uses sink `ENCRYPT`. No `config.encoding`. Flow MUST NOT decrypt. Session stores `result.token` and hex `result.str`. Session MUST NOT store the decrypted symKey. `timeOut` is ignored.

`cipher.sm4-ecb` stays encrypt-only with a 16-byte UTF-8 key and Base64 output. The 32-character hex key MUST still fail that encrypt node before HTTP.

## Business sign

Canonical preimage (one-field body, so sorted JSON equals the wire body):

```text
{"plateNo":"A12345"}&_t=<X-Time>&symKey=<symKey>
```

Whole-string percent-encode keeps only RFC 3986 unreserved (ALPHA, DIGIT, `-`, `.`, `_`, `~`). Space → `%20`. Every other octet → uppercase `%HH`. Then lowercase SM3.

Pipeline on each signed queryGps send (when `session.str` is present): decrypt → bind `flow.symKey` as secret → concat (`{"plateNo":"A12345"}&_t=`, business epoch, `&symKey=`, `flow.symKey` with sink `HASH`) → `canonicalizer.percent-encode` → `hasher.sm3`. Flow MUST NOT encrypt, percent-encode, or hash.

Headers: `X-Access-Token` (`session.token`, sink `HEADER`), `X-Username` (`test-user`, sink `HEADER`), `X-System` (`test-sys`), `X-Time` (business epoch), `X-Sign` (digest). When `session.str` is absent, skip the sign pipeline and omit `X-Access-Token` and `X-Sign`.

### Known sign vectors

| Epoch | Percent-encoded preimage | `X-Sign` |
|-------|--------------------------|---------|
| `1700000000000` | `%7B%22plateNo%22%3A%22A12345%22%7D%26_t%3D1700000000000%26symKey%3Dsymkey-fixture` | `8da4c1ef6d160c49f7e5494c8caa76b02c677f0d795e77a914a74b81029a3647` |
| `1700000002000` | `%7B%22plateNo%22%3A%22A12345%22%7D%26_t%3D1700000002000%26symKey%3Dsymkey-fixture` | `9b4464d0f66c0b9de9d640bcfe9a5fc8126772f4533985198b85b88ea6f1675c` |

Computed with BouncyCastle 1.80 `SM4Engine` + `PKCS7Padding` and `SM3Digest`. Do not edit a vector to fit a failing test.

## Session and replay

queryGps: `SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`.  
getToken: `UNSAFE`, `allowAutomaticReplay: false`, `maxAttempts: 1`.  
`failureCooldown`: 5s.

1. First execute stamps business epoch, sends queryGps without token → 401 → getToken → store token + ciphertext → replay with `X-Sign` for `1700000000000`.
2. Second execute with a valid session does not call getToken; one queryGps with `X-Time` `1700000002000` and the second `X-Sign`.
3. getToken with `success` false, or 200 without `result.str`: session fails; no signed queryGps; cooldown follow-up sends no HTTP.

## Secrets

Agreed key and `symkey-fixture` MUST be absent from outbound headers, body, and query, and from DecisionTrace and exception messages. `test-user` appears as `X-Username` only. `test-token` appears as `X-Access-Token` only. Neither appears in DecisionTrace or exception messages.

## Out of scope

Support-vehicle and support-task (no login), metro SHA256, `timeOut` renewal, CBC/IV, folding decrypt into `cipher.sm4-ecb`, copying `QyhGameVehicleGpsExtendParam` credentials, Host / Catalog / Groovy, `sm-crypto` dependency, public internet, production secrets.

## FakeTransport script (acceptance)

| Send | Expectation |
|------|-------------|
| probe queryGps | no `X-Access-Token`; FakeTransport 401 |
| getToken | body equals the published login vector |
| replay queryGps | body `{"plateNo":"A12345"}`, `X-Time` `1700000000000`, `X-Sign` first published value |
| second execute | zero getToken; one queryGps with `X-Time` `1700000002000` and second `X-Sign` |
