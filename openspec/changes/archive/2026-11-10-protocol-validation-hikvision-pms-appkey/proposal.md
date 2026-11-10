# Proposal

## Why

海康 PMS 门禁把 `appkey` 冻成 GLOBAL 字面量 `{"appkey":"test-ak"}`。喂给 `hasher.md5` 的 `canonicalizer.concat` 上，`secretRef` 必须声明 sink `HASH`，凭证因此进不了线上 body。生产客户端的 body 含凭证里的 appkey，并且 `token = MD5(uri + body + secret)` 哈希的就是发出去的那份 body。这条缝不补，下一条要凭证上线的协议会停在同一处。

## What Changes

- 协议门禁：既有 PMS `POST /getParkingInfo` 的 body 改为 appkey 来自 `secretRef`。fixture 材料仍是 `test-ak`，线上 body 仍是 `{"appkey":"test-ak"}`，query `token` 仍是 `EDAA4B3136EA75DF9A435C95B920DD9E`。哈希所用的 body 字节必须就是实际发出的字节。
- 目录准入：产出线上 body 的 concat 可以接收 sink `BODY` 的 `secretRef`。哈希 concat 消费这份 body 字节；SK 仍只以 sink `HASH` 进入哈希材料。编译拒绝「同一段 concat 既是线上 body，其中的 `secretRef` 又只标 `HASH`」。直接喂给 `hasher.md5` 的 `secretRef` 仍必须是 `HASH`。
- appkey 出现在线上 body，不出现在 DecisionTrace 或异常信息。SK 不出现在 body、header、query、DecisionTrace 或异常信息。
- 既有 Artemis、科拓及其余协议门禁保持全绿。不改 token 公式，不新增节点类型。
- **不做**：数运 SM3、讯飞 WebSocket、`/ImageServer*`、其它 PMS 端点、appkey 的 JSON 转义、Host / Catalog / Groovy、真外网、生产密钥入库。

## Capabilities

### New Capabilities

- （无。）

### Modified Capabilities

- `protocol-validation`: PMS 下行 token 的 body appkey 改为凭证引用；已发布 token 向量，以及「哈希的 body 字节等于线上 body」保持不变。
- `pipeline-graph`: 产出线上 body 的 concat 允许 sink `BODY`；哈希侧消费该 body 字节；该 concat 上只标 `HASH` 的 `secretRef` 编译失败；`hasher.md5` 的直接入口仍要求 sink `HASH`。
- `secret-script`: `BODY` sink 可以把属于本 api 的凭证写入线上 body；DecisionTrace 与异常信息仍不得出现明文。SK 的 `HASH` 路径不变。

## Impact

- Runtime: `PipelineGraphValidator` 区分 body 出口与哈希入口；`PipelineExecutor` 的 concat 要把已产出的 body 字节送进哈希材料。无新节点，无新依赖。
- Docs: `docs/design/v2.7-protocols/hikvision-pms.md` 与 `hikvision-pms.yaml`。真相源仍是 `HikvisionPmsClient`（考古引用，不引入依赖）。
- Tests: 更新 PMS FakeTransport 门禁。既有 Wenxin、Gaode、IDPS、Huawei、DaHua、Artemis GET/POST、科拓门禁保持全绿。
- Credentials: fixture 仍为 `test-ak` / `test-sk`。git 无生产密钥。
