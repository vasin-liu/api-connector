# Legacy Auth Inventory (system-thirdpart audit)

**Deliverable:** D-03 — ROADMAP Phase 1 success criterion #6  
**Source:** Read-only audit of `D:\Work\99_Code\ITS\suntek-system\system-thirdpart` (controllers → clients → auth patterns)  
**Profile IDs:** Cross-reference [profile-registry.md](./profile-registry.md)

## Summary

| Wave | Java built-in (existing) | Java L2 (new) | Groovy (L3) | Deferred |
|------|--------------------------|---------------|-------------|----------|
| **Wave 1** | IDPS, Gaode, Baidu catalogs | — (gate: none required) | Hikvision, 大华, 讯飞 | PF4J (D-15), Transform/SM4 body (D-16) |
| **Wave 2+** | — | oauth2_password, bearer_from_login, sm3_header_sign_v1 | Cookie session, vendor SDK | sm4_body_encrypt_v1 |

**D-14 (ADR-002 SPI admission):** L3 vendors with multi-step login ≥3 round-trips, vendor SDK only, non-templateable crypto, or Cookie/WebSocket use **Groovy** in Phase 1 — no `custom_spi` Java unless criteria force it.

**国密 (D-04):** No Wave 1 vendor requires `sm3_header_sign_v1` or `sm4_body_encrypt_v1`. 数运 (Shuyun) and CETC use 国密 but are **Wave 2+**.

## Inventory

| vendor | code3rd | legacy_client | profile_id | level | wave | path | notes |
|--------|---------|---------------|------------|-------|------|------|-------|
| IDPS 交通大脑 | `IDPS` | `client/idps/IdpsClient`, `IdpsUtils` | `aksk_hmac_sha256_v1` | L1 | 1 | Java ✅ | Canonical HMAC-SHA256 headers; Catalog `IdpsConnectorCatalog` |
| 交通大脑 (user API) | `TRAFFIC_BRAIN` | `controller/TrafficBrainController` → `IdpsInvokeService` | `aksk_hmac_sha256_v1` | L1 | 1 | Java ✅ | Reuses IDPS client auth; separate connector TBD Phase 7 |
| 高德开放平台 | `GAODE_OPEN_PLATFORM` | `client/gaode/GaodeOpenPlatformClient` | `api_key_query` | L1 | 1 | Java ✅ | Query `key` from publicKey; Catalog implemented |
| 高德交通态势 | `GAODE_TRAFFIC` | `client/gaode/traffic/GaodeTrafficClient` | `gaode_traffic_hmac_v1` | L2 | 1 | Java ✅ | HMAC digest on sorted query; Catalog implemented |
| 百度地图交通 | `BAIDU_MAP` | `client/baidu/jiaotong/*` | `api_key_query` | L1 | 1 | Java ✅ | AK query param; Catalog `BaiduMapConnectorCatalog` |
| 百度文心 / ERNIE | `BAIDU_WENXIN` | `client/baidu/gpt/BaiduGptClient` | `oauth2_token_in_query` | L2 | 1 | Java ✅ | CC token → `access_token` query; alias `BaiduGpt` |
| 海康威视 | `HIKVISION` | `client/hikvision/HikvisionClient` | `hikvision_artemis_sdk_v1` | L3 | 1 | **Groovy** | Artemis SDK (`ArtemisHttpUtil`); D-13/D-14 — no Java SPI Phase 1 |
| 大华交警视频云 | `TRAFFIC_DAHUA` | `client/trafficDaHua/TrafficDaHuaClient` | `multi_step_login_dahua_v1` | L3 | 1 | **Groovy** | Two-step login + signature; D-13 — 大华 marked Groovy |
| 讯飞 TTS | `IFLYTEK_TTS` | `client/tts/iflytek/IflytekTtsClient` | `ws_iflytek_hmac_v1` | L3 | 1 | **Groovy** | WebSocket + HMAC-SHA256; D-13 — 讯飞 marked Groovy |
| 科达 | `KEDA` | `client/keda/KedaClient` | `oauth2_password` | L2 | 2 | deferred Java | `/kstp/oauth2/password` → `jwt-token` header; **not Wave 1** (D-01) |
| 广州停车场 | `GZ_PARKING` | `controller/GzParkingLotController` | `bearer_from_login` | L2 | 2 | deferred Java | `loginV2` POST → Bearer; **not Wave 1** |
| 数运 | `SHUYUN` | `client/shuyun/ShuyunClient` | `sm3_header_sign_v1` | L1 | 2 | deferred Java | BouncyCastle SM3 header sign; **not Wave 1 国密** (D-04) |
| 中电科 CETC | `CETC` | `client/cetc/CetcClient` | `sm4_body_encrypt_v1` | L2 | 2 | deferred | SM4 body encrypt → Transform Pipeline Phase 2 (D-16) |
| 公服平台 | `PUBLIC_SERVICE` | `client/publicservice/PublicServiceClient` | `cookie_session_v1` | L3 | 2 | Groovy | Cookie session; ADR-002 SPI admission |
| 美亚大数据 | `MEIYA` | `client/meiya/*` | `oauth2_cc_plus_zero_trust_v1` | L3 | 2 | Groovy / deferred SPI | OAuth + zero-trust combo |
| PF4J hot-deploy | — | — | `custom_spi` | L3 | v2 | **deferred** | D-15: Spring `@Bean` interim only |
| Transform pipeline | — | — | (pipeline steps) | L2 | 2 | **deferred** | D-16: SM4 body / business envelope in mapping engine |

## Wave 1 catalog auth mapping (AUTH-01)

Verified in `ProductionConnectorCatalogsTest` against [profile-registry.md](./profile-registry.md):

| code3rd | profile_id | Status |
|---------|------------|--------|
| `IDPS` | `aksk_hmac_sha256_v1` | Catalog + Java provider |
| `GAODE_OPEN_PLATFORM` | `api_key_query` | Catalog + Java provider |
| `GAODE_TRAFFIC` | `gaode_traffic_hmac_v1` | Catalog + Java provider |
| `BAIDU_MAP` | `api_key_query` | Catalog + Java provider |
| `BAIDU_WENXIN` | `oauth2_token_in_query` | Catalog + Java provider |

## Implementation gate (D-01, D-02) — Plan 01-06 Task 2

After inventory audit, **no Wave 1 vendor requires new Java L2 profiles**:

| profile_id | Wave 1 need | Decision |
|------------|-------------|----------|
| `oauth2_password` | No (Keda = Wave 2) | **Skip** — implement when Keda migrates |
| `bearer_from_login` | No (parking = Wave 2) | **Skip** — implement when GZ parking migrates |
| `sm3_header_sign_v1` | No (Shuyun = Wave 2) | **Skip** — D-04 conditional not met |

AUTH-01 satisfied via five existing Wave 1 catalogs + built-in providers wired in `IntegrationEngineConfiguration`.

## Audit method

1. List `controller/*Controller.java` and `client/*/*Client.java` under system-thirdpart.
2. Classify auth: OAuth, HMAC, API key, Bearer login, Cookie, SDK, 国密, none.
3. Map to `profile-registry.md` profile ID and L1/L2/L3.
4. Assign migration wave per Wave 1 / Wave 2+ columns in this inventory (historical GSD Phase 1 D-01; `.planning/` removed after V2.7 baseline).
5. Assign path: existing Java ✅ / new Java L2 / Groovy / deferred.

*Audit date: 2026-06-17 | Plan: 01-06*
