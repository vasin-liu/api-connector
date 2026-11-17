# Proposal

## Why

全运会地铁 `QyhMetroClient.inOutRecord` 还没有协议门禁。`signature` 是 `timestamp + token + nonce + timestamp` 的 SHA-256 大写十六进制，`timestamp` 是 epoch 秒。目录里有 `hasher.md5`、`hasher.sm3` 和带密钥的 `signer.hmac-sha256`，没有无密钥 SHA-256。Flow 目前只接受 `{ now: epochMillis }` 与 `{ now: isoOffset }`，`{ now: epochSeconds }` 在校验阶段失败。

## What Changes

- 协议门禁：协议表 + Canonical YAML + FakeTransport 测试。冻结 `GET https://qyh-metro.example/jtj/in-out`，query 为 `start_time=2026-01-01T00:00:00` 与 `end_time=2026-01-01T00:10:00`。请求头为 `x-tif-paasid`、`timestamp`、`nonce`、`signature`。`timestamp` 来自 `{ now: epochSeconds }`。`nonce` 来自已有的 `{ generate: nonce }`。`signature` 是上述拼接的大写十六进制 SHA-256。Flow 不算 SHA-256。
- 无登录、无 Session。HTTP 503 选择 `RETRY_FLOW`。认证 Flow 不发 HTTP，只重新取秒级时间戳和 nonce。下一次 GET 的 `timestamp`、`nonce`、`signature` 必须与第一次不同，query 保持不变。HTTP 200 选择 SUCCESS，响应体保持 FakeTransport 返回的字节。
- 目录准入：新增 `hasher.sha256`。`.in` 只接受字节。`config.encoding` 为 `hex`、`hex-upper`、`base64`，省略时为小写十六进制。本门禁使用 `hex-upper`。摘要原文由 `canonicalizer.concat` 提供，token 以 sink `HASH` 进入该 concat。`secretRef` 不得直接连到 `hasher.sha256.in`。`hasher.md5`、`hasher.sm3` 与 `signer.hmac-sha256` 的规则不改。
- Flow 准入：`{ now: epochSeconds }` 取注入时钟的毫秒除以 1000、向零截断，赋值为整数。请求头上的文本是这个整数的十进制，不带小数。其它未知 `now` 形式仍然校验失败。
- token 不出现在 header、query、body、DecisionTrace 和异常里。`x-tif-paasid` 使用合成 pass id，该值不出现在 DecisionTrace 和异常里。
- **不做**：禹好办（同一条摘要，本变更不建它的定义）；保障车辆与保障任务；`timeOut` 续期；`Math.random()` 的 nonce；抄录 `QyhMetroExtendParam` 里的 token、pass id 和生产 URL；query 时间里的空格（`yyyy-MM-dd HH:mm:ss`）；解析 `success` / 空列表；Host / Catalog / Groovy；讯飞 WebSocket；真外网；生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: 全运会地铁进出站的 Canonical Definition + FakeTransport 门禁（epoch 秒、可注入 nonce、无密钥 SHA-256 大写十六进制、token 不上线、503 后重建签名）。
- `pipeline-graph`: 内置 `hasher.sha256`（字节进、字符串出；`hex` / `hex-upper` / `base64`；喂入 concat 上的 secret 使用 sink `HASH`）。既有 MD5、SM3、HMAC-SHA256 规则不改。
- `flow-runtime`: `{ now: epochSeconds }` 成为合法 assign。它产出注入时钟的整秒。未知 `now` 形式仍然失败，负例不再使用 `epochSeconds`。

## Impact

- Runtime: `PipelineGraphValidator` 与 `PipelineExecutor` 增加 `hasher.sha256`，摘要使用 JDK `MessageDigest` SHA-256，不新增 Maven 依赖。`FlowRuntime` 与 `DefinitionValidator` 接受 `epochSeconds`。`DefinitionValidatorTest.unknownNowForm` 改用别的未知 `now` 形式。
- Docs: `docs/design/v2.7-protocols/qyh-metro.md` 与 `qyh-metro.yaml`。真相源是 `QyhMetroClient.inOutRecord` 与 `getSHA256`。考古引用，不引入该客户端。
- Tests: 新 FakeTransport 协议测试，以及 `hasher.sha256` 的管道测试。既有文心、高德、IDPS、华为、大华、Artemis、PMS、科拓、数运、中电科、全运会 GPS 门禁保持全绿。
- Credentials: 夹具使用合成 token 与合成 pass id。生产主机、pass id 和 token 不入库。
