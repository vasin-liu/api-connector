# Proposal

## Why

中电科 `CetcClient.postRequest` 是尚未覆盖的 HTTP body 加密：Jackson 明文经 SM4/ECB/PKCS5Padding 后做标准 Base64，再套进 `{"data":"<密文>"}`。目录已有 `hasher.sm3` 与 `signer.hmac-sha256`，没有分组密码节点；密钥进的是 cipher key，不是摘要原文，也不是 HMAC。

## What Changes

- 协议门禁：协议表 + Canonical YAML + FakeTransport 测试。冻结 `POST https://cetc.example/detect/third/traffic/event/collect`。明文是六个必填字段的字母序 JSON 字面量；密文是该 UTF-8 的 SM4/ECB/PKCS5 Base64；body 是紧凑的 `{"data":"<Base64>"}`。
- 目录准入：新增 `cipher.sm4-ecb`。明文 bytes 进 `.in`，`appSecret` 只从 `.key` 以 sink `ENCRYPT` 进入。模式与填充写死，输出只做 `base64`。Flow 不计算 SM4。
- 响应保持明文 JSON。200 即成功。密钥不出现在 body、trace、异常里。
- **不做**：响应解密；可选字段与 Jackson 重放；24/32 字节密钥；CBC/IV；全运会另一套 SM4；503 重试；Host / Catalog / Groovy；真外网；生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: 中电科事件采集的 SM4 body 加密 Canonical Definition + FakeTransport 门禁（字母序明文、`cipher.sm4-ecb` Base64、`{"data":...}` 信封、密钥不上线）。
- `pipeline-graph`: 内置 `cipher.sm4-ecb`（bytes 进、string 出，`encoding: base64`）；`.key` 上的 `secretRef` 必须是 sink `ENCRYPT`。

## Impact

- Runtime: `SecretSink` 增加 `ENCRYPT`。`PipelineGraphValidator` 与 `PipelineExecutor` 增加 `cipher.sm4-ecb`。实现使用已有 `bcprov-jdk18on` 的 `SM4Engine`，不注册 JCA Provider。无新 Maven 依赖。
- Docs: `docs/design/v2.7-protocols/cetc.md` 与 `cetc.yaml`。真相源是 `CetcClient.postRequest` 与 `CetcUtils.sm4Encrypt`（考古引用，不引入依赖）。
- Tests: 新 FakeTransport 协议测试，以及 cipher 节点的管道测试。既有 Wenxin、Gaode、IDPS、Huawei、DaHua、Artemis GET/POST、PMS、科拓、数运门禁保持全绿。
- Credentials: fixture 密钥为恰好 16 字节的 `test-cetc-secret`。生产主机和密钥不入库。
