# Proposal

## Why

大华合成作战平台 `DahuaTcplClient.getSecurityPassword` 是尚未覆盖的公钥加密：口令先做两层 MD5，再用上一跳返回的 X.509 公钥做 `RSA/ECB/PKCS1Padding`，Base64 后放进登录体的 `password`。目录已有 `hasher.md5` 与 `cipher.sm4-ecb`，后者的密钥是 credential 上的 sink `ENCRYPT`；这里的密钥是服务器公钥，而且 PKCS1 填充使密文每次不同。

## What Changes

- 协议门禁：协议表 + Canonical YAML + FakeTransport 测试。冻结 `GET https://dahua-tcpl.example/jg/AuthPlatform/accounts/publicKey`，再 `POST https://dahua-tcpl.example/jg/AuthPlatform/accounts/login`。`s1 = md5(password)`，`s2 = md5(userName + s1)`，RSA 加密的是 `s2` 的 UTF-8。登录体字段顺序为 `userName`、`password`、`publicKey`、`subjectSessionId`。登录头是 `X-Subject-SessionId`。业务桩用 `X-Subject-Token`。
- 目录准入：新增 `cipher.rsa-pkcs1`。`.in` 是待加密字节，`.key` 是 SPKI Base64 的 UTF-8 字节，来自变量而不是 `secretRef`。填充写死，输出只做无换行 Base64。Flow 不算 MD5，也不做 RSA。
- 验收解密登录体里的 `password`，明文必须等于冻结的 `s2` hex。口令不出现在 body、trace、异常里。用户名只作为登录体的 `userName` 出现。
- **不做**：视频云 `encryptType=RSA`（源码直接抛「暂不支持」）；保活 `PUT`；OAEP 与可配置填充；抄录旧 mock 里的公钥和 token；Host / Catalog / Groovy；真外网；生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: 合成作战平台公钥登录的 Canonical Definition + FakeTransport 门禁（两层 MD5、`cipher.rsa-pkcs1`、解密断言、口令不上线）。
- `pipeline-graph`: 内置 `cipher.rsa-pkcs1`（bytes 进、string 出，`encoding: base64`）；`.key` 接受 SPKI Base64 字节，拒绝 `secretRef`。

## Impact

- Runtime: `PipelineGraphValidator` 与 `PipelineExecutor` 增加 `cipher.rsa-pkcs1`。实现使用 JDK `Cipher` `RSA/ECB/PKCS1Padding`，不注册 JCA Provider，不新增 Maven 依赖。`SecretSink` 不新增取值。`cipher.sm4-ecb` 的 `ENCRYPT` 规则保持不变。
- Docs: `docs/design/v2.7-protocols/dahua-tcpl.md` 与 `dahua-tcpl.yaml`。真相源是 `DahuaTcplClient.publicKey`、`login` 与 `getSecurityPassword`（考古引用，不引入依赖）。
- Tests: 新 FakeTransport 协议测试，以及 RSA 节点的管道测试。既有 Wenxin、Gaode、IDPS、Huawei、DaHua MD5、Artemis GET/POST、PMS、科拓、数运、中电科门禁保持全绿。
- Credentials: 夹具用户名 `test-user`、口令 `test-pass`、会话 `test-session-id`、token `test-token`，另生成一对仅用于测试的 RSA 密钥。生产主机、公钥和私钥不入库。
