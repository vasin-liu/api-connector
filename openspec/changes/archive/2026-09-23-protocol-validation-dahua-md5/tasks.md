## 1. Engine unlock — HASH sink

- [x] 1.1 Add `SecretSink.HASH` and allow it in `SecretSinkPolicy` destination checks (same apiId rule as HMAC); add unit tests for matching api allow, wrong api deny, and verify Gaode/IDPS-style HMAC checks still pass

## 2. Engine unlock — hasher.md5 and concat/sorted-query sinks

- [x] 2.1 Register `hasher.md5` in `PipelineGraphValidator` with `in: bytes` → `out: hex string` and implement MD5 → lowercase hex in `PipelineExecutor`; verify unknown hasher types fail compile and string/secret edges into `in` without a bytes producer fail type check
- [x] 2.2 Fix `PipelineExecutor.concat` and sorted-query `resolvePart` to honor an explicit declared sink on `secretRef` parts while defaulting omitted sink to `HMAC`; add unit tests that HASH on concat→hasher works for password and username parts, and that HMAC into a hash path is denied

## 3. Engine unlock — 401 CONTINUE

- [x] 3.1 Add a focused Flow/FakeTransport fixture: authentication **request** returns HTTP 401, transition `CONTINUE`, then **separate EXTRACT** steps bind JSON fields; no `CHALLENGE` / nested AUTHENTICATE; unmatched 401 still FAILs

## 4. Protocol table and YAML

- [x] 4.1 Write `docs/design/v2.7-protocols/traffic-dahua.md` with authorize path, hop-1 401 fields (non-blank realm/randomKey/encryptType=MD5, **no method key**), no-method nested MD5 formula, hop-2 token fields, `clientType=web`, `expiredTime=86400`, `Content-Type` with charset, `X-Api-Version=V1.0`, two `type: secret` credentials `dahuaUser`/`dahuaPass`, `X-Subject-Token` + `sink: HEADER`, stub business URL, TTL/cooldown, fixtures and published signature hex; cite `TrafficDaHuaClient` / `TrafficDaHuaUtils`; no production secrets
- [x] 4.2 Add `docs/design/v2.7-protocols/traffic-dahua.yaml` per D9/D10 (challenge CONTINUE with realm+randomKey+encryptType=MD5 → extracts → HASH concat + hasher nest → token → VALID), wire `ProtocolDefinitions.trafficDahua()`, verify compile + stable `planId`

## 5. FakeTransport protocol gate

- [x] 5.1 Add `TrafficDaHuaProtocolTest`: business 401 → one challenge + one token authorize → replay with `X-Subject-Token=test-token` and signature matching the table; valid session skips authorize; challenge without `randomKey` + cooldown does not storm; password/username/token absent from trace; MD5 only via pipeline nodes
- [x] 5.2 Verify Wenxin, Gaode, IDPS, and Huawei protocol tests still pass

## 6. Gap discipline

- [x] 6.1 If nested MD5 cannot be expressed without Script/Groovy/Catalog, without HASH for username, or without treating hop-1 401 as CHALLENGE, stop and update this change's specs/design rather than adding AuthProvider or vendor node types, and verify no `*ConnectorCatalog` types were introduced
