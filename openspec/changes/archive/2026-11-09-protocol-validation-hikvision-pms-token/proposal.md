# Proposal

## Why

海康威视在 system-thirdpart 里的第二个协议 PMS（下行 token 认证，2025-10 新增）是 Artemis 之外未覆盖的活集成需求：`token = MD5(uri + body + 密钥).toUpperCase()` 走 query 参数。现有目录只能输出小写 hex MD5，无法发射该 token。此前提案文字里的 "Accurio hex HMAC" 在真实代码库（50 个客户端包）中零命中，本变更以 `HikvisionPmsClient.withToken` + `BaseClient.postHttpRequest` 为唯一真相源，正式弃用 Accurio 提法。

## What Changes

- 目录增量：`DigestEncoding` 新增 `HEX_UPPER`，YAML `encoding: hex-upper`，仅作用于 `hasher.md5` / `signer.hmac-sha256` 的摘要字符串形态；`hex`/`base64` 输出不变，`.hex` 端口别名保持小写专属。
- 协议门禁：协议表 + Canonical YAML + FakeTransport 测试。冻结端点 POST `/getParkingInfo`，wire body 为一个 GLOBAL 字面量 `{"appkey":"test-ak"}` 形状的 fixture JSON，token 为大写 hex MD5(uri + body + secret) 并放 query `token=`。
- 重试语义：503 → `REPLAY_REQUEST` — token 是 uri+body 的确定函数，无新鲜度字段，第二次发送的 body 与 token 与首次相同（与 Artemis 的 `RETRY_FLOW` 重签形成对照）。
- SK 仅为 MD5 key（sink HASH），不出现在 query / body / trace。
- **不做**：生产 appkey 组装（concat 对 secretRef 强制 sink HASH，appkey 进 body 属已记录的后续目录缺口）；`/ImageServer*` 二进制下载；讯飞 WebSocket；Host / Catalog / AuthProvider / Groovy；真外网；生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `pipeline-graph`: `hasher.md5` / `signer.hmac-sha256` 的 `encoding` 配置新增合法值 `hex-upper`（摘要大写 hex）；其余编码值与端口别名行为不变。
- `protocol-validation`: Hikvision PMS 下行 token Canonical Definition + FakeTransport 门禁（冻结 body 字面量、query `token` 为大写 hex MD5(uri+body+secret)、确定性 `REPLAY_REQUEST` 重试、SK 仅作 MD5 key）。

## Impact

- Runtime: `DigestEncoding`（enum + parse + format）、`PipelineExecutor` 端口别名逻辑无改动需求（`.hex` 仅 HEX；HEX_UPPER 仅 `.out`）；`PipelineGraphValidator` 编码校验随 parse 自动放行 `hex-upper`。
- Docs: `docs/design/v2.7-protocols/hikvision-pms.md` + `hikvision-pms.yaml`。真相源为 `suntek-system/system-thirdpart` 的 `HikvisionPmsClient`（考古引用，不引入依赖）。
- Tests: 新 FakeTransport 协议测试；`Md5HasherTest` / `HmacSha256Test` / `PipelineGraphValidatorTest` 补 hex-upper 用例；既有 Wenxin、Gaode、IDPS、Huawei、DaHua、Artemis GET/POST 门禁保持全绿。
- Credentials: fixtures `test-ak` / `test-sk`；git 无生产密钥。
