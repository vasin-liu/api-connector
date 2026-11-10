# Design

## Context

See `proposal.md` for why. Truth source is `KeytopSignUtil.paramsSign` plus `KeytopClient` / `BaseClient.postHttpRequest` (read-only; no dependency). `paramsSign` drops `key`, `appId`, null, empty string, Map, and Iterable, sorts the remaining keys, joins unescaped `key=value` with `&`, appends `&` + secret, and returns uppercase hex MD5. The client then puts that string on the JSON field `key`. Catalog already has `canonicalizer.sorted-query` (`encoding: none`, `exclude`), `canonicalizer.concat` (a `secretRef` part must declare sink HASH), and `hasher.md5` `hex-upper`. Concat parts are `var` / `value` / `secretRef` only; a prior node's bytes become usable after they are bound to a variable (`BytesValue` reads back as UTF-8). `codec.json` encodes the execution object, so it is the wrong body builder.

## Goals / Non-Goals

**Goals:**

- One FakeTransport POST whose JSON field `key` equals the javadoc digest, and whose signed material equals the javadoc string.
- Build that signature with the existing catalog. No new node.
- 503 → `RETRY_REQUEST`: second body bytes equal the first.

**Non-Goals:**

- Teaching `sorted-query` to skip empty strings or nested Map/Iterable values. The fixture supplies the javadoc strings, which are already flat and non-empty.
- `getFreeSpaceNum` / `getParkingPlaceArea` / `getCarInoutInfo` business fields, integer `appId`, or fresh `ts` / `reqId`.
- 数运 SM3, 讯飞 WebSocket, PMS production appkey-in-body.

## Decisions

### D1. Javadoc vector on a real route

**Choice:** POST `https://keytop.example/api/wec/GetFreeSpaceNum`. The body is the `KeytopSignUtil` javadoc sample plus `key`, not the `getFreeSpaceNum` field set. Sign does not use the path.

**Why:** The comment is the only published input/output pair (`B19F7863ADCC8B5442A757AC7B90F6AC`). The path is a real `KeytopClient` route so the gate is not an invented URL. Replacing the body with `getFreeSpaceNum`'s fields would drop that pair.

**Alternatives:** Invent a digest over `test-sk` and a small body — loses the published witness. Sign the live method's fields — no frozen expected digest in source.

### D2. Two pipelines, variable in the middle, no codec.json

**Choice:**

1. `canonicalizer.sorted-query`, `encoding: none`, `exclude: [appId, key]`, one `var` per javadoc field including `appId`. Bind `execution.canonical` from that node's bytes.
2. `canonicalizer.concat` separator `&`, parts `[execution.canonical, secretRef appSecret sink HASH]`, then `hasher.md5` `encoding: hex-upper`. Bind `execution.key`.
3. Wire body is another concat: frozen JSON prefix, `execution.key`, frozen suffix. Headers: `Content-Type: application/json;charset=UTF-8` (hutool `ContentType.JSON` on `postHttpRequest`), `version: 1.0.0`, `accept-language: zh-CN`.

**Why:** `exclude` already implements the `appId` / `key` omission (Gaode excludes `sig` the same way). Binding bytes to a variable is how Artemis feeds concat. The secret enters only the HASH part. `codec.json` would also emit nonce and other execution fields.

**Alternatives:** A new "merge signature into JSON object" node — catalog growth for one fixture. Pre-baked JSON with the digest pasted in and no pipeline — would not prove `sorted-query`.

### D3. Published vectors

Sample secret `EED96C219E83450A` is the javadoc sample, not a production key. `appId` and `paySource` in that sample are `85d15350778b11e9bbaa506b4b2f6421`.

| Field | Value |
|-------|--------|
| Signed material | `amount=100&freeDetail=[{"code":"","money":100,"time":0,"type":0}]&freeMoney=100&freeTime=0&orderNo=闽C12345&outOrderNo=T20200306124536001&parkId=1000001&payMethod=6&paySource=85d15350778b11e9bbaa506b4b2f6421&payTime=2020-03-06 10:57:22&payType=1006&payableAmount=200&paymentExt={"deviceNo":"123456"}&reqId=5be4e3e6d5704a7d91ccbd9731d970f5&ts=1583744086841&EED96C219E83450A` |
| `key` | `B19F7863ADCC8B5442A757AC7B90F6AC` |
| Lowercase hex (MUST NOT be `key`) | `b19f7863adcc8b5442a757ac7b90f6ac` |

The protocol table freezes one UTF-8 JSON byte string (concat order). That order is the fixture's, not fastjson's. Replay compares those bytes.

### D4. 503 → RETRY_REQUEST, no authentication flow

**Choice:** `replayability: SAFE`, `allowAutomaticReplay: true`, `maxAttempts: 2`. Transitions: 503 → `RETRY_REQUEST`, 200 → `SUCCESS`. No `authentication` key. `maxAuthAttempts` at least 1.

**Why:** Every signed field is a GLOBAL literal, so the digest is deterministic. `RETRY_REQUEST` re-renders without `authenticate`. `RETRY_FLOW` would require an authentication flow and would fail closed when that flow is absent.

**Alternatives:** `RETRY_FLOW` with a re-sign authentication flow — right only if `ts` or `reqId` were regenerated. They are not, in this gate.

### D5. Stop rather than grow the catalog

**Choice:** If sorted-query, the variable binding, or the body concat cannot produce this wire body, stop and update this change's spec and design. Do not add a node, do not hash in Flow, and do not drop `appId` from the body to make the digest easier.

**Why:** The proposal's claim is that the current catalog is enough. A failing test that needs a new node is a spec change, not an implementation shortcut.

## Risks / Trade-offs

- [Body is not a `getFreeSpaceNum` payload] → Protocol table says so. The path is only the transport stub.
- [Empty and nested filtering stay in the Java client] → Fixture has no empty or nested values. A later change owns that walker if a real payload needs it.
- [Chinese and embedded JSON in `encoding: none`] → `sorted-query` already writes those characters unescaped. The published string is the assertion, not a percent-encoded form.
- [Javadocs sample secret in git] → It is the published sample inside `KeytopSignUtil`, recorded as a fixture. No production secret.

## Migration Plan

No runtime migration. The change adds a protocol table, Canonical YAML, and a FakeTransport test.

## Open Questions

None.
