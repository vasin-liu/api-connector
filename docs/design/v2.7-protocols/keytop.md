# Keytop paramsSign — protocol table

Canonical Definition: [`keytop.yaml`](keytop.yaml).  
Source: legacy `suntek-system/system-thirdpart` `KeytopSignUtil.paramsSign` + `KeytopClient` / `BaseClient.postHttpRequest`（考古引用，不引入依赖）。This Runtime MUST NOT depend on any vendor SDK JAR.

**No production secrets.** The secret below is the sample published in the `paramsSign` javadoc, not a live credential.

The POST path is a real `KeytopClient` route. The body is **not** a `getFreeSpaceNum` payload. Sign does not use the path; the body is the javadoc sample plus the `key` field so the published digest stays the witness.

## Endpoint

| Field | Value |
|-------|--------|
| Base URL | `https://keytop.example` (FakeTransport only) |
| Method | `POST` |
| Path | `/api/wec/GetFreeSpaceNum` |
| Auth profile (YAML) | `keytop-params-sign-v1` |

## Headers

| Name | Source | Value |
|------|--------|-------|
| `Content-Type` | GLOBAL | `application/json;charset=UTF-8` (hutool `ContentType.JSON` on `postHttpRequest`; no space after `;`) |
| `version` | GLOBAL | `1.0.0` |
| `accept-language` | GLOBAL | `zh-CN` |

The secret is never a header value.

## Sign

```text
key = MD5( sorted-query(fields, encoding=none, exclude=appId,key) + "&" + secret ).toUpperCase()
```

- Field values are the javadoc strings. Empty-string and nested Map/Iterable filtering are **out of scope**; this fixture has neither.
- `appId` is in the JSON body and excluded from the signed material (`sorted-query` `exclude`).
- `secret` enters only the concat part `secretRef` with sink `HASH`.
- Digest: `hasher.md5` with `encoding: hex-upper`.
- The digest is written into JSON field `key` by a body concat (prefix + `execution.key` + suffix). No `codec.json`.

Pipeline: `canonicalizer.sorted-query` → bind `execution.canonical` → `canonicalizer.concat` (separator `&`, parts `[canonical, secretRef(HASH)]`) → `hasher.md5` `encoding: hex-upper` → bind `execution.key`. Flow MUST NOT sort, join, or MD5.

## Known test vector (FakeTransport only)

| Input | Value |
|-------|--------|
| Signed material | `amount=100&freeDetail=[{"code":"","money":100,"time":0,"type":0}]&freeMoney=100&freeTime=0&orderNo=闽C12345&outOrderNo=T20200306124536001&parkId=1000001&payMethod=6&paySource=85d15350778b11e9bbaa506b4b2f6421&payTime=2020-03-06 10:57:22&payType=1006&payableAmount=200&paymentExt={"deviceNo":"123456"}&reqId=5be4e3e6d5704a7d91ccbd9731d970f5&ts=1583744086841&EED96C219E83450A` |
| `key` (r1 = r2) | `B19F7863ADCC8B5442A757AC7B90F6AC` |
| lowercase hex (MUST NOT be `key`) | `b19f7863adcc8b5442a757ac7b90f6ac` |
| Sample secret | `EED96C219E83450A` |
| `appId` / `paySource` | `85d15350778b11e9bbaa506b4b2f6421` |

Wire body (fixture concat order, not fastjson order):

```text
{"amount":100,"appId":"85d15350778b11e9bbaa506b4b2f6421","freeDetail":"[{\"code\":\"\",\"money\":100,\"time\":0,\"type\":0}]","freeMoney":100,"freeTime":0,"orderNo":"闽C12345","outOrderNo":"T20200306124536001","parkId":"1000001","payMethod":6,"paySource":"85d15350778b11e9bbaa506b4b2f6421","payTime":"2020-03-06 10:57:22","payType":1006,"payableAmount":200,"paymentExt":"{\"deviceNo\":\"123456\"}","reqId":"5be4e3e6d5704a7d91ccbd9731d970f5","ts":1583744086841,"key":"B19F7863ADCC8B5442A757AC7B90F6AC"}
```

## Replay / freshness

Every signed field is a GLOBAL literal. YAML uses `replayability: SAFE` + 503 → `RETRY_REQUEST`:

1. Bind the sorted material, then the digest, then POST.
2. FakeTransport returns **503**.
3. `RETRY_REQUEST` re-renders the request: a **new `RawHttpRequest` instance** whose body bytes and `key` **equal** the first send.
4. No `authentication` flow.

## Out of scope

| Topic | Decision |
|-------|----------|
| Empty string / nested Map / Iterable filtering | Not in this fixture. `sorted-query` is not taught that walker. |
| `getFreeSpaceNum` / `getParkingPlaceArea` / `getCarInoutInfo` business fields | Not this body. |
| Integer `appId`, fresh `ts` / `reqId` | Literals stay fixed so the javadoc digest holds. |
| 数运 SM3, 讯飞 WebSocket, PMS production appkey-in-body | Other changes. |
| Live portal verification | FakeTransport only. |

## FakeTransport script (acceptance)

| Send | Body `key` | Body bytes |
|------|------------|------------|
| r1 | `B19F7863ADCC8B5442A757AC7B90F6AC` | the frozen JSON above |
| r2 (after 503 + RETRY_REQUEST) | identical | identical |

r2 is a new `RawHttpRequest` instance. The sample secret never appears on the wire or in the DecisionTrace.
