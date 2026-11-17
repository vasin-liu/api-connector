# Proposal

## Why

全运会赛事车辆 `QyhGameVehicleGpsClient` 还没有协议门禁。凭证接口返回的 `result.str` 要用约定密钥做 SM4/ECB 解密，明文才是和 token 绑定的 `symKey`；业务 `queryGps` 的 `X-Sign` 是对整段规范串做百分号编码后再 SM3。目录里的 `cipher.sm4-ecb` 只加密、密钥是 16 个 UTF-8 字节、输出 Base64，表达不了这套解密。

## What Changes

- 协议门禁：协议表 + Canonical YAML + FakeTransport 测试。冻结 `POST https://qyh-gps.example/zhjt/sportservice/basic-api/gzqyh/open/api/getToken` 与 `POST https://qyh-gps.example/zhjt/sportservice/basic-api/gzqyh/open/api/queryGps`。两条 URL 各带自己的合成 `app_id` 与 `api_key` query。登录体含 `sign`、`t`、`sysId`；`sign` 是 `username + t + 约定密钥` 的小写 SM3。`t` 与业务 `X-Time` 都是 epoch 毫秒，各跳各自取值。登录字段顺序由本门禁冻住，断言这三个字段的值。
- 凭证成功后抽出 `result.token` 与 `result.str`。`str` 是十六进制密文，用约定密钥解密得到 `symKey`。业务体冻成单字段 `{"plateNo":"A12345"}`。`X-Access-Token`、`X-Username`、`X-System`、`X-Time` 按源码送出。`X-Sign` 是 `hasher.sm3` 对百分号编码后的 `{"plateNo":"A12345"}&_t=<X-Time>&symKey=<symKey>` 的小写十六进制。Flow 不算 SM3，不做 SM4，也不做百分号编码。
- 目录准入：新增 `cipher.sm4-ecb-decrypt`。`.in` 是十六进制密文的 UTF-8，`.key` 是 32 位十六进制约定密钥的 UTF-8，经解码得到 16 字节；模式与 PKCS5/PKCS7 填充写死，输出是明文 UTF-8。`.key` 使用已有 sink `ENCRYPT`。同一约定密钥在登录 SM3 的 concat 上使用已有 sink `HASH`。新增 `canonicalizer.percent-encode`，按 RFC 3986 只保留 unreserved（字母、数字、`-`、`.`、`_`、`~`），编码整段规范串。`cipher.sm4-ecb` 的加密、16 字节 UTF-8 密钥和 Base64 输出保持不变。
- 会话保存 token。同会话的下一次执行不再调用 getToken，并重新取 `X-Time`、重新签名。凭证 `success` 不为真，或缺少 `str`：会话失败，冷却期内不再打登录。约定密钥与 `symKey` 不出现在 body、query、header、DecisionTrace 和异常里。用户名只作为 `X-Username` 和登录摘要原文出现。token 不出现在 DecisionTrace 和异常里。
- **不做**：保障车辆与保障任务（源码不登录）；地铁 SHA256；`timeOut` 续期；CBC/IV；把解密并进 `cipher.sm4-ecb`；抄录 `QyhGameVehicleGpsExtendParam` 里的网关 id、key 和约定密钥；Host / Catalog / Groovy；`sm-crypto` 依赖；真外网；生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: 全运会赛事车辆 GPS 的 Canonical Definition + FakeTransport 门禁（登录 SM3、十六进制 SM4/ECB 解密、单字段 queryGps、整串百分号编码后的 `X-Sign`、密钥与 `symKey` 不上线）。
- `pipeline-graph`: 内置 `cipher.sm4-ecb-decrypt`（十六进制密文进、明文 UTF-8 出；`.key` 为 32 位十六进制，sink `ENCRYPT`）和 `canonicalizer.percent-encode`（整串 RFC 3986 unreserved 编码）。既有 `cipher.sm4-ecb` 规则不改。

## Impact

- Runtime: `PipelineGraphValidator` 与 `PipelineExecutor` 增加 `cipher.sm4-ecb-decrypt` 和 `canonicalizer.percent-encode`。解密使用已有 `bcprov-jdk18on` 的 `SM4Engine`，不注册 JCA Provider，不新增 Maven 依赖。`SecretSink` 不新增取值。登录 SM3 继续走 `hasher.sm3`。
- Docs: `docs/design/v2.7-protocols/qyh-gps.md` 与 `qyh-gps.yaml`。真相源是 `QyhGameVehicleGpsClient.login`、`buildHeader`、`getVehicleGps`，以及 `antherd` `sm-crypto` 0.3.2 的默认 SM4（ECB、pkcs#5、十六进制密钥与密文）。考古引用，不引入该依赖。
- Tests: 新 FakeTransport 协议测试，以及解密节点和百分号编码节点的管道测试。既有 Wenxin、Gaode、IDPS、Huawei、DaHua、Artemis GET/POST、PMS、科拓、数运、中电科、大华 TCPL 门禁保持全绿。
- Credentials: 夹具使用合成的 32 位十六进制约定密钥、合成 `app_id` / `api_key`、合成用户名与 `sysId`。生产主机、网关 id 和密钥不入库。
