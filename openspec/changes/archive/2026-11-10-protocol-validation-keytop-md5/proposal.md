# Proposal

## Why

科拓 `KeytopSignUtil.paramsSign` 是 system-thirdpart 里尚未覆盖的 HTTP 签名：JSON 字段按 key 排序，拼成未转义的 `key=value&...`，去掉 `appId` 和 `key`，末尾再接 `&` 加 secret，MD5 后转大写，写回 body 字段 `key`。现有目录已经有 `sorted-query`（`encoding: none` 与 `exclude`）、`concat` 的 HASH sink 和 `hasher.md5` 的 `hex-upper`，这条协议可以钉死，不必再加节点。

## What Changes

- 协议门禁：协议表 + Canonical YAML + FakeTransport 测试。冻结 `KeytopSignUtil` 注释里的公开向量，签名为 `B19F7863ADCC8B5442A757AC7B90F6AC`。
- 传输桩：POST `https://keytop.example/api/wec/GetFreeSpaceNum`（`KeytopClient` 的真实路径）。签名只依赖 body，冻结 body 用注释向量，不是 `getFreeSpaceNum` 的业务字段集。
- 重试：字段是固定字面量，503 → `RETRY_REQUEST`，第二次 body 与 `key` 与首次相同。
- secret 只进入 MD5 材料（sink HASH），不出现在 body / header / query / trace。
- **不做**：空串与嵌套 Map/Iterable 的过滤；`KeytopVO` 的整数 `appId` 与新鲜 `ts`/`reqId`；数运 SM3；讯飞 WebSocket；PMS 生产 appkey 进 body；Host / Catalog / Groovy；真外网；生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: 科拓 body MD5 `key` 的 Canonical Definition + FakeTransport 门禁（`sorted-query` 排除 `appId`/`key`，`&` + secret，`hex-upper`，确定性 `RETRY_REQUEST`）。

## Impact

- Runtime: 无目录增量。签名由已有 `canonicalizer.sorted-query`、`canonicalizer.concat`、`hasher.md5` 组成。
- Docs: `docs/design/v2.7-protocols/keytop.md` + `keytop.yaml`。真相源为 `KeytopSignUtil.paramsSign` 与 `KeytopClient`（考古引用，不引入依赖）。
- Tests: 新 FakeTransport 协议测试。既有 Wenxin、Gaode、IDPS、Huawei、DaHua、Artemis GET/POST、PMS 门禁保持全绿。
- Credentials: 注释样本 secret `EED96C219E83450A`（不是生产密钥）；git 无生产密钥。
