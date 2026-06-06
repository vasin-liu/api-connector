# Auth Profile 注册表

基于 ITS 现有 `system-thirdpart` 对接协议归纳，供 Connector Spec 与 UI 使用。

## 分级

| 级别 | 说明 |
|------|------|
| L1 | 仅 YAML 配置 |
| L2 | Profile + Transform / Pipeline |
| L3 | Java SPI |

## 注册表

| Profile ID | 级别 | 说明 | 代表场景 |
|------------|------|------|----------|
| `gaode_traffic_hmac_v1` | L2 | 高德交通 digest 签 | GAODE_TRAFFIC（已实现） |
| `none` | L1 | 无认证 | 内网 REST、统一信控 |
| `custom_headers` | L1 | 固定/表达式头 | 长春 X-Ait-* |
| `api_key_query` | L1 | Query key | 高德 |
| `api_key_url_path` | L1 | URL 嵌入 key | GCI |
| `bearer_static` | L1 | 静态 Bearer | TransBridge、ChatGPT |
| `header_token` | L1 | 自定义 token 头 | 九识 |
| `bearer_from_login` | L2 | 登录换 Bearer | 停车场 |
| `oauth2_client_credentials` | L1 | 标准 CC | 新石器、美亚 token |
| `oauth2_password` | L2 | Password | 科达 jwt-token |
| `oauth2_custom_token` | L2 | 私有 token URL | 九识 |
| `oauth2_token_in_query` | L2 | Token 拼 URL | 百度文心（已实现） |
| `basic_then_bearer` | L2 | Basic → Bearer | GZ 统一用户 |
| `aksk_hmac_sha256_v1` | L1 | IDPS 规范串 | IDPS、Traffic |
| `hmac_headers_sha256_v1` | L1 | Header+Body HMAC | 新粤智联 |
| `md5_body_sign_sorted_v1` | L1 | Body MD5 | 科拓 |
| `top_md5_sign_v1` | L1 | 淘宝 Top 签 | 途强 |
| `md5_params_sign_v1` | L1 | 参数 MD5 | Pony |
| `sm3_header_sign_v1` | L1 | SM3 头签名 | 数运 |
| `sm4_body_encrypt_v1` | L2 | SM4 加密 body | CETC |
| `bearer_plus_request_sign_v1` | L2 | Bearer+请求签 | 禹好办 |
| `token_plus_multi_header_sign_v1` | L2 | Token+多头签 | 全运会 |
| `oauth2_cc_plus_zero_trust_v1` | L3 | OAuth+零信任 | 美亚 |
| `multi_step_login_dahua_v1` | L3 | 大华两步登录 | 大华 |
| `hikvision_artemis_sdk_v1` | L3 | 海康 SDK | 海康 |
| `cookie_session_v1` | L3 | Cookie 会话 | 公服 |
| `ws_iflytek_hmac_v1` | L3 | 讯飞 WS | TTS |

完整配置示例见前期设计评审稿；实现优先级见 [DEVELOPER.md](./DEVELOPER.md) P0–P3。

**UI 元数据（P1）：** [schemas/profiles-meta/](./schemas/profiles-meta/) 下 JSON 与 [UI-CONFIG-SPEC.md](./UI-CONFIG-SPEC.md) §4 对齐；持久化见 [CONNECTOR-PERSISTENCE.md](./CONNECTOR-PERSISTENCE.md)。

## Pipeline 示例

```yaml
auth:
  pipeline:
    - type: oauth2_client_credentials
      tokenUrl: /xsp-auth/oauth2/token
      scope: email
    - type: custom_spi
      plugin: meiya_zero_trust_v1
```

## SPI 准入（摘要）

仅当：多步登录 ≥3 往返、仅 SDK、算法不可模板化、Cookie/WebSocket 专用时允许 L3。
