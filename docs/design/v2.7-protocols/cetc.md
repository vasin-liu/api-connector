# CETC SM4 body encrypt — protocol table

Canonical Definition: [`cetc.yaml`](cetc.yaml).  
Sources: `system-thirdpart` `com.suntek.system.thirdpart.client.cetc.CetcClient.postRequest` and `CetcUtils.sm4Encrypt`. Old Java Catalog/AuthProvider is gone.

**No production secrets.** Tests use `test-cetc-secret` only (exactly 16 UTF-8 bytes). Do not copy a production host or secret.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://cetc.example` (FakeTransport only) |
| Method | `POST` |
| Path | `/detect/third/traffic/event/collect` |
| Auth profile (YAML) | `cetc-sm4-body-v1` |
| `Content-Type` | `application/json;charset=UTF-8` |

## Plaintext (frozen)

Alphabetical, null-omitted JSON of the six `@NotBlank` fields on `EventCollectRequest`:

```text
{"alarmNo":"a1","alarmTime":"2026-01-01 00:00:00","deviceCode":"dev-1","factory":"test","picPath":"https://cetc.example/p","type":"98","videoPath":"https://cetc.example/v"}
```

Charset contract is UTF-8. The definition stores this as a GLOBAL literal. Flow MUST NOT re-run Jackson.

## Cipher

1. Key = UTF-8 bytes of `test-cetc-secret` (length **16**). SM4 is 128-bit; other lengths fail before HTTP.
2. Encrypt plaintext UTF-8 with **SM4 / ECB / PKCS5Padding** (PKCS7 on a 16-byte block).
3. Emit **RFC 4648 Base64** with padding, no line wrap (`java.util.Base64.getEncoder()`).
4. Wrap as the compact body `{"data":"<base64>"}`.

Pipeline: `canonicalizer.concat` of the frozen plaintext → `cipher.sm4-ecb` (`encoding: base64`, key edge sink `ENCRYPT`) → bind ciphertext to a FLOW variable → body concat of `{"data":"`, ciphertext, `"}`. Flow MUST NOT encrypt.

`appSecret` is the cipher key only. It MUST NOT appear as a header, query, or body field.

### Known test vector (FakeTransport only)

| Item | Value |
|------|--------|
| Key | `test-cetc-secret` |
| Plaintext | the frozen JSON above |
| Body | `{"data":"ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g="}` |

Computed with BouncyCastle 1.80: JCA `SM4/ECB/PKCS5Padding` and lightweight `SM4Engine` + `PKCS7Padding` match. Do not edit this body to fit a failing test. `+`, `/`, and `=` stay raw inside the JSON string.

## Response

HTTP **200** with plaintext JSON (for example `{"code":200,"msg":"ok"}`). The runtime MUST NOT decrypt the response. `CetcUtils.sm4Decrypt` is not on this call path.

## Replay

Replay is `UNSAFE`, `allowAutomaticReplay: false`, `maxAttempts: 1`. There is no authentication flow and no 503 retry. ECB over a fixed plaintext is deterministic; a second body cannot prove rebuild.

## Assumptions

| Topic | Frozen here | Unverified vs live CETC |
|-------|-------------|-------------------------|
| Charset | UTF-8 | Same as `CetcUtils` |
| Key length | 16 bytes only | Source comment mentions 24/32; BC rejects those |
| Routes | `collect` only | Other URIs share the same encrypt path |
| Body fields | Six required fields | Optional fields omitted by Jackson `NON_NULL` |

## Out of scope

Response decryption, optional event fields, Jackson re-serialization inside the pipeline, 24/32-byte keys, CBC/IV, 全运会 SM4 (`QyhGameVehicleGpsClient`), 503 retry, Host / Catalog / Groovy, public internet, production secrets.

## FakeTransport script (acceptance)

| Send | Expectation |
|------|-------------|
| r1 | path `/detect/third/traffic/event/collect`, body equals the published vector above, not the plaintext |
| response | 200 `{"code":200,"msg":"ok"}` → SUCCESS; response bytes unchanged |
