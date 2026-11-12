# Proposal

## Why

数运 `ShuyunClient.sign` 是尚未覆盖的 HTTP 头签名：`appId + appSecret + timestamp + nonce` 无分隔符拼接后做 SM3，小写 hex 写入 `signature`。目录已有 `hasher.md5` 与 `signer.hmac-sha256`，没有 SM3。前面的协议门禁都把这条留在门外；BouncyCastle 已在 runtime 依赖里，运行时代码尚未使用它。

## What Changes

- 协议门禁：协议表 + Canonical YAML + FakeTransport 测试。冻结 `POST https://shuyun.example/sznszhddpt/rest/event/list`。签名材料是四段 UTF-8 直接拼接，摘要是小写 hex。
- 目录准入：新增 `hasher.sm3`。输入是已有 `canonicalizer.concat` 的字节；`appSecret` 只在该 concat 上以 sink `HASH` 进入。Flow 不计算 SM3。
- 头：`x-app-id`、`nonce`、`timestamp`、`signature`，以及四个不参与签名的业务字面量 `operationUserId`、`operationUserName`、`operationUserCode`、`operationAppCode`。
- 重试：503 → `RETRY_FLOW`。authentication 只重新盖时间戳和 nonce，自身不发 HTTP。第二次请求是新实例，`timestamp`、`nonce`、`signature` 都与第一次不同。
- **不做**：`pageList` 与 `complete`；Hutool nanoId 的字母表和长度；平台默认 `getBytes()`；SM4；讯飞 WebSocket；科拓空串/嵌套过滤与新鲜 `ts`/`reqId`；Host / Catalog / Groovy；真外网；生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: 数运 list 的 SM3 头签名 Canonical Definition + FakeTransport 门禁（空分隔符 concat、`hasher.sm3` 小写 hex、无 HTTP 的 authentication 重盖章、`RETRY_FLOW`）。
- `pipeline-graph`: 内置 `hasher.sm3`（bytes 进、string 出，`encoding: hex`）；直接喂给它的 concat 上的 `secretRef` 必须是 sink `HASH`。

## Impact

- Runtime: `PipelineGraphValidator` 与 `PipelineExecutor` 增加 `hasher.sm3`。实现使用已有 `bcprov-jdk18on` 的 `SM3Digest`。无新 Maven 依赖，无新节点种类以外的目录改动。
- Docs: `docs/design/v2.7-protocols/shuyun.md` 与 `shuyun.yaml`。真相源是 `ShuyunClient.sign` 与 `getBusiHeader`（考古引用，不引入依赖）。
- Tests: 新 FakeTransport 协议测试。既有 Wenxin、Gaode、IDPS、Huawei、DaHua、Artemis GET/POST、PMS、科拓门禁保持全绿。
- Credentials: fixture 为 `test-app-id` / `test-app-secret`。旧 `ShuyunClientTest` 里的主机和密钥不入库。
